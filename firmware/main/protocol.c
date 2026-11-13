#include "protocol.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

#include "cJSON.h"

static bool copy_string(const cJSON *obj, const char *key, char *dst, size_t dst_len, bool required)
{
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(obj, key);
    if (!cJSON_IsString(item) || item->valuestring == NULL) {
        dst[0] = '\0';
        return !required;
    }
    strncpy(dst, item->valuestring, dst_len - 1);
    dst[dst_len - 1] = '\0';
    return true;
}

static pb_parse_result_t parse_payment(const cJSON *root, pb_payment_event_t *out)
{
    memset(out, 0, sizeof(*out));
    const cJSON *seq = cJSON_GetObjectItemCaseSensitive(root, "sequence");
    if (!cJSON_IsNumber(seq) || seq->valuedouble < 0) {
        return PB_PARSE_MISSING_FIELD;
    }
    out->sequence = (uint32_t)seq->valuedouble;

    if (!copy_string(root, "eventId", out->event_id, sizeof(out->event_id), true) ||
        !copy_string(root, "source", out->source, sizeof(out->source), true)) {
        return PB_PARSE_MISSING_FIELD;
    }
    copy_string(root, "currency", out->currency, sizeof(out->currency), false);
    copy_string(root, "sender", out->sender, sizeof(out->sender), false);

    const cJSON *amount = cJSON_GetObjectItemCaseSensitive(root, "amount");
    out->amount_cents = cJSON_IsNumber(amount) ? (int64_t)llround(amount->valuedouble * 100.0) : -1;

    /* "2026-11-12T15:30:00-05:00": la hora local está en las posiciones 11..15. */
    char ts[40];
    copy_string(root, "timestamp", ts, sizeof(ts), false);
    if (strlen(ts) >= 16 && ts[10] == 'T' && ts[13] == ':') {
        memcpy(out->time_hhmm, ts + 11, 5);
        out->time_hhmm[5] = '\0';
    }

    const cJSON *verification = cJSON_GetObjectItemCaseSensitive(root, "verificationStatus");
    out->verification = (cJSON_IsString(verification) && strcmp(verification->valuestring, "VERIFIED") == 0)
                            ? PB_VERIFICATION_VERIFIED
                            : PB_VERIFICATION_DETECTED;
    return PB_PARSE_OK;
}

pb_parse_result_t pb_parse_message(const char *json, pb_message_in_t *out)
{
    memset(out, 0, sizeof(*out));
    cJSON *root = cJSON_Parse(json);
    if (root == NULL) {
        return PB_PARSE_INVALID_JSON;
    }
    pb_parse_result_t result = PB_PARSE_OK;
    const cJSON *type = cJSON_GetObjectItemCaseSensitive(root, "type");
    const char *t = cJSON_IsString(type) ? type->valuestring : "";

    if (strcmp(t, "PAYMENT_EVENT") == 0) {
        out->type = PB_MSG_PAYMENT_EVENT;
        result = parse_payment(root, &out->payment);
    } else if (strcmp(t, "GET_STATUS") == 0) {
        out->type = PB_MSG_GET_STATUS;
    } else if (strcmp(t, "PLAY_TEST") == 0) {
        out->type = PB_MSG_PLAY_TEST;
    } else if (strcmp(t, "SELF_TEST") == 0) {
        out->type = PB_MSG_SELF_TEST;
    } else if (strcmp(t, "TEST_EVENT") == 0) {
        out->type = PB_MSG_TEST_EVENT;
        if (!copy_string(root, "testId", out->test_id, sizeof(out->test_id), true) || out->test_id[0] == '\0') {
            result = PB_PARSE_MISSING_FIELD;
        }
    } else if (strcmp(t, "SET_VOLUME") == 0) {
        out->type = PB_MSG_SET_VOLUME;
        const cJSON *v = cJSON_GetObjectItemCaseSensitive(root, "volume");
        if (!cJSON_IsNumber(v)) {
            result = PB_PARSE_MISSING_FIELD;
        } else {
            int vol = (int)v->valuedouble;
            out->volume = vol < 0 ? 0 : (vol > 100 ? 100 : vol);
        }
    } else if (strcmp(t, "WIFI_CONFIG") == 0) {
        out->type = PB_MSG_WIFI_CONFIG;
        if (!copy_string(root, "ssid", out->wifi.ssid, sizeof(out->wifi.ssid), true) || out->wifi.ssid[0] == '\0') {
            result = PB_PARSE_MISSING_FIELD;
        }
        copy_string(root, "password", out->wifi.password, sizeof(out->wifi.password), false);
    } else {
        out->type = PB_MSG_UNKNOWN;
        result = PB_PARSE_WRONG_TYPE;
    }
    cJSON_Delete(root);
    return result;
}

pb_parse_result_t pb_parse_payment_event(const char *json, pb_payment_event_t *out)
{
    pb_message_in_t msg;
    pb_parse_result_t r = pb_parse_message(json, &msg);
    if (r == PB_PARSE_INVALID_JSON) {
        memset(out, 0, sizeof(*out));
        return r;
    }
    if (msg.type != PB_MSG_PAYMENT_EVENT) {
        memset(out, 0, sizeof(*out));
        return PB_PARSE_WRONG_TYPE;
    }
    *out = msg.payment;
    return r;
}

