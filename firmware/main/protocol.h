#pragma once
/*
 * Protocolo Android <-> PAGOBOX Device (ver docs/protocolo.md).
 * Un ACK confirma la recepción técnica del mensaje, NO el ingreso del dinero.
 */
#include <stdbool.h>
#include <stdint.h>

#define PB_EVENT_ID_MAX 32
#define PB_SOURCE_MAX 16
#define PB_SENDER_MAX 48
#define PB_CURRENCY_MAX 4

typedef enum {
    PB_VERIFICATION_DETECTED = 0,
    PB_VERIFICATION_VERIFIED = 1,
} pb_verification_t;

typedef struct {
    char event_id[PB_EVENT_ID_MAX];
    uint32_t sequence;
    char source[PB_SOURCE_MAX];
    int64_t amount_cents; /* -1 si no hay importe */
    char currency[PB_CURRENCY_MAX];
    char sender[PB_SENDER_MAX]; /* vacío si no hay remitente */
    pb_verification_t verification;
} pb_payment_event_t;

typedef enum {
    PB_PARSE_OK = 0,
    PB_PARSE_INVALID_JSON,
    PB_PARSE_WRONG_TYPE,
    PB_PARSE_MISSING_FIELD,
} pb_parse_result_t;

/* Interpreta un mensaje PAYMENT_EVENT. No inventa campos ausentes. */
pb_parse_result_t pb_parse_payment_event(const char *json, pb_payment_event_t *out);

/* Escribe en buf un ACK de recepción ("RECEIVED") o de reproducción ("PLAYED"). */
int pb_build_ack(char *buf, int buf_len, const char *event_id, uint32_t sequence, const char *stage);

/* Devuelve false si la secuencia ya fue procesada (protección contra repetición). */
bool pb_is_new_sequence(uint32_t last_processed, uint32_t incoming);
