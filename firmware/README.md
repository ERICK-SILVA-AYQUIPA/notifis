# Firmware PAGOBOX Device (ESP32-S3)

Proyecto ESP-IDF (v5.x) con FreeRTOS.

## Compilar

```bash
. $IDF_PATH/export.sh
cd firmware
idf.py set-target esp32s3
idf.py build flash monitor
```

## Qué incluye hoy

| Módulo | Estado |
|---|---|
| `protocol.c` | Lectura de `PAYMENT_EVENT`, construcción de ACK y control de secuencia |
| `display_state.c` | Estados de pantalla: esperando, nuevo pago, desconectado (vuelve solo a la principal) |
| `main.c` | NVS (última secuencia persistente), cola de mensajes, watchdog de tareas |

## Pendiente

- Servicio BLE GATT (control, estado, eventos) — fase 4.
- Transporte de audio por Wi-Fi local y paquete de voces preinstaladas — fase 4, a validar en prototipo.
- Driver de pantalla TFT IPS, salida I2S al amplificador clase D, LED RGB y botones — fase 5.
- Gestión de batería, OTA y cifrado/emparejamiento — fases 5 y 7.

Este firmware aún no se ha compilado en hardware real.
