#include "audio_out.h"

#include <math.h>
#include <stdlib.h>

#include "driver/gpio.h"
#include "driver/i2s_std.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "sdkconfig.h"

static const char *TAG = "audio";
static i2s_chan_handle_t s_tx;
static uint32_t s_rate = 16000;
static volatile int s_volume = 70;
static SemaphoreHandle_t s_lock;

#define CHUNK 512

esp_err_t pb_audio_init(void)
{
    s_lock = xSemaphoreCreateMutex();
    if (CONFIG_PB_AMP_PIN_SD >= 0) {
        gpio_config_t io = {.pin_bit_mask = 1ULL << CONFIG_PB_AMP_PIN_SD, .mode = GPIO_MODE_OUTPUT};
        gpio_config(&io);
        gpio_set_level(CONFIG_PB_AMP_PIN_SD, 0);
    }
    i2s_chan_config_t chan_cfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_0, I2S_ROLE_MASTER);
    chan_cfg.auto_clear = true;
    esp_err_t err = i2s_new_channel(&chan_cfg, &s_tx, NULL);
    if (err != ESP_OK) {
        return err;
    }
    i2s_std_config_t std_cfg = {
        .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(16000),
        .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO),
        .gpio_cfg = {
            .mclk = I2S_GPIO_UNUSED,
            .bclk = CONFIG_PB_I2S_PIN_BCLK,
            .ws = CONFIG_PB_I2S_PIN_WS,
            .dout = CONFIG_PB_I2S_PIN_DOUT,
            .din = I2S_GPIO_UNUSED,
            .invert_flags = {.mclk_inv = false, .bclk_inv = false, .ws_inv = false},
        },
    };
    err = i2s_channel_init_std_mode(s_tx, &std_cfg);
    if (err == ESP_OK) {
        err = i2s_channel_enable(s_tx);
    }
    ESP_LOGI(TAG, "I2S listo (%s)", esp_err_to_name(err));
    return err;
}

void pb_audio_set_volume(int volume) { s_volume = volume < 0 ? 0 : (volume > 100 ? 100 : volume); }
int pb_audio_get_volume(void) { return s_volume; }

static esp_err_t set_rate(uint32_t rate)
{
    if (rate == s_rate) {
        return ESP_OK;
    }
    i2s_channel_disable(s_tx);
    i2s_std_clk_config_t clk = I2S_STD_CLK_DEFAULT_CONFIG(rate);
    esp_err_t err = i2s_channel_reconfig_std_clock(s_tx, &clk);
    i2s_channel_enable(s_tx);
    if (err == ESP_OK) {
        s_rate = rate;
    }
    return err;
}

esp_err_t pb_audio_play_pcm(const int16_t *samples, size_t count, uint32_t sample_rate)
{
    if (s_tx == NULL) {
        return ESP_ERR_INVALID_STATE;
    }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    esp_err_t err = set_rate(sample_rate);
    if (CONFIG_PB_AMP_PIN_SD >= 0) {
        gpio_set_level(CONFIG_PB_AMP_PIN_SD, 1);
    }
    int16_t buf[CHUNK];
    /* Curva perceptual simple: volumen^2 */
    int32_t gain = (int32_t)s_volume * s_volume; /* 0..10000 */
    for (size_t i = 0; err == ESP_OK && i < count; i += CHUNK) {
        size_t n = count - i < CHUNK ? count - i : CHUNK;
        for (size_t k = 0; k < n; k++) {
            buf[k] = (int16_t)(((int32_t)samples[i + k] * gain) / 10000);
        }
        size_t written = 0;
        err = i2s_channel_write(s_tx, buf, n * sizeof(int16_t), &written, pdMS_TO_TICKS(1000));
    }
    /* Silencio final para vaciar el DMA sin chasquidos. */
    int16_t silence[CHUNK] = {0};
    size_t written = 0;
    i2s_channel_write(s_tx, silence, sizeof(silence), &written, pdMS_TO_TICKS(200));
    if (CONFIG_PB_AMP_PIN_SD >= 0) {
        gpio_set_level(CONFIG_PB_AMP_PIN_SD, 0);
    }
    xSemaphoreGive(s_lock);
    return err;
}

esp_err_t pb_audio_play_chime(void)
{
    /* Dos tonos ascendentes (880 Hz y 1320 Hz) con envolvente para evitar chasquidos. */
    const uint32_t rate = 16000;
    const size_t tone = rate / 5; /* 200 ms */
    const size_t total = tone * 2;
    int16_t *pcm = malloc(total * sizeof(int16_t));
    if (pcm == NULL) {
        return ESP_ERR_NO_MEM;
    }
    for (size_t i = 0; i < total; i++) {
        double f = i < tone ? 880.0 : 1320.0;
        size_t t = i % tone;
        double env = t < 400 ? t / 400.0 : (t > tone - 800 ? (tone - t) / 800.0 : 1.0);
        pcm[i] = (int16_t)(sin(2.0 * M_PI * f * i / rate) * 12000.0 * env);
    }
    esp_err_t err = pb_audio_play_pcm(pcm, total, rate);
    free(pcm);
    return err;
}