int pb_build_ack(char *buf, int buf_len, const char *event_id, uint32_t sequence, const char *stage)
{
    return snprintf(buf, buf_len, "{\"type\":\"ACK\",\"eventId\":\"%s\",\"sequence\":%lu,\"stage\":\"%s\"}",
                    event_id, (unsigned long)sequence, stage);
}

int pb_build_status(char *buf, int buf_len, const pb_status_t *s)
{
    cJSON *o = cJSON_CreateObject();
    cJSON_AddStringToObject(o, "type", "STATUS");
    cJSON_AddStringToObject(o, "deviceId", s->device_id);
    cJSON_AddStringToObject(o, "firmware", s->firmware);
    if (s->battery >= 0) {
        cJSON_AddNumberToObject(o, "battery", s->battery);
    } else {
        cJSON_AddNullToObject(o, "battery");
    }
    cJSON_AddBoolToObject(o, "charging", s->charging);
    cJSON_AddNumberToObject(o, "volume", s->volume);
    if (s->wifi_ip != NULL && s->wifi_ip[0] != '\0') {
        cJSON_AddStringToObject(o, "wifiIp", s->wifi_ip);
        cJSON_AddStringToObject(o, "audioToken", s->audio_token);
    } else {
        cJSON_AddNullToObject(o, "wifiIp");
    }
    cJSON_AddNumberToObject(o, "lastSequence", s->last_sequence);
    if (s->error != NULL) {
        cJSON_AddStringToObject(o, "error", s->error);
    }
    if (s->reset_reason != NULL) {
        cJSON_AddStringToObject(o, "resetReason", s->reset_reason);
    }
    cJSON_AddNumberToObject(o, "uptimeS", s->uptime_s);
    bool ok = cJSON_PrintPreallocated(o, buf, buf_len, false);
    cJSON_Delete(o);
    return ok ? (int)strlen(buf) : -1;
}

int pb_build_self_test(char *buf, int buf_len, const pb_self_test_t *t)
{
    cJSON *o = cJSON_CreateObject();
    cJSON_AddStringToObject(o, "type", "SELF_TEST_RESULT");
    cJSON_AddStringToObject(o, "firmware", t->firmware);
    cJSON_AddBoolToObject(o, "display", t->display_ok);
    cJSON_AddBoolToObject(o, "audio", t->audio_ok);
    cJSON_AddBoolToObject(o, "ble", t->ble_connected);
    cJSON_AddBoolToObject(o, "wifi", t->wifi_connected);
    if (t->battery >= 0) {
        cJSON_AddNumberToObject(o, "battery", t->battery);
    } else {
        cJSON_AddNullToObject(o, "battery");
    }
    cJSON_AddBoolToObject(o, "charging", t->charging);
    cJSON *b = cJSON_AddObjectToObject(o, "buttons");
    cJSON_AddNumberToObject(b, "volUp", t->btn_up);
    cJSON_AddNumberToObject(b, "volDown", t->btn_down);
    cJSON_AddNumberToObject(b, "mute", t->btn_mute);
    cJSON_AddStringToObject(o, "resetReason", t->reset_reason);
    cJSON_AddNumberToObject(o, "uptimeS", t->uptime_s);
    cJSON_AddNumberToObject(o, "freeHeap", t->free_heap);
    bool ok = cJSON_PrintPreallocated(o, buf, buf_len, false);
    cJSON_Delete(o);
    return ok ? (int)strlen(buf) : -1;
}

bool pb_is_new_sequence(uint32_t last_processed, uint32_t incoming)
{
    return incoming > last_processed;
}

void pb_ascii_fold(const char *in, char *out, size_t out_len)
{
    size_t o = 0;
    const unsigned char *p = (const unsigned char *)in;
    while (*p != '\0' && o + 1 < out_len) {
        if (*p < 0x80) {
            if (*p >= 0x20) {
                out[o++] = (char)*p;
            }
            p++;
            continue;
        }
        if (*p == 0xC3 && p[1] != '\0') {
            /* Bloque Latin-1 (U+00C0..U+00FF) en UTF-8: 0xC3 0x80..0xBF */
            static const char map[65] = "AAAAAAACEEEEIIIIDNOOOOOxOUUUUYTsaaaaaaaceeeeiiiidnooooo/ouuuuyty";
            unsigned char c = p[1];
            if (c >= 0x80 && c <= 0xBF) {
                char m = map[c - 0x80];
                if (m != 'x' && m != '/') {
                    out[o++] = m;
                }
            }
            p += 2;
            continue;
        }
        /* Otros caracteres multibyte: se omiten completos. */
        p++;
        while ((*p & 0xC0) == 0x80) {
            p++;
        }
    }
    out[o] = '\0';
}

void pb_format_amount(int64_t cents, const char *currency, char *out, size_t out_len)
{
    if (cents < 0) {
        snprintf(out, out_len, "Importe no disponible");
        return;
    }
    const char *symbol = (currency != NULL && strcmp(currency, "USD") == 0) ? "US$" : "S/";
    int64_t units = cents / 100;
    int frac = (int)(cents % 100);
    char digits[32];
    snprintf(digits, sizeof(digits), "%lld", (long long)units);
    char grouped[48];
    size_t len = strlen(digits), g = 0;
    for (size_t i = 0; i < len; i++) {
        grouped[g++] = digits[i];
        size_t remaining = len - i - 1;
        if (remaining > 0 && remaining % 3 == 0) {
            grouped[g++] = ',';
        }
    }
    grouped[g] = '\0';
    snprintf(out, out_len, "%s %s.%02d", symbol, grouped, frac);
}
