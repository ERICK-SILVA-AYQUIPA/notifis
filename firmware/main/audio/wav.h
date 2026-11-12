#pragma once
#include <stddef.h>
#include <stdint.h>

/* Datos PCM dentro de un archivo WAV (sin copiar). */
typedef struct {
    const int16_t *samples;
    size_t sample_count;
    uint32_t sample_rate;
} pb_wav_t;

/* Acepta WAV PCM 16 bits mono, como los genera el TTS de Android. Devuelve 0 si es válido. */
int pb_wav_parse(const uint8_t *data, size_t len, pb_wav_t *out);
