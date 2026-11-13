#include <stdio.h>
#include <string.h>

#include "display_state.h"
#include "protocol.h"
#include "audio/wav.h"
#include "power/battery_math.h"
#include <stdint.h>

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

static int test_verification_values(void)
{
    /* Solo PROVIDER_CONFIRMED (v2) o VERIFIED (v1) cuentan como confirmados. */
    const char *base = "{\"type\":\"PAYMENT_EVENT\",\"eventId\":\"e\",\"sequence\":1,\"source\":\"YAPE\","
                       "\"verificationStatus\":\"%s\",\"test\":%s}";
    const char *values[] = {"PROVIDER_CONFIRMED", "VERIFIED", "INCOME_CANDIDATE", "DETECTED",
                            "PENDING_VERIFICATION", "DUPLICATE", "provider_confirmed"};
    const int confirmed[] = {1, 1, 0, 0, 0, 0, 0};
    char json[256];
    pb_payment_event_t e;
    for (size_t i = 0; i < sizeof(values) / sizeof(values[0]); i++) {
        snprintf(json, sizeof(json), base, values[i], "false");
        CHECK(pb_parse_payment_event(json, &e) == PB_PARSE_OK);
        CHECK((e.verification == PB_VERIFICATION_VERIFIED) == confirmed[i]);
        CHECK(!e.test_mode);
    }
    /* "test" debe ser booleano true; la cadena "true" no activa el modo prueba. */
    snprintf(json, sizeof(json), base, "INCOME_CANDIDATE", "\"true\"");
    CHECK(pb_parse_payment_event(json, &e) == PB_PARSE_OK);
    CHECK(!e.test_mode);
    snprintf(json, sizeof(json), base, "INCOME_CANDIDATE", "true");
    CHECK(pb_parse_payment_event(json, &e) == PB_PARSE_OK);
    CHECK(e.test_mode);
    CHECK(strcmp(pb_verification_label(&e), "PRUEBA - Posible ingreso (no verificado)") == 0);
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

static int test_messages(void)
{
    pb_message_in_t m;
    CHECK(pb_parse_message("{\"type\":\"SET_VOLUME\",\"volume\":150}", &m) == PB_PARSE_OK);
    CHECK(m.type == PB_MSG_SET_VOLUME && m.volume == 100);
    CHECK(pb_parse_message("{\"type\":\"WIFI_CONFIG\",\"ssid\":\"Tienda\",\"password\":\"x\"}", &m) == PB_PARSE_OK);
    CHECK(strcmp(m.wifi.ssid, "Tienda") == 0);
    CHECK(pb_parse_message("{\"type\":\"WIFI_CONFIG\",\"ssid\":\"\"}", &m) == PB_PARSE_MISSING_FIELD);
    CHECK(pb_parse_message("{\"type\":\"GET_STATUS\"}", &m) == PB_PARSE_OK && m.type == PB_MSG_GET_STATUS);
    CHECK(pb_parse_message("{\"type\":\"BORRAR_TODO\"}", &m) == PB_PARSE_WRONG_TYPE);
    return 0;
}

static int test_text(void)
{
    char out[64];
    pb_ascii_fold("Jos\xC3\xA9 \xC3\x91\xC3\xBA\xC3\xB1" "ez \xE2\x82\xAC" "!", out, sizeof(out));
    CHECK(strcmp(out, "Jose Nunez !") == 0);
    pb_format_amount(125050, "PEN", out, sizeof(out));
    CHECK(strcmp(out, "S/ 1,250.50") == 0);
    pb_format_amount(2000, "PEN", out, sizeof(out));
    CHECK(strcmp(out, "S/ 20.00") == 0);
    pb_format_amount(-1, "PEN", out, sizeof(out));
    CHECK(strcmp(out, "Importe no disponible") == 0);
    pb_payment_event_t e;
    CHECK(pb_parse_payment_event("{\"type\":\"PAYMENT_EVENT\",\"eventId\":\"e\",\"sequence\":1,\"source\":\"YAPE\","
                                 "\"timestamp\":\"2026-11-12T15:30:00-05:00\"}", &e) == PB_PARSE_OK);
    CHECK(strcmp(e.time_hhmm, "15:30") == 0);
    return 0;
}

static int test_status(void)
{
    char buf[256];
    pb_status_t s = {.device_id = "AABB", .firmware = "1.0", .battery = -1, .volume = 70, .wifi_ip = "", .audio_token = "t",
                     .last_sequence = 9, .error = NULL};
    CHECK(pb_build_status(buf, sizeof(buf), &s) > 0);
    CHECK(strstr(buf, "\"wifiIp\":null") != NULL);
    CHECK(strstr(buf, "audioToken") == NULL); /* sin Wi-Fi no se entrega el token */
    CHECK(strstr(buf, "\"lastSequence\":9") != NULL);
    return 0;
}

static int test_wav(void)
{
    uint8_t wav[44 + 8] = {0};
    memcpy(wav, "RIFF", 4);
    memcpy(wav + 8, "WAVEfmt ", 8);
    wav[16] = 16;           /* tamaño fmt */
    wav[20] = 1;            /* PCM */
    wav[22] = 1;            /* mono */
    wav[24] = 0x22; wav[25] = 0x56; /* 22050 Hz */
    wav[34] = 16;           /* bits */
    memcpy(wav + 36, "data", 4);
    wav[40] = 8;            /* 4 muestras */
    pb_wav_t w;
    CHECK(pb_wav_parse(wav, sizeof(wav), &w) == 0);
    CHECK(w.sample_rate == 22050 && w.sample_count == 4);
    wav[22] = 2; /* estéreo no soportado */
    CHECK(pb_wav_parse(wav, sizeof(wav), &w) != 0);
    CHECK(pb_wav_parse((const uint8_t *)"basura", 6, &w) != 0);
    return 0;
}

static int test_battery(void)
{
    CHECK(pb_battery_percent(4250) == 100);
    CHECK(pb_battery_percent(3800) == 50);
    CHECK(pb_battery_percent(3750) == 42);
    CHECK(pb_battery_percent(3200) == 0);
    return 0;
}

static char *read_fixture(const char *name)
{
    static char buf[2048];
    char path[512];
    snprintf(path, sizeof(path), "%s/%s", FIXTURES_DIR, name);
    FILE *f = fopen(path, "rb");
    if (f == NULL) return NULL;
    size_t n = fread(buf, 1, sizeof(buf) - 1, f);
    fclose(f);
    buf[n] = '\0';
    return buf;
}

static int test_contract_with_android(void)
{
    /* Archivos generados/validados por las pruebas Kotlin (EndToEndIntegrationTest). */
    char *json = read_fixture("payment_event.json");
    CHECK(json != NULL);
    pb_payment_event_t e;
    CHECK(pb_parse_payment_event(json, &e) == PB_PARSE_OK);
    CHECK(strcmp(e.event_id, "evt_000001") == 0);
    CHECK(e.sequence == 1);
    CHECK(e.amount_cents == 125050);
    CHECK(strcmp(e.currency, "PEN") == 0);
    CHECK(strcmp(e.time_hhmm, "15:30") == 0);
    char folded[PB_SENDER_MAX];
    pb_ascii_fold(e.sender, folded, sizeof(folded));
    CHECK(strcmp(folded, "Jose Nunez") == 0);
    CHECK(e.verification == PB_VERIFICATION_DETECTED); /* INCOME_CANDIDATE: no es confirmación */
    CHECK(!e.test_mode && !e.official);
    CHECK(strcmp(pb_verification_label(&e), "Posible ingreso (no verificado)") == 0);

    json = read_fixture("payment_event_confirmed.json");
    CHECK(json != NULL);
    CHECK(pb_parse_payment_event(json, &e) == PB_PARSE_OK);
    CHECK(e.verification == PB_VERIFICATION_VERIFIED);
    CHECK(e.test_mode && e.official);
    CHECK(e.amount_cents == 2500);
    CHECK(e.sender[0] == '\0');
    CHECK(strcmp(pb_verification_label(&e), "PRUEBA - Confirmado por proveedor") == 0);

    json = read_fixture("test_event.json");
    CHECK(json != NULL);
    pb_message_in_t m;
    CHECK(pb_parse_message(json, &m) == PB_PARSE_OK);
    CHECK(m.type == PB_MSG_TEST_EVENT);
    CHECK(strcmp(m.test_id, "test_1731443400000") == 0);
    return 0;
}

static int test_self_test_report(void)
{
    char buf[512];
    pb_self_test_t t = {.display_ok = true, .audio_ok = false, .battery = -1, .btn_up = 2, .reset_reason = "TASK_WATCHDOG",
                        .uptime_s = 5, .free_heap = 1000, .firmware = "1.1"};
    CHECK(pb_build_self_test(buf, sizeof(buf), &t) > 0);
    CHECK(strstr(buf, "\"audio\":false") != NULL);
    CHECK(strstr(buf, "\"volUp\":2") != NULL);
    CHECK(strstr(buf, "TASK_WATCHDOG") != NULL);
    pb_message_in_t m;
    CHECK(pb_parse_message("{\"type\":\"SELF_TEST\"}", &m) == PB_PARSE_OK && m.type == PB_MSG_SELF_TEST);
    CHECK(pb_parse_message("{\"type\":\"TEST_EVENT\"}", &m) == PB_PARSE_MISSING_FIELD);
    return 0;
}

int main(void)
{
    int failed = test_parse_event() + test_verification_values() + test_missing_optional_fields() + test_invalid_messages() +
                 test_ack_and_sequence() + test_display_states() +
                 test_messages() + test_text() + test_status() + test_wav() + test_battery() +
                 test_contract_with_android() + test_self_test_report();
    printf("%d comprobaciones correctas, %d pruebas fallidas\n", s_passed, failed);
    return failed ? 1 : 0;
}
