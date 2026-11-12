# PAGOBOX PERÚ

Sistema inteligente de detección, notificación y anuncio de pagos digitales para negocios peruanos:
una app Android que detecta los pagos recibidos (Yape, Plin y bancos compatibles) y un dispositivo
ESP32 con pantalla y parlante que los anuncia en voz alta.

📄 Especificación completa: [docs/especificacion-maestra.md](docs/especificacion-maestra.md)

> **Principio fundamental:** nunca anunciar un pago como verificado si solamente existe una
> notificación que no ofrece una confirmación bancaria confiable.

## Estructura

| Carpeta | Contenido |
|---|---|
| `domain/` | Modelos y máquina de estados de eventos (Kotlin puro) |
| `payment-engine/` | Motor de pagos: adaptadores por entidad, lectura de importes, clasificación, deduplicación, verificación, cola de anuncios, frases de voz y protocolo |
| `app/` | App Android (Kotlin + Compose): lector de notificaciones, voz TTS y diagnóstico |
| `firmware/` | Firmware ESP32-S3 (ESP-IDF): protocolo, estados de pantalla, NVS |
| `docs/` | Especificación, protocolo y registro de compatibilidad bancaria |

## Estado actual (fase 1–2)

- ✅ Motor de pagos con pruebas unitarias (`./gradlew test`).
- ✅ App Android base: lector de notificaciones, voz en español y pantalla de diagnóstico.
- ✅ Firmware base: protocolo de eventos, ACK, control de secuencia y estados de pantalla.
- ⏳ **Ninguna entidad está habilitada todavía.** Las reglas de Yape, Plin y bancos se escribirán
  a partir de notificaciones reales recopiladas con autorización (ver
  [docs/compatibilidad-bancaria.md](docs/compatibilidad-bancaria.md)).
- ⏳ Persistencia con Room, BLE GATT, audio por Wi-Fi y hardware.

## Compilar

```bash
# Motor y pruebas (no necesita Android SDK)
./gradlew test

# App Android: abrir el proyecto en Android Studio (crea local.properties con el SDK)
./gradlew :app:assembleDebug
```

Firmware: ver [firmware/README.md](firmware/README.md).
