/*
 * PAGOBOX Device — firmware ESP32-S3 (ESP-IDF + FreeRTOS).
 *
 * Tareas:
 *  - pb_events: procesa mensajes del celular (BLE), confirma con ACK, muestra y anuncia pagos.
 *  - pb_ui:     botones, batería, LED y refresco de pantalla.
 * Un ACK confirma la recepción técnica del mensaje, no el ingreso del dinero.
 */
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "audio/audio_out.h"
#include "audio/wav.h"
#include "bluetooth/ble_service.h"
#include "diagnostics/diag.h"
#include "display/display.h"
#include "display/display_state.h"
#include "esp_app_desc.h"
#include "esp_log.h"
#include "esp_mac.h"
#include "esp_ota_ops.h"
#include "esp_task_wdt.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "net/wifi_audio.h"
#include "power/power.h"
#include "protocol.h"
#include "storage/settings_store.h"

static const char *TAG = "pagobox";

#define PB_MSG_MAX 1024
#define AUDIO_WAIT_MS 6000

typedef struct {
    char json[PB_MSG_MAX];
} pb_inbox_item_t;

static QueueHandle_t s_inbox;
static SemaphoreHandle_t s_state_lock;
static pb_display_state_t s_display;
static pb_settings_t s_settings;
static char s_device_id[16];
static bool s_muted;
static bool s_has_last_payment;
static pb_payment_event_t s_last_payment;

/* ---------- Mensajes salientes ---------- */

static void send_status(void)
{
    char buf[384];
    pb_status_t st = {
        .device_id = s_device_id,
        .firmware = esp_app_get_description()->version,
        .battery = pb_power_battery_percent(),
        .charging = pb_power_is_charging(),
        .volume = pb_audio_get_volume(),
        .wifi_ip = pb_wifi_ip(),
        .audio_token = pb_wifi_audio_token(),
        .last_sequence = s_settings.last_sequence,
        .error = pb_diag_error(),
    };
    if (pb_build_status(buf, sizeof(buf), &st) > 0) {
        pb_ble_send(buf);
    }
}

static void send_ack(const pb_payment_event_t *e, const char *stage)
{
    char ack[160];
    pb_build_ack(ack, sizeof(ack), e->event_id, e->sequence, stage);
    pb_ble_send(ack);
}

/* ---------- Callbacks BLE (contexto del host NimBLE: solo encolar) ---------- */

static void on_ble_rx(const char *line, size_t len)
{
    pb_inbox_item_t item;
    if (len >= sizeof(item.json)) return;
    memcpy(item.json, line, len + 1);
    if (xQueueSend(s_inbox, &item, 0) != pdTRUE) {
        ESP_LOGW(TAG, "Bandeja llena; mensaje descartado (el celular reintentará)");
    }
}

static void on_ble_conn(bool connected)
{
    xSemaphoreTake(s_state_lock, portMAX_DELAY);
    pb_display_on_connection(&s_display, connected);
    xSemaphoreGive(s_state_lock);
    pb_led_set(connected ? PB_LED_OK : PB_LED_WARNING);
}

static void on_passkey(unsigned long passkey)
{
    pb_display_show_passkey((uint32_t)passkey);
    if (passkey != 0) pb_led_set(PB_LED_PAIRING);
}

/* ---------- Anuncios ---------- */

static void play_announcement(const pb_payment_event_t *e)
{
    if (s_muted) return;
    pb_audio_clip_t clip;
    bool played = false;
    if (pb_wifi_is_connected() && pb_wifi_wait_audio(e->event_id, AUDIO_WAIT_MS, &clip)) {
        pb_wav_t wav;
        if (pb_wav_parse(clip.data, clip.len, &wav) == 0) {
            pb_audio_play_chime();
            played = pb_audio_play_pcm(wav.samples, wav.sample_count, wav.sample_rate) == ESP_OK;
        } else {
            pb_diag_set_error("Audio recibido con formato no compatible");
        }
        free(clip.data);
    }
    if (!played) {
        /* Sin voz por Wi-Fi: tono de aviso; el importe queda visible en pantalla. */
        if (pb_audio_play_chime() != ESP_OK) pb_diag_set_error("Error de audio");
    }
}

static void handle_payment(const pb_payment_event_t *e)
{
    /* ACK de recepción siempre, también para repetidos, para que el celular deje de reintentar. */
    send_ack(e, "RECEIVED");
    if (!pb_is_new_sequence(s_settings.last_sequence, e->sequence)) {
        ESP_LOGI(TAG, "Evento %s repetido (seq %" PRIu32 "); no se vuelve a anunciar", e->event_id, e->sequence);
        send_ack(e, "PLAYED");
        return;
    }
    s_settings.last_sequence = e->sequence;
    pb_settings_set_last_sequence(e->sequence);

    s_last_payment = *e;
    s_has_last_payment = true;
    pb_display_set_payment(e);
    xSemaphoreTake(s_state_lock, portMAX_DELAY);
    pb_display_on_payment(&s_display, esp_timer_get_time());
    xSemaphoreGive(s_state_lock);
    pb_led_set(PB_LED_PAYMENT);

    play_announcement(e);
    send_ack(e, "PLAYED");
    pb_led_set(PB_LED_OK);
}

