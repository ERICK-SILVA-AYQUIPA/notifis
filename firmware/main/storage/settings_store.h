#pragma once
#include <stdbool.h>
#include <stdint.h>

#include "esp_err.h"
#include "protocol.h"

typedef struct {
    uint32_t last_sequence;
    int volume;
    char wifi_ssid[PB_SSID_MAX];
    char wifi_password[PB_PASS_MAX];
} pb_settings_t;

esp_err_t pb_settings_init(void);
void pb_settings_load(pb_settings_t *out);
esp_err_t pb_settings_set_last_sequence(uint32_t seq);
esp_err_t pb_settings_set_volume(int volume);
esp_err_t pb_settings_set_wifi(const char *ssid, const char *password);
