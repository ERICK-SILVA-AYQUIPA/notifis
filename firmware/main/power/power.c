#include "power.h"

#include "battery_math.h"
#include "driver/gpio.h"
#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_cali_scheme.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "sdkconfig.h"

static const char *TAG = "power";
static adc_oneshot_unit_handle_t s_adc;
static adc_cali_handle_t s_cali;
static adc_channel_t s_chan;
static bool s_adc_ok;

static const int BTN_PINS[] = {CONFIG_PB_BTN_VOL_UP, CONFIG_PB_BTN_VOL_DOWN, CONFIG_PB_BTN_MUTE};
static bool s_pressed[3];
static int64_t s_pressed_at[3];
static bool s_long_fired;

esp_err_t pb_power_init(void)
{
    adc_unit_t unit;
    if (adc_oneshot_io_to_channel(CONFIG_PB_BATTERY_ADC_GPIO, &unit, &s_chan) == ESP_OK) {
        adc_oneshot_unit_init_cfg_t ucfg = {.unit_id = unit};
        adc_oneshot_chan_cfg_t ccfg = {.atten = ADC_ATTEN_DB_12, .bitwidth = ADC_BITWIDTH_DEFAULT};
        adc_cali_curve_fitting_config_t cal = {.unit_id = unit, .chan = s_chan, .atten = ADC_ATTEN_DB_12, .bitwidth = ADC_BITWIDTH_DEFAULT};
        s_adc_ok = adc_oneshot_new_unit(&ucfg, &s_adc) == ESP_OK &&
                   adc_oneshot_config_channel(s_adc, s_chan, &ccfg) == ESP_OK &&
                   adc_cali_create_scheme_curve_fitting(&cal, &s_cali) == ESP_OK;
    }
    if (!s_adc_ok) ESP_LOGW(TAG, "Medición de batería no disponible");

    uint64_t in_mask = 0;
    for (int i = 0; i < 3; i++) in_mask |= 1ULL << BTN_PINS[i];
    if (CONFIG_PB_CHARGE_PIN >= 0) in_mask |= 1ULL << CONFIG_PB_CHARGE_PIN;
    gpio_config_t in = {.pin_bit_mask = in_mask, .mode = GPIO_MODE_INPUT, .pull_up_en = GPIO_PULLUP_ENABLE};
    gpio_config(&in);

    gpio_config_t out = {
        .pin_bit_mask = (1ULL << CONFIG_PB_LED_R) | (1ULL << CONFIG_PB_LED_G) | (1ULL << CONFIG_PB_LED_B),
        .mode = GPIO_MODE_OUTPUT,
    };
    gpio_config(&out);
    pb_led_set(PB_LED_OFF);
    return ESP_OK;
}

int pb_power_battery_percent(void)
{
    if (!s_adc_ok) return -1;
    int sum = 0, raw = 0, mv = 0;
    for (int i = 0; i < 8; i++) {
        if (adc_oneshot_read(s_adc, s_chan, &raw) != ESP_OK) return -1;
        sum += raw;
    }
    if (adc_cali_raw_to_voltage(s_cali, sum / 8, &mv) != ESP_OK) return -1;
    int cell_mv = mv * CONFIG_PB_BATTERY_DIVIDER_X10 / 10;
    if (cell_mv < 2500) return -1; /* Sin batería conectada */
    return pb_battery_percent(cell_mv);
}

bool pb_power_is_charging(void)
{
    return CONFIG_PB_CHARGE_PIN >= 0 && gpio_get_level(CONFIG_PB_CHARGE_PIN) == 0;
}

pb_button_t pb_power_poll_button(void)
{
    int64_t now = esp_timer_get_time();
    for (int i = 0; i < 3; i++) {
        bool down = gpio_get_level(BTN_PINS[i]) == 0;
        if (down && !s_pressed[i]) {
            s_pressed[i] = true;
            s_pressed_at[i] = now;
            s_long_fired = false;
        } else if (down && i == 2 && !s_long_fired && now - s_pressed_at[i] > 1500000) {
            s_long_fired = true;
            return PB_BTN_MUTE_LONG;
        } else if (!down && s_pressed[i]) {
            s_pressed[i] = false;
            bool valid = now - s_pressed_at[i] > 30000; /* antirrebote 30 ms */
            if (valid && !(i == 2 && s_long_fired)) {
                return (pb_button_t)(PB_BTN_VOL_UP + i);
            }
        }
    }
    return PB_BTN_NONE;
}

void pb_led_set(pb_led_t state)
{
    int r = 0, g = 0, b = 0;
    switch (state) {
    case PB_LED_OK: g = 1; break;
    case PB_LED_PAYMENT: g = 1; b = 1; break;
    case PB_LED_WARNING: r = 1; g = 1; break;
    case PB_LED_ERROR: r = 1; break;
    case PB_LED_PAIRING: b = 1; break;
    default: break;
    }
    gpio_set_level(CONFIG_PB_LED_R, r);
    gpio_set_level(CONFIG_PB_LED_G, g);
    gpio_set_level(CONFIG_PB_LED_B, b);
}
