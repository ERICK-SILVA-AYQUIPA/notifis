#pragma once
#include <stdbool.h>
#include <stdint.h>

#include "display_state.h"
#include "esp_err.h"
#include "protocol.h"

esp_err_t pb_display_hw_init(void);

typedef struct {
    pb_screen_t screen;
    bool android_connected;
    bool wifi_connected;
    int battery;   /* -1 desconocido */
    bool charging;
    int volume;
    bool muted;
    const char *error; /* NULL si no hay error */
} pb_display_view_t;

/* Redibuja la barra de estado y la pantalla activa. */
void pb_display_render(const pb_display_view_t *view);
/* Datos del último pago mostrado en la pantalla "Nuevo pago". */
void pb_display_set_payment(const pb_payment_event_t *evt);
/* Muestra u oculta el código de vinculación BLE (0 lo oculta). */
void pb_display_show_passkey(uint32_t passkey);
