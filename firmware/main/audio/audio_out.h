#pragma once
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

esp_err_t pb_audio_init(void);
void pb_audio_set_volume(int volume); /* 0–100 */
int pb_audio_get_volume(void);
/* Reproduce PCM 16 bits mono de forma bloqueante (un anuncio a la vez). */
esp_err_t pb_audio_play_pcm(const int16_t *samples, size_t count, uint32_t sample_rate);
/* Tono de aviso usado cuando no hay audio de voz por Wi-Fi. */
esp_err_t pb_audio_play_chime(void);
