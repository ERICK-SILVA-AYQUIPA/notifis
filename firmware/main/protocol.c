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

pb_parse_result_t pb_parse_payment_event(const char *json, pb_payment_event_t *out)
{
    memset(out, 0, sizeof(*out));
    cJSON *root = cJSON_Parse(json);
    if (root == NULL) {
        return PB_PARSE_INVALID_JSON;
    }

    pb_parse_result_t result = PB_PARSE_OK;
    const cJSON *type = cJSON_GetObjectItemCaseSensitive(root, "type");
    if (!cJSON_IsString(type) || strcmp(type->valuestring, "PAYMENT_EVENT") != 0) {
        result = PB_PARSE_WRONG_TYPE;
        goto done;
    }

    const cJSON *seq = cJSON_GetObjectItemCaseSensitive(root, "sequence");
    if (!cJSON_IsNumber(seq) || seq->valuedouble < 0) {
        result = PB_PARSE_MISSING_FIELD;
        goto done;
    }
    out->sequence = (uint32_t)seq->valuedouble;

    if (!copy_string(root, "eventId", out->event_id, sizeof(out->event_id), true) ||
        !copy_string(root, "source", out->source, sizeof(out->source), true)) {
        result = PB_PARSE_MISSING_FIELD;
        goto done;
    }
    copy_string(root, "currency", out->currency, sizeof(out->currency), false);
    copy_string(root, "sender", out->sender, sizeof(out->sender), false);

    const cJSON *amount = cJSON_GetObjectItemCaseSensitive(root, "amount");
    out->amount_cents = cJSON_IsNumber(amount) ? (int64_t)llround(amount->valuedouble * 100.0) : -1;

    const cJSON *verification = cJSON_GetObjectItemCaseSensitive(root, "verificationStatus");
    out->verification = (cJSON_IsString(verification) && strcmp(verification->valuestring, "VERIFIED") == 0)
                            ? PB_VERIFICATION_VERIFIED
                            : PB_VERIFICATION_DETECTED;

done:
    cJSON_Delete(root);
    return result;
}

int pb_build_ack(char *buf, int buf_len, const char *event_id, uint32_t sequence, const char *stage)
{
    return snprintf(buf, buf_len, "{\"type\":\"ACK\",\"eventId\":\"%s\",\"sequence\":%lu,\"stage\":\"%s\"}",
                    event_id, (unsigned long)sequence, stage);
}

bool pb_is_new_sequence(uint32_t last_processed, uint32_t incoming)
{
    return incoming > last_processed;
}
