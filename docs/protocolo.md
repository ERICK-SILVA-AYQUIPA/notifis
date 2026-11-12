# Protocolo Android ↔ PAGOBOX Device

Versión 1.0 (sección 4 de la especificación).

## Transportes

| Canal | Uso | Seguridad |
|---|---|---|
| **BLE GATT** | Eventos, ACK, estado y comandos | LE Secure Connections con código de 6 dígitos mostrado en la pantalla del equipo (protección MITM). Vínculo guardado en ambos lados. |
| **Wi-Fi local (HTTP, puerto 8080)** | Audio de voz y actualización de firmware | Cabecera `X-Pagobox-Token`, un token aleatorio de 128 bits que el equipo genera en cada arranque y entrega solo por BLE cifrado. |

No se usa Internet en ningún momento.

### Servicio GATT

| Elemento | UUID | Propiedades |
|---|---|---|
| Servicio PAGOBOX | `6a4e0001-7a1b-4c2e-9b0a-50a90b0c0001` | Primario, anunciado |
| RX (celular → equipo) | `6a4e0002-7a1b-4c2e-9b0a-50a90b0c0001` | Write; requiere cifrado y autenticación |
| TX (equipo → celular) | `6a4e0003-7a1b-4c2e-9b0a-50a90b0c0001` | Notify; requiere cifrado y autenticación |

Cada mensaje es un JSON en UTF-8 terminado en `\n`. Si supera el MTU negociado (hasta 247), se envía en
varios fragmentos y el receptor lo reensambla hasta el `\n`.

## Mensajes del celular al equipo

### `PAYMENT_EVENT`

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
- Generado por `DeviceMessages.paymentEvent` (Kotlin) e interpretado por `pb_parse_message` (firmware).

### Comandos

| `type` | Campos | Efecto |
|---|---|---|
| `GET_STATUS` | — | El equipo responde `STATUS` |
| `SET_VOLUME` | `volume` 0–100 | Cambia y guarda el volumen; responde `STATUS` |
| `WIFI_CONFIG` | `ssid`, `password` | Guarda la red y se conecta; responde `STATUS` |
| `PLAY_TEST` | — | Reproduce el tono de prueba |

## Mensajes del equipo al celular

### `ACK`

```json
{ "type": "ACK", "eventId": "evt_000001", "sequence": 1, "stage": "RECEIVED" }
```

`stage`: `RECEIVED` (mensaje recibido) o `PLAYED` (anuncio reproducido).

> Un ACK confirma la recepción técnica del mensaje, **no** que el dinero haya ingresado a una cuenta.

### `STATUS`

```json
{
  "type": "STATUS", "deviceId": "A1B2C3D4E5F6", "firmware": "1.0.0",
  "battery": 82, "charging": false, "volume": 70,
  "wifiIp": "192.168.1.40", "audioToken": "…", "lastSequence": 41, "error": null
}
```

`battery` es `null` si no hay batería medible. Sin Wi-Fi, `wifiIp` es `null` y no se envía `audioToken`.

## Audio por Wi-Fi

`POST http://<wifiIp>:8080/audio?eventId=<eventId>` con cabecera `X-Pagobox-Token` y cuerpo WAV
PCM 16 bits mono (8–48 kHz, hasta 768 KB), generado con el TTS de Android.

## Secuencia de entrega

1. Android guarda el evento en Room (estado `INCOME_CANDIDATE`, anuncio `PENDING`).
2. Android envía `PAYMENT_EVENT` por BLE.
3. El equipo responde `ACK RECEIVED` (también a eventos repetidos, para cortar reintentos).
4. Si el número de secuencia es nuevo, lo guarda en NVS, muestra el pago y espera hasta 6 s el audio.
5. Android sintetiza la frase y la sube por Wi-Fi (si el equipo tiene IP).
6. El equipo reproduce el tono de aviso y la voz; sin audio, solo el tono (el importe queda en pantalla).
7. El equipo responde `ACK PLAYED`. Si la secuencia ya se había procesado, responde `PLAYED` sin volver a anunciar.
8. Android marca el evento como `ANNOUNCED` y registra el intento en `AnnouncementHistory`.

Sin `ACK RECEIVED` en 5 s o sin `ACK PLAYED` en 30 s, Android registra la falla, anuncia en el celular si
está activado el respaldo, y reintenta cada 15 s. Un aviso que no se pudo entregar en 30 minutos se marca como
no entregado y no se reproduce tarde.

## Actualización de firmware (OTA)

`POST http://<wifiIp>:8080/ota` con `X-Pagobox-Token` y el archivo `pagobox_device.bin`. El equipo escribe la
partición OTA libre, reinicia y confirma la nueva versión al arrancar; si no arranca, el bootloader vuelve a la anterior.
