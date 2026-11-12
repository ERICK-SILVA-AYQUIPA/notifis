#pragma once
#include <stdbool.h>
#include <stddef.h>

#include "esp_err.h"

/* Se llama con cada mensaje completo (línea JSON) recibido del celular. */
typedef void (*pb_ble_rx_cb_t)(const char *line, size_t len);
/* Cambios de conexión y del código de vinculación (0 = ocultar). */
typedef void (*pb_ble_conn_cb_t)(bool connected);
typedef void (*pb_ble_passkey_cb_t)(unsigned long passkey);

esp_err_t pb_ble_init(const char *device_name, pb_ble_rx_cb_t rx, pb_ble_conn_cb_t conn, pb_ble_passkey_cb_t passkey);
bool pb_ble_is_connected(void);
/* Envía un mensaje (se agrega '\n' y se fragmenta según el MTU). */
esp_err_t pb_ble_send(const char *msg);
