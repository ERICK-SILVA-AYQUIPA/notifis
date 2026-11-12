/*
 * PAGOBOX Device — firmware ESP32-S3 (ESP-IDF + FreeRTOS).
 *
 * Estado actual: base del proyecto con protocolo de eventos, estados de pantalla,
 * NVS y watchdog de tareas. Pendiente (fase 4/5, ver firmware/README.md):
 * servicio BLE GATT, transporte de audio por Wi-Fi, driver de pantalla TFT,
 * salida I2S al amplificador, gestión de batería y OTA.
 */
#include <inttypes.h>
#include <string.h>

#include "display_state.h"
#include "esp_log.h"
#include "esp_task_wdt.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "nvs.h"
#include "nvs_flash.h"
#include "protocol.h"

static const char *TAG = "pagobox";

#define PB_MSG_MAX 512

typedef struct {
    char json[PB_MSG_MAX];
} pb_message_t;

/* Los transportes (BLE/Wi-Fi) depositan aquí los mensajes recibidos. */
static QueueHandle_t s_inbox;
static pb_display_state_t s_display;
static uint32_t s_last_sequence;

static void load_last_sequence(void)
{
    nvs_handle_t h;
    if (nvs_open("pagobox", NVS_READONLY, &h) == ESP_OK) {
        nvs_get_u32(h, "last_seq", &s_last_sequence);
        nvs_close(h);
    }
}

static void store_last_sequence(uint32_t seq)
{
    nvs_handle_t h;
    if (nvs_open("pagobox", NVS_READWRITE, &h) == ESP_OK) {
        nvs_set_u32(h, "last_seq", seq);
        nvs_commit(h);
        nvs_close(h);
    }
}

static void handle_message(const char *json)
{
    pb_payment_event_t evt;
    pb_parse_result_t r = pb_parse_payment_event(json, &evt);
    if (r != PB_PARSE_OK) {
        ESP_LOGW(TAG, "Mensaje descartado (error %d)", r);
        return;
    }
    char ack[160];
    pb_build_ack(ack, sizeof(ack), evt.event_id, evt.sequence, "RECEIVED");
    /* El ACK se envía siempre, también para repetidos, para que Android deje de reintentar. */
    ESP_LOGI(TAG, "ACK: %s", ack);

    if (!pb_is_new_sequence(s_last_sequence, evt.sequence)) {
        ESP_LOGI(TAG, "Evento %s repetido (seq %" PRIu32 "), no se anuncia", evt.event_id, evt.sequence);
        return;
    }
    s_last_sequence = evt.sequence;
    store_last_sequence(evt.sequence);
    pb_display_on_payment(&s_display, esp_timer_get_time());
    ESP_LOGI(TAG, "Pago %s: %s %" PRId64 " centimos (%s)", evt.event_id, evt.source, evt.amount_cents,
             evt.verification == PB_VERIFICATION_VERIFIED ? "verificado" : "detectado");
}

static void event_task(void *arg)
{
    ESP_ERROR_CHECK(esp_task_wdt_add(NULL));
    pb_message_t msg;
    for (;;) {
        if (xQueueReceive(s_inbox, &msg, pdMS_TO_TICKS(500)) == pdTRUE) {
            handle_message(msg.json);
        }
        pb_display_tick(&s_display, esp_timer_get_time());
        esp_task_wdt_reset();
    }
}

void app_main(void)
{
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);

    load_last_sequence();
    pb_display_init(&s_display);
    s_inbox = xQueueCreate(8, sizeof(pb_message_t));
    configASSERT(s_inbox);

    ESP_LOGI(TAG, "PAGOBOX Device iniciado. Última secuencia: %" PRIu32, s_last_sequence);
    xTaskCreate(event_task, "pb_events", 6144, NULL, 5, NULL);
}
