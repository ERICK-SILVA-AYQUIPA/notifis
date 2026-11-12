#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

/* Audio recibido del celular para un evento concreto. El receptor libera [data] con free(). */
typedef struct {
    char event_id[32];
    uint8_t *data;
    size_t len;
} pb_audio_clip_t;

esp_err_t pb_wifi_init(void);
/* Conecta (o reconecta) a la red indicada. SSID vacío desactiva el Wi-Fi. */
esp_err_t pb_wifi_connect(const char *ssid, const char *password);
bool pb_wifi_is_connected(void);
/* IP actual ("" si no hay conexión). */
const char *pb_wifi_ip(void);
/* Token aleatorio que el celular debe enviar para subir audio u OTA. Cambia en cada arranque. */
const char *pb_wifi_audio_token(void);
/* Espera un clip de audio para [event_id] hasta [timeout_ms]. */
bool pb_wifi_wait_audio(const char *event_id, uint32_t timeout_ms, pb_audio_clip_t *out);
