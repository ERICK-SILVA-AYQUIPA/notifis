#include "display_state.h"

void pb_display_init(pb_display_state_t *s)
{
    s->screen = PB_SCREEN_DISCONNECTED;
    s->shown_at_us = 0;
    s->android_connected = false;
}

void pb_display_on_payment(pb_display_state_t *s, int64_t now_us)
{
    s->screen = PB_SCREEN_NEW_PAYMENT;
    s->shown_at_us = now_us;
}

void pb_display_on_connection(pb_display_state_t *s, bool connected)
{
    s->android_connected = connected;
    if (!connected) {
        s->screen = PB_SCREEN_DISCONNECTED;
    } else if (s->screen == PB_SCREEN_DISCONNECTED) {
        s->screen = PB_SCREEN_WAITING;
    }
}

void pb_display_tick(pb_display_state_t *s, int64_t now_us)
{
    if (s->screen == PB_SCREEN_NEW_PAYMENT && now_us - s->shown_at_us >= PB_NEW_PAYMENT_SCREEN_US) {
        s->screen = s->android_connected ? PB_SCREEN_WAITING : PB_SCREEN_DISCONNECTED;
    }
}
