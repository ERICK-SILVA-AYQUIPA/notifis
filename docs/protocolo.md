# Protocolo Android ↔ PAGOBOX Device

Versión 0.1 (borrador, sección 4 de la especificación).

## Transporte

- **BLE GATT:** conexión, estado, comandos y eventos de pago.
- **Wi-Fi local:** transferencia de audio cuando sea necesario (por validar en prototipo).
- No depende de Internet.

## Mensajes

### `PAYMENT_EVENT` (Android → Device)

```json
{
  "type": "PAYMENT_EVENT",
  "eventId": "evt_000001",
  "sequence": 1,
  "source": "YAPE",
  "eventType": "INCOMING_TRANSFER",
  "amount": 35.00,
  "currency": "PEN",
  "sender": "Juan",
  "timestamp": "2026-11-12T15:30:00-05:00",
  "verificationStatus": "DETECTED",
  "announcementStatus": "PENDING"
}
```

- `amount`, `currency` y `sender` pueden ser `null`: nunca se inventan.
- `verificationStatus` es `DETECTED` salvo confirmación oficial (`VERIFIED`).
- Generado por `DeviceMessages.paymentEvent` (Kotlin) e interpretado por `pb_parse_payment_event` (firmware).

### `ACK` (Device → Android)

```json
{ "type": "ACK", "eventId": "evt_000001", "sequence": 1, "stage": "RECEIVED" }
```

`stage`: `RECEIVED` (mensaje recibido) o `PLAYED` (anuncio reproducido).

> Un ACK confirma la recepción técnica del mensaje, **no** que el dinero haya ingresado a una cuenta.

## Reglas

1. Android registra el evento antes de enviarlo.
2. El dispositivo responde `ACK RECEIVED` también a eventos repetidos, para cortar los reintentos.
3. El dispositivo solo anuncia secuencias mayores a la última procesada (persistida en NVS).
4. Sin ACK, Android conserva el evento y reintenta al reconectar; la cola nunca reproduce dos veces un `eventId` ya anunciado.
