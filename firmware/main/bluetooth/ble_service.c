/*
 * Servicio BLE GATT de PAGOBOX (NimBLE).
 * - RX (escritura) exige enlace cifrado y autenticado: LE Secure Connections con
 *   código de 6 dígitos mostrado en la pantalla (MITM). Los vínculos se guardan en NVS.
 * - TX (notificación) envía ACK y estado.
 */
#include "ble_service.h"

#include <string.h>

#include "esp_log.h"
#include "esp_random.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "host/ble_hs.h"
#include "host/util/util.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

void ble_store_config_init(void);

static const char *TAG = "ble";

/* 6a4e0001-7a1b-4c2e-9b0a-50a90b0c0001 (bytes en orden little-endian) */
static const ble_uuid128_t SVC_UUID =
    BLE_UUID128_INIT(0x01, 0x00, 0x0c, 0x0b, 0xa9, 0x50, 0x0a, 0x9b, 0x2e, 0x4c, 0x1b, 0x7a, 0x01, 0x00, 0x4e, 0x6a);
static const ble_uuid128_t RX_UUID =
    BLE_UUID128_INIT(0x01, 0x00, 0x0c, 0x0b, 0xa9, 0x50, 0x0a, 0x9b, 0x2e, 0x4c, 0x1b, 0x7a, 0x02, 0x00, 0x4e, 0x6a);
static const ble_uuid128_t TX_UUID =
    BLE_UUID128_INIT(0x01, 0x00, 0x0c, 0x0b, 0xa9, 0x50, 0x0a, 0x9b, 0x2e, 0x4c, 0x1b, 0x7a, 0x03, 0x00, 0x4e, 0x6a);

#define RX_BUF_MAX 1024

static uint16_t s_tx_handle;
static uint16_t s_conn = BLE_HS_CONN_HANDLE_NONE;
static uint8_t s_own_addr_type;
static char s_rx_buf[RX_BUF_MAX];
static size_t s_rx_len;
static SemaphoreHandle_t s_tx_lock;
static pb_ble_rx_cb_t s_rx_cb;
static pb_ble_conn_cb_t s_conn_cb;
static pb_ble_passkey_cb_t s_passkey_cb;
static char s_name[32];

static int gap_event(struct ble_gap_event *event, void *arg);

static void on_rx_bytes(const uint8_t *data, size_t len)
{
    for (size_t i = 0; i < len; i++) {
        char c = (char)data[i];
        if (c == '\n') {
            s_rx_buf[s_rx_len] = '\0';
            if (s_rx_len > 0 && s_rx_cb != NULL) {
                s_rx_cb(s_rx_buf, s_rx_len);
            }
            s_rx_len = 0;
        } else if (s_rx_len + 1 < RX_BUF_MAX) {
            s_rx_buf[s_rx_len++] = c;
        } else {
            ESP_LOGW(TAG, "Mensaje demasiado largo; descartado");
            s_rx_len = 0;
        }
    }
}

static int chr_access(uint16_t conn_handle, uint16_t attr_handle, struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    if (ctxt->op == BLE_GATT_ACCESS_OP_WRITE_CHR) {
        uint8_t buf[256];
        uint16_t len = 0;
        int rc = ble_hs_mbuf_to_flat(ctxt->om, buf, sizeof(buf), &len);
        if (rc != 0) {
            return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
        }
        on_rx_bytes(buf, len);
        return 0;
    }
    if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR) {
        return 0; /* TX se usa por notificaciones; la lectura devuelve vacío. */
    }
    return BLE_ATT_ERR_UNLIKELY;
}

static const struct ble_gatt_svc_def s_services[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = &SVC_UUID.u,
        .characteristics = (struct ble_gatt_chr_def[]){
            {
                .uuid = &RX_UUID.u,
                .access_cb = chr_access,
                .flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_ENC | BLE_GATT_CHR_F_WRITE_AUTHEN,
            },
            {
                .uuid = &TX_UUID.u,
                .access_cb = chr_access,
                .val_handle = &s_tx_handle,
                .flags = BLE_GATT_CHR_F_NOTIFY | BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_READ_ENC | BLE_GATT_CHR_F_READ_AUTHEN,
            },
            {0},
        },
    },
    {0},
};

static void advertise(void)
{
    struct ble_hs_adv_fields fields = {0};
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.uuids128 = (ble_uuid128_t *)&SVC_UUID;
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;
    int rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) {
        ESP_LOGE(TAG, "adv_set_fields: %d", rc);
        return;
    }
    struct ble_hs_adv_fields rsp = {0};
    rsp.name = (uint8_t *)s_name;
    rsp.name_len = strlen(s_name);
    rsp.name_is_complete = 1;
    ble_gap_adv_rsp_set_fields(&rsp);

    struct ble_gap_adv_params params = {0};
    params.conn_mode = BLE_GAP_CONN_MODE_UND;
    params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    rc = ble_gap_adv_start(s_own_addr_type, NULL, BLE_HS_FOREVER, &params, gap_event, NULL);
    if (rc != 0 && rc != BLE_HS_EALREADY) {
        ESP_LOGE(TAG, "adv_start: %d", rc);
    }
}

