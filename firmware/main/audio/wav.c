#include "wav.h"

#include <string.h>

static uint32_t rd32(const uint8_t *p) { return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24); }
static uint16_t rd16(const uint8_t *p) { return (uint16_t)(p[0] | (p[1] << 8)); }

int pb_wav_parse(const uint8_t *data, size_t len, pb_wav_t *out)
{
    memset(out, 0, sizeof(*out));
    if (len < 12 || memcmp(data, "RIFF", 4) != 0 || memcmp(data + 8, "WAVE", 4) != 0) {
        return -1;
    }
    size_t pos = 12;
    int have_fmt = 0;
    while (pos + 8 <= len) {
        const uint8_t *chunk = data + pos;
        uint32_t size = rd32(chunk + 4);
        size_t body = pos + 8;
        if (memcmp(chunk, "fmt ", 4) == 0) {
            if (size < 16 || body + 16 > len) {
                return -2;
            }
            uint16_t format = rd16(data + body);
            uint16_t channels = rd16(data + body + 2);
            out->sample_rate = rd32(data + body + 4);
            uint16_t bits = rd16(data + body + 14);
            if (format != 1 || channels != 1 || bits != 16 || out->sample_rate < 8000 || out->sample_rate > 48000) {
                return -3; /* Solo PCM 16 bits mono */
            }
            have_fmt = 1;
        } else if (memcmp(chunk, "data", 4) == 0) {
            if (!have_fmt) {
                return -4;
            }
            /* El TTS puede escribir un tamaño 0 o mayor al real si el archivo quedó abierto. */
            size_t available = len - body;
            size_t bytes = (size == 0 || size > available) ? available : size;
            out->samples = (const int16_t *)(const void *)(data + body);
            out->sample_count = bytes / 2;
            return out->sample_count > 0 ? 0 : -5;
        }
        pos = body + size + (size & 1);
    }
    return -6;
}
