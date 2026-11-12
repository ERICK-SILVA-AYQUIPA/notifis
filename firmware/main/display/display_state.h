#pragma once
/* Estados de la pantalla (sección 2.5). */
#include <stdbool.h>
#include <stdint.h>

typedef enum {
    PB_SCREEN_WAITING = 0,     /* Esperando pagos */
    PB_SCREEN_NEW_PAYMENT,     /* Nuevo pago recibido */
    PB_SCREEN_DISCONNECTED,    /* Sistema desconectado o con errores */
} pb_screen_t;

typedef struct {
    pb_screen_t screen;
    int64_t shown_at_us;
    bool android_connected;
} pb_display_state_t;

#define PB_NEW_PAYMENT_SCREEN_US (8LL * 1000 * 1000)

void pb_display_init(pb_display_state_t *s);
void pb_display_on_payment(pb_display_state_t *s, int64_t now_us);
void pb_display_on_connection(pb_display_state_t *s, bool connected);
/* Vuelve a la pantalla principal tras mostrar el último evento. */
void pb_display_tick(pb_display_state_t *s, int64_t now_us);
