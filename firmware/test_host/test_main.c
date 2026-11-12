#include <stdio.h>
#include <string.h>

#include "display_state.h"
#include "protocol.h"

static int s_passed;
#define CHECK(cond)                                                                   \
    do {                                                                              \
        if (!(cond)) {                                                                \
            fprintf(stderr, "FALLO %s:%d: %s\n", __FILE__, __LINE__, #cond);          \
            return 1;                                                                 \
        }                                                                             \
        s_passed++;                                                                   \
    } while (0)

static int test_parse_event(void)
{
    const char *json =
        "{\"type\":\"PAYMENT_EVENT\",\"eventId\":\"evt_000001\",\"sequence\":7,\"source\":\"YAPE\","
        "\"eventType\":\"INCOMING_TRANSFER\",\"amount\":35.50,\"currency\":\"PEN\",\"sender\":\"Juan\","
        "\"timestamp\":\"2026-11-12T15:30:00-05:00\",\"verificationStatus\":\"DETECTED\","
        "\"announcementStatus\":\"PENDING\"}";
    pb_payment_event_t e;
    CHECK(pb_parse_payment_event(json, &e) == PB_PARSE_OK);
    CHECK(strcmp(e.event_id, "evt_000001") == 0);
    CHECK(e.sequence == 7);
    CHECK(e.amount_cents == 3550);
    CHECK(strcmp(e.sender, "Juan") == 0);
    CHECK(e.verification == PB_VERIFICATION_DETECTED);
    return 0;
}

static int test_missing_optional_fields(void)
{
    const char *json = "{\"type\":\"PAYMENT_EVENT\",\"eventId\":\"e\",\"sequence\":1,\"source\":\"PLIN\","
                       "\"amount\":null,\"sender\":null}";
    pb_payment_event_t e;
    CHECK(pb_parse_payment_event(json, &e) == PB_PARSE_OK);
    CHECK(e.amount_cents == -1);
    CHECK(e.sender[0] == '\0');
    return 0;
}

static int test_invalid_messages(void)
{
    pb_payment_event_t e;
    CHECK(pb_parse_payment_event("no es json", &e) == PB_PARSE_INVALID_JSON);
    CHECK(pb_parse_payment_event("{\"type\":\"OTRO\"}", &e) == PB_PARSE_WRONG_TYPE);
    CHECK(pb_parse_payment_event("{\"type\":\"PAYMENT_EVENT\",\"sequence\":1}", &e) == PB_PARSE_MISSING_FIELD);
    return 0;
}

static int test_ack_and_sequence(void)
{
    char buf[128];
    pb_build_ack(buf, sizeof(buf), "evt_1", 3, "PLAYED");
    CHECK(strcmp(buf, "{\"type\":\"ACK\",\"eventId\":\"evt_1\",\"sequence\":3,\"stage\":\"PLAYED\"}") == 0);
    CHECK(pb_is_new_sequence(2, 3));
    CHECK(!pb_is_new_sequence(3, 3));
    CHECK(!pb_is_new_sequence(5, 4));
    return 0;
}

static int test_display_states(void)
{
    pb_display_state_t s;
    pb_display_init(&s);
    CHECK(s.screen == PB_SCREEN_DISCONNECTED);
    pb_display_on_connection(&s, true);
    CHECK(s.screen == PB_SCREEN_WAITING);
    pb_display_on_payment(&s, 1000);
    CHECK(s.screen == PB_SCREEN_NEW_PAYMENT);
    pb_display_tick(&s, 1000 + PB_NEW_PAYMENT_SCREEN_US - 1);
    CHECK(s.screen == PB_SCREEN_NEW_PAYMENT);
    pb_display_tick(&s, 1000 + PB_NEW_PAYMENT_SCREEN_US);
    CHECK(s.screen == PB_SCREEN_WAITING);
    pb_display_on_connection(&s, false);
    CHECK(s.screen == PB_SCREEN_DISCONNECTED);
    return 0;
}

int main(void)
{
    int failed = test_parse_event() + test_missing_optional_fields() + test_invalid_messages() +
                 test_ack_and_sequence() + test_display_states();
    printf("%d comprobaciones correctas, %d pruebas fallidas\n", s_passed, failed);
    return failed ? 1 : 0;
}