static void handle_message(const char *json)
{
    pb_message_in_t msg;
    pb_parse_result_t r = pb_parse_message(json, &msg);
    if (r != PB_PARSE_OK) {
        ESP_LOGW(TAG, "Mensaje descartado (error %d)", r);
        return;
    }
    switch (msg.type) {
    case PB_MSG_PAYMENT_EVENT:
        handle_payment(&msg.payment);
        break;
    case PB_MSG_GET_STATUS:
        send_status();
        break;
    case PB_MSG_SET_VOLUME:
        pb_audio_set_volume(msg.volume);
        pb_settings_set_volume(msg.volume);
        send_status();
        break;
    case PB_MSG_WIFI_CONFIG:
        pb_settings_set_wifi(msg.wifi.ssid, msg.wifi.password);
        pb_wifi_connect(msg.wifi.ssid, msg.wifi.password);
        memset(&msg.wifi, 0, sizeof(msg.wifi));
        vTaskDelay(pdMS_TO_TICKS(4000)); /* Dar tiempo a obtener IP antes de informar */
        send_status();
        break;
    case PB_MSG_PLAY_TEST:
        pb_audio_play_chime();
        break;
    default:
        break;
    }
}

static void event_task(void *arg)
{
    ESP_ERROR_CHECK(esp_task_wdt_add(NULL));
    pb_inbox_item_t item;
    for (;;) {
        if (xQueueReceive(s_inbox, &item, pdMS_TO_TICKS(1000)) == pdTRUE) {
            esp_task_wdt_reset();
            handle_message(item.json);
        }
        esp_task_wdt_reset();
    }
}

/* ---------- Interfaz física ---------- */

static void ui_task(void *arg)
{
    ESP_ERROR_CHECK(esp_task_wdt_add(NULL));
    int64_t last_status = 0, last_render = 0;
    int battery = pb_power_battery_percent();
    bool low_battery_warned = false;
    for (;;) {
        int64_t now = esp_timer_get_time();
        switch (pb_power_poll_button()) {
        case PB_BTN_VOL_UP:
            pb_audio_set_volume(pb_audio_get_volume() + 10);
            pb_settings_set_volume(pb_audio_get_volume());
            break;
        case PB_BTN_VOL_DOWN:
            pb_audio_set_volume(pb_audio_get_volume() - 10);
            pb_settings_set_volume(pb_audio_get_volume());
            break;
        case PB_BTN_MUTE:
            s_muted = !s_muted;
            break;
        case PB_BTN_MUTE_LONG:
            /* Mantener pulsado: volver a mostrar el último pago (sin volver a anunciarlo). */
            if (s_has_last_payment) {
                pb_display_set_payment(&s_last_payment);
                xSemaphoreTake(s_state_lock, portMAX_DELAY);
                pb_display_on_payment(&s_display, now);
                xSemaphoreGive(s_state_lock);
            }
            break;
        default:
            break;
        }

        if (now - last_status > 30LL * 1000 * 1000) {
            last_status = now;
            battery = pb_power_battery_percent();
            if (battery >= 0 && battery <= 15 && !pb_power_is_charging()) {
                if (!low_battery_warned) {
                    pb_diag_set_error("Bateria baja");
                    low_battery_warned = true;
                }
            } else if (low_battery_warned) {
                low_battery_warned = false;
                pb_diag_clear_error();
            }
            if (pb_ble_is_connected()) send_status();
        }

        if (now - last_render > 250000) {
            last_render = now;
            xSemaphoreTake(s_state_lock, portMAX_DELAY);
            pb_display_tick(&s_display, now);
            pb_display_view_t view = {
                .screen = s_display.screen,
                .android_connected = s_display.android_connected,
                .wifi_connected = pb_wifi_is_connected(),
                .battery = battery,
                .charging = pb_power_is_charging(),
                .volume = pb_audio_get_volume(),
                .muted = s_muted,
                .error = pb_diag_error(),
            };
            xSemaphoreGive(s_state_lock);
            pb_display_render(&view);
        }
        esp_task_wdt_reset();
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}

void app_main(void)
{
    ESP_ERROR_CHECK(pb_settings_init());
    pb_settings_load(&s_settings);

    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_BT);
    snprintf(s_device_id, sizeof(s_device_id), "%02X%02X%02X%02X%02X%02X", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    char name[24];
    snprintf(name, sizeof(name), "PAGOBOX-%02X%02X", mac[4], mac[5]);

    s_state_lock = xSemaphoreCreateMutex();
    s_inbox = xQueueCreate(8, sizeof(pb_inbox_item_t));
    configASSERT(s_state_lock && s_inbox);
    pb_display_init(&s_display);

    ESP_ERROR_CHECK(pb_power_init());
    pb_led_set(PB_LED_WARNING);
    if (pb_display_hw_init() != ESP_OK) pb_diag_set_error("Error de pantalla");
    if (pb_audio_init() != ESP_OK) pb_diag_set_error("Error de audio");
    pb_audio_set_volume(s_settings.volume);

    ESP_ERROR_CHECK(pb_wifi_init());
    if (s_settings.wifi_ssid[0] != '\0') {
        pb_wifi_connect(s_settings.wifi_ssid, s_settings.wifi_password);
    }
    ESP_ERROR_CHECK(pb_ble_init(name, on_ble_rx, on_ble_conn, on_passkey));

    xTaskCreate(event_task, "pb_events", 8192, NULL, 6, NULL);
    xTaskCreate(ui_task, "pb_ui", 6144, NULL, 4, NULL);

    /* Si esta versión llegó por OTA y arrancó bien, se confirma (si no, el bootloader revierte). */
    esp_ota_mark_app_valid_cancel_rollback();
    ESP_LOGI(TAG, "%s iniciado. Firmware %s. Última secuencia %" PRIu32, name,
             esp_app_get_description()->version, s_settings.last_sequence);
}
