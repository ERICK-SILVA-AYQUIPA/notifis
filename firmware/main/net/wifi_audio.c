/*
 * Wi-Fi local y servidor HTTP del equipo:
 *   POST /audio?eventId=...  (audio/wav)  -> voz del anuncio generada en el celular
 *   POST /ota                (binario)    -> actualización de firmware
 * Ambos exigen la cabecera X-Pagobox-Token, que solo se entrega por el enlace BLE cifrado.
 */
#include "wifi_audio.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_event.h"
#include "esp_heap_caps.h"
#include "esp_http_server.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_ota_ops.h"
#include "esp_random.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"

static const char *TAG = "wifi";

#define AUDIO_PORT 8080
#define MAX_AUDIO_BYTES (768 * 1024)

static esp_netif_t *s_netif;
static httpd_handle_t s_http;
static QueueHandle_t s_audio_q;
static volatile bool s_connected;
static volatile bool s_enabled;
static char s_ip[16];
static char s_token[33];
static int s_retry;

static void on_wifi_event(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_START) {
        if (s_enabled) esp_wifi_connect();
    } else if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        s_connected = false;
        s_ip[0] = '\0';
        if (s_enabled) {
            /* Reintento con espera creciente (máximo ~30 s) sin bloquear el bucle de eventos. */
            int delay_ms = s_retry < 6 ? (1000 << s_retry) : 30000;
            if (delay_ms > 30000) delay_ms = 30000;
            s_retry++;
            ESP_LOGW(TAG, "Wi-Fi desconectado; reintento en %d ms", delay_ms);
            vTaskDelay(pdMS_TO_TICKS(delay_ms > 2000 ? 2000 : delay_ms));
            esp_wifi_connect();
        }
    } else if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        ip_event_got_ip_t *e = data;
        snprintf(s_ip, sizeof(s_ip), IPSTR, IP2STR(&e->ip_info.ip));
        s_connected = true;
        s_retry = 0;
        ESP_LOGI(TAG, "IP %s", s_ip);
    }
}

static bool token_ok(httpd_req_t *req)
{
    char tok[40] = {0};
    if (httpd_req_get_hdr_value_str(req, "X-Pagobox-Token", tok, sizeof(tok)) != ESP_OK) {
        return false;
    }
    /* Comparación en tiempo constante. */
    size_t n = strlen(s_token);
    if (strlen(tok) != n) return false;
    unsigned diff = 0;
    for (size_t i = 0; i < n; i++) diff |= (unsigned)(tok[i] ^ s_token[i]);
    return diff == 0;
}

static esp_err_t audio_post(httpd_req_t *req)
{
    if (!token_ok(req)) {
        return httpd_resp_send_err(req, HTTPD_403_FORBIDDEN, "token");
    }
    if (req->content_len == 0 || req->content_len > MAX_AUDIO_BYTES) {
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "size");
    }
    pb_audio_clip_t clip = {0};
    char query[96];
    if (httpd_req_get_url_query_str(req, query, sizeof(query)) != ESP_OK ||
        httpd_query_key_value(query, "eventId", clip.event_id, sizeof(clip.event_id)) != ESP_OK) {
        return httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "eventId");
    }
    clip.data = heap_caps_malloc(req->content_len, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (clip.data == NULL) clip.data = malloc(req->content_len);
    if (clip.data == NULL) {
        return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "memoria");
    }
    size_t got = 0;
    while (got < req->content_len) {
        int r = httpd_req_recv(req, (char *)clip.data + got, req->content_len - got);
        if (r == HTTPD_SOCK_ERR_TIMEOUT) continue;
        if (r <= 0) {
            free(clip.data);
            return ESP_FAIL;
        }
        got += r;
    }
    clip.len = got;
    if (xQueueSend(s_audio_q, &clip, 0) != pdTRUE) {
        free(clip.data);
        return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "ocupado");
    }
    return httpd_resp_sendstr(req, "OK");
}

