#include "settings_store.h"

#include <string.h>

#include "nvs.h"
#include "nvs_flash.h"

#define NS "pagobox"

esp_err_t pb_settings_init(void)
{
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    return err;
}

void pb_settings_load(pb_settings_t *out)
{
    memset(out, 0, sizeof(*out));
    out->volume = 70;
    nvs_handle_t h;
    if (nvs_open(NS, NVS_READONLY, &h) != ESP_OK) {
        return;
    }
    nvs_get_u32(h, "last_seq", &out->last_sequence);
    int32_t vol;
    if (nvs_get_i32(h, "volume", &vol) == ESP_OK) {
        out->volume = vol;
    }
    size_t len = sizeof(out->wifi_ssid);
    if (nvs_get_str(h, "wifi_ssid", out->wifi_ssid, &len) != ESP_OK) {
        out->wifi_ssid[0] = '\0';
    }
    len = sizeof(out->wifi_password);
    if (nvs_get_str(h, "wifi_pass", out->wifi_password, &len) != ESP_OK) {
        out->wifi_password[0] = '\0';
    }
    nvs_close(h);
}

static esp_err_t with_handle(esp_err_t (*fn)(nvs_handle_t, const void *), const void *arg)
{
    nvs_handle_t h;
    esp_err_t err = nvs_open(NS, NVS_READWRITE, &h);
    if (err != ESP_OK) {
        return err;
    }
    err = fn(h, arg);
    if (err == ESP_OK) {
        err = nvs_commit(h);
    }
    nvs_close(h);
    return err;
}

static esp_err_t put_seq(nvs_handle_t h, const void *arg) { return nvs_set_u32(h, "last_seq", *(const uint32_t *)arg); }
static esp_err_t put_vol(nvs_handle_t h, const void *arg) { return nvs_set_i32(h, "volume", *(const int *)arg); }

typedef struct {
    const char *ssid;
    const char *password;
} wifi_arg_t;

static esp_err_t put_wifi(nvs_handle_t h, const void *arg)
{
    const wifi_arg_t *w = arg;
    esp_err_t err = nvs_set_str(h, "wifi_ssid", w->ssid);
    return err == ESP_OK ? nvs_set_str(h, "wifi_pass", w->password) : err;
}

esp_err_t pb_settings_set_last_sequence(uint32_t seq) { return with_handle(put_seq, &seq); }
esp_err_t pb_settings_set_volume(int volume) { return with_handle(put_vol, &volume); }
esp_err_t pb_settings_set_wifi(const char *ssid, const char *password)
{
    wifi_arg_t w = {ssid, password};
    return with_handle(put_wifi, &w);
}