static int gap_event(struct ble_gap_event *event, void *arg)
{
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status == 0) {
            s_conn = event->connect.conn_handle;
            s_rx_len = 0;
            ESP_LOGI(TAG, "Conectado");
            /* Exigir cifrado desde el inicio: dispara la vinculación si no existe. */
            ble_gap_security_initiate(s_conn);
        } else {
            advertise();
        }
        return 0;
    case BLE_GAP_EVENT_DISCONNECT:
        ESP_LOGI(TAG, "Desconectado (motivo %d)", event->disconnect.reason);
        s_conn = BLE_HS_CONN_HANDLE_NONE;
        if (s_passkey_cb) s_passkey_cb(0);
        if (s_conn_cb) s_conn_cb(false);
        advertise();
        return 0;
    case BLE_GAP_EVENT_ENC_CHANGE:
        if (s_passkey_cb) s_passkey_cb(0);
        if (event->enc_change.status == 0) {
            ESP_LOGI(TAG, "Enlace cifrado");
            if (s_conn_cb) s_conn_cb(true);
        } else {
            ESP_LOGW(TAG, "Fallo de cifrado: %d", event->enc_change.status);
        }
        return 0;
    case BLE_GAP_EVENT_PASSKEY_ACTION:
        if (event->passkey.params.action == BLE_SM_IOACT_DISP) {
            struct ble_sm_io io = {0};
            io.action = BLE_SM_IOACT_DISP;
            io.passkey = 100000 + esp_random() % 900000; /* siempre 6 dígitos */
            if (s_passkey_cb) s_passkey_cb(io.passkey);
            ble_sm_inject_io(event->passkey.conn_handle, &io);
        }
        return 0;
    case BLE_GAP_EVENT_REPEAT_PAIRING: {
        /* El celular olvidó el vínculo: se borra el anterior y se vuelve a vincular. */
        struct ble_gap_conn_desc desc;
        if (ble_gap_conn_find(event->repeat_pairing.conn_handle, &desc) == 0) {
            ble_store_util_delete_peer(&desc.peer_id_addr);
        }
        return BLE_GAP_REPEAT_PAIRING_RETRY;
    }
    case BLE_GAP_EVENT_ADV_COMPLETE:
        advertise();
        return 0;
    default:
        return 0;
    }
}

static void on_sync(void)
{
    ble_hs_util_ensure_addr(0);
    ble_hs_id_infer_auto(0, &s_own_addr_type);
    advertise();
}

static void on_reset(int reason) { ESP_LOGW(TAG, "Reinicio del host BLE: %d", reason); }

static void host_task(void *param)
{
    nimble_port_run();
    nimble_port_freertos_deinit();
}

esp_err_t pb_ble_init(const char *device_name, pb_ble_rx_cb_t rx, pb_ble_conn_cb_t conn, pb_ble_passkey_cb_t passkey)
{
    s_rx_cb = rx;
    s_conn_cb = conn;
    s_passkey_cb = passkey;
    s_tx_lock = xSemaphoreCreateMutex();
    strncpy(s_name, device_name, sizeof(s_name) - 1);

    esp_err_t err = nimble_port_init();
    if (err != ESP_OK) {
        return err;
    }
    ble_hs_cfg.reset_cb = on_reset;
    ble_hs_cfg.sync_cb = on_sync;
    ble_hs_cfg.store_status_cb = ble_store_util_status_rr;
    ble_hs_cfg.sm_io_cap = BLE_SM_IO_CAP_DISP_ONLY;
    ble_hs_cfg.sm_bonding = 1;
    ble_hs_cfg.sm_mitm = 1;
    ble_hs_cfg.sm_sc = 1;
    ble_hs_cfg.sm_our_key_dist = BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;
    ble_hs_cfg.sm_their_key_dist = BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;

    ble_svc_gap_init();
    ble_svc_gatt_init();
    int rc = ble_gatts_count_cfg(s_services);
    if (rc == 0) rc = ble_gatts_add_svcs(s_services);
    if (rc != 0) {
        ESP_LOGE(TAG, "No se pudo registrar el servicio GATT: %d", rc);
        return ESP_FAIL;
    }
    ble_svc_gap_device_name_set(s_name);
    ble_store_config_init();
    nimble_port_freertos_init(host_task);
    return ESP_OK;
}

bool pb_ble_is_connected(void) { return s_conn != BLE_HS_CONN_HANDLE_NONE; }

esp_err_t pb_ble_send(const char *msg)
{
    if (s_conn == BLE_HS_CONN_HANDLE_NONE) {
        return ESP_ERR_INVALID_STATE;
    }
    xSemaphoreTake(s_tx_lock, portMAX_DELAY);
    size_t len = strlen(msg);
    uint16_t mtu = ble_att_mtu(s_conn);
    size_t chunk = mtu > 3 ? mtu - 3 : 20;
    esp_err_t result = ESP_OK;
    /* Mensaje + '\n' como terminador. */
    for (size_t off = 0; off <= len && result == ESP_OK;) {
        size_t n = len + 1 - off < chunk ? len + 1 - off : chunk;
        uint8_t part[520];
        for (size_t i = 0; i < n; i++) {
            part[i] = off + i < len ? (uint8_t)msg[off + i] : '\n';
        }
        struct os_mbuf *om = ble_hs_mbuf_from_flat(part, n);
        if (om == NULL || ble_gatts_notify_custom(s_conn, s_tx_handle, om) != 0) {
            result = ESP_FAIL;
        }
        off += n;
        if (off > len) break;
    }
    xSemaphoreGive(s_tx_lock);
    return result;
}