static esp_err_t ota_post(httpd_req_t *req)
{
    if (!token_ok(req)) {
        return httpd_resp_send_err(req, HTTPD_403_FORBIDDEN, "token");
    }
    const esp_partition_t *part = esp_ota_get_next_update_partition(NULL);
    esp_ota_handle_t h;
    if (part == NULL || esp_ota_begin(part, OTA_WITH_SEQUENTIAL_WRITES, &h) != ESP_OK) {
        return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "ota_begin");
    }
    char buf[2048];
    size_t remaining = req->content_len;
    while (remaining > 0) {
        int r = httpd_req_recv(req, buf, remaining < sizeof(buf) ? remaining : sizeof(buf));
        if (r == HTTPD_SOCK_ERR_TIMEOUT) continue;
        if (r <= 0 || esp_ota_write(h, buf, r) != ESP_OK) {
            esp_ota_abort(h);
            return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "ota_write");
        }
        remaining -= r;
    }
    if (esp_ota_end(h) != ESP_OK || esp_ota_set_boot_partition(part) != ESP_OK) {
        return httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "imagen invalida");
    }
    httpd_resp_sendstr(req, "OK; reiniciando");
    ESP_LOGI(TAG, "OTA completada; reiniciando");
    vTaskDelay(pdMS_TO_TICKS(500));
    esp_restart();
    return ESP_OK;
}

esp_err_t pb_wifi_init(void)
{
    s_audio_q = xQueueCreate(2, sizeof(pb_audio_clip_t));
    for (int i = 0; i < 16; i++) {
        snprintf(s_token + i * 2, 3, "%02x", (unsigned)(esp_random() & 0xFF));
    }
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    s_netif = esp_netif_create_default_wifi_sta();
    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));
    ESP_ERROR_CHECK(esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, on_wifi_event, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, on_wifi_event, NULL));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_ps(WIFI_PS_MIN_MODEM)); /* necesario para coexistir con BLE */

    httpd_config_t hc = HTTPD_DEFAULT_CONFIG();
    hc.server_port = AUDIO_PORT;
    hc.recv_wait_timeout = 10;
    hc.stack_size = 8192;
    if (httpd_start(&s_http, &hc) == ESP_OK) {
        httpd_uri_t audio = {.uri = "/audio", .method = HTTP_POST, .handler = audio_post};
        httpd_uri_t ota = {.uri = "/ota", .method = HTTP_POST, .handler = ota_post};
        httpd_register_uri_handler(s_http, &audio);
        httpd_register_uri_handler(s_http, &ota);
    }
    return ESP_OK;
}

esp_err_t pb_wifi_connect(const char *ssid, const char *password)
{
    esp_wifi_stop();
    s_connected = false;
    s_ip[0] = '\0';
    s_enabled = ssid != NULL && ssid[0] != '\0';
    if (!s_enabled) {
        return ESP_OK;
    }
    wifi_config_t wc = {0};
    strncpy((char *)wc.sta.ssid, ssid, sizeof(wc.sta.ssid) - 1);
    strncpy((char *)wc.sta.password, password, sizeof(wc.sta.password) - 1);
    wc.sta.threshold.authmode = password[0] != '\0' ? WIFI_AUTH_WPA2_PSK : WIFI_AUTH_OPEN;
    wc.sta.pmf_cfg.capable = true;
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wc));
    s_retry = 0;
    return esp_wifi_start();
}

bool pb_wifi_is_connected(void) { return s_connected; }
const char *pb_wifi_ip(void) { return s_ip; }
const char *pb_wifi_audio_token(void) { return s_token; }

bool pb_wifi_wait_audio(const char *event_id, uint32_t timeout_ms, pb_audio_clip_t *out)
{
    TickType_t deadline = xTaskGetTickCount() + pdMS_TO_TICKS(timeout_ms);
    pb_audio_clip_t clip;
    for (;;) {
        TickType_t now = xTaskGetTickCount();
        if (now >= deadline) return false;
        if (xQueueReceive(s_audio_q, &clip, deadline - now) != pdTRUE) return false;
        if (strcmp(clip.event_id, event_id) == 0) {
            *out = clip;
            return true;
        }
        /* Audio de otro evento (atrasado): se descarta. */
        free(clip.data);
    }
}
