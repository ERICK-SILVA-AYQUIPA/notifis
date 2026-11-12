#pragma once
#include <stdbool.h>

#include "esp_err.h"

typedef enum { PB_BTN_NONE = 0, PB_BTN_VOL_UP, PB_BTN_VOL_DOWN, PB_BTN_MUTE, PB_BTN_MUTE_LONG } pb_button_t;

typedef enum { PB_LED_OFF, PB_LED_OK, PB_LED_PAYMENT, PB_LED_WARNING, PB_LED_ERROR, PB_LED_PAIRING } pb_led_t;

esp_err_t pb_power_init(void);
/* Porcentaje de batería (-1 si no hay lectura válida). */
int pb_power_battery_percent(void);
bool pb_power_is_charging(void);
/* Devuelve el botón pulsado desde la última llamada (con antirrebote). */
pb_button_t pb_power_poll_button(void);
void pb_led_set(pb_led_t state);
