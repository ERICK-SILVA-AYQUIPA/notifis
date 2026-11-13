#pragma once
/*
 * Protocolo Android <-> PAGOBOX Device (ver docs/protocolo.md).
 * Un ACK confirma la recepción técnica del mensaje, NO el ingreso del dinero.
 */
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define PB_EVENT_ID_MAX 32
#define PB_SOURCE_MAX 16
#define PB_SENDER_MAX 48
#define PB_CURRENCY_MAX 4
#define PB_SSID_MAX 33
#define PB_PASS_MAX 65

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
    char time_hhmm[6]; /* "15:30" tomado del timestamp local del evento; vacío si no viene */
} pb_payment_event_t;

typedef enum {
    PB_PARSE_OK = 0,
    PB_PARSE_INVALID_JSON,
    PB_PARSE_WRONG_TYPE,
    PB_PARSE_MISSING_FIELD,
} pb_parse_result_t;

typedef enum {
    PB_MSG_UNKNOWN = 0,
    PB_MSG_PAYMENT_EVENT,
    PB_MSG_GET_STATUS,
    PB_MSG_SET_VOLUME,
    PB_MSG_WIFI_CONFIG,
    PB_MSG_PLAY_TEST,
    PB_MSG_TEST_EVENT, /* prueba de entrega completa: no es un pago */
    PB_MSG_SELF_TEST,  /* autoprueba de componentes */
} pb_msg_type_t;

typedef struct {
    pb_msg_type_t type;
    union {
        pb_payment_event_t payment;
        int volume;
        char test_id[PB_EVENT_ID_MAX];
        struct {
            char ssid[PB_SSID_MAX];
            char password[PB_PASS_MAX];
        } wifi;
    };
} pb_message_in_t;

/* Interpreta cualquier mensaje entrante. No inventa campos ausentes. */
pb_parse_result_t pb_parse_message(const char *json, pb_message_in_t *out);

/* Interpreta un mensaje PAYMENT_EVENT. */
pb_parse_result_t pb_parse_payment_event(const char *json, pb_payment_event_t *out);

/* ACK de recepción ("RECEIVED") o de reproducción ("PLAYED"). Devuelve la longitud escrita. */
int pb_build_ack(char *buf, int buf_len, const char *event_id, uint32_t sequence, const char *stage);

typedef struct {
    const char *device_id;
    const char *firmware;
    int battery;     /* -1 si no se conoce */
    bool charging;
    int volume;
    const char *wifi_ip; /* NULL o "" si no hay Wi-Fi */
    const char *audio_token;
    uint32_t last_sequence;
    const char *error; /* NULL si no hay error */
    const char *reset_reason;
    uint32_t uptime_s;
} pb_status_t;

int pb_build_status(char *buf, int buf_len, const pb_status_t *s);

typedef struct {
    bool display_ok;
    bool audio_ok;
    bool ble_connected;
    bool wifi_connected;
    int battery;          /* -1 si no hay lectura */
    bool charging;
    uint32_t btn_up, btn_down, btn_mute; /* pulsaciones desde el arranque */
    const char *reset_reason;
    uint32_t uptime_s;
    uint32_t free_heap;
    const char *firmware;
} pb_self_test_t;

int pb_build_self_test(char *buf, int buf_len, const pb_self_test_t *t);

/* Devuelve false si la secuencia ya fue procesada (protección contra repetición). */
bool pb_is_new_sequence(uint32_t last_processed, uint32_t incoming);

/*
 * Convierte texto UTF-8 en ASCII apto para la fuente de la pantalla:
 * "José Ñúñez" -> "Jose Nunez". Los caracteres no representables se omiten.
 */
void pb_ascii_fold(const char *in, char *out, size_t out_len);

/* Formatea céntimos como "S/ 1,250.50". */
void pb_format_amount(int64_t cents, const char *currency, char *out, size_t out_len);
