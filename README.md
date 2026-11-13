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
| `payment-engine/` | Motor de pagos: adaptadores por entidad, importes, clasificación, deduplicación, verificación, cola de anuncios, frases y protocolo |
| `app/` | App Android (Kotlin, Compose, Room, TTS, BLE): lector de notificaciones, anuncios, historial, voz, equipo y diagnóstico |
| `firmware/` | Firmware ESP32-S3 (ESP-IDF, FreeRTOS, NimBLE, LVGL): BLE cifrado, pantalla, audio I2S, Wi-Fi local, OTA, batería y botones |
| `backend/` | Backend de integraciones oficiales (Canal B): webhooks firmados, idempotencia, auditoría, sandbox/producción; adaptador de Mercado Pago |
| `docs/` | Especificación, protocolo, hardware, manuales, pruebas y limitaciones |

## Documentación

| Documento | Contenido |
|---|---|
| [Auditoría](docs/auditoria.md) | Estado real del repositorio, errores encontrados y corregidos |
| [Arquitectura híbrida — Fase 1](docs/hibrido/fase-1-auditoria.md) | Auditoría frente al modelo Canal A (Android) + Canal B (webhooks oficiales) |
| [Proveedores del Canal B](docs/hibrido/proveedores-canal-b.md) | Qué permiten Mercado Pago, Izipay y bancos (fuentes oficiales) |
| [Especificación maestra](docs/especificacion-maestra.md) | Requisitos del producto |
| [Protocolo](docs/protocolo.md) | BLE GATT, mensajes, ACK, audio y OTA |
| [Hardware](docs/hardware.md) | Componentes, conexiones, botones y LED |
| [Manual de instalación](docs/manual-instalacion.md) | App, firmware y vinculación |
| [Manual de usuario](docs/manual-usuario.md) | Uso diario de la app |
| [Informe de pruebas](docs/informe-pruebas.md) | Pruebas automáticas y pendientes |
| [Compatibilidad bancaria](docs/compatibilidad-bancaria.md) | Estados por entidad y cómo activarlas con muestras reales |
| [Guía de pruebas físicas](docs/guia-pruebas-fisicas.md) | Protocolo en celulares y equipo reales |
| [Limitaciones y pendientes](docs/limitaciones.md) | Lo que el sistema aún no hace y problemas pendientes |
| [Actualización y recuperación](docs/actualizacion-recuperacion.md) | OTA, migraciones y solución de problemas |

## Estado actual

- ✅ Motor de pagos con pruebas unitarias.
- ✅ App Android completa: configuración inicial, inicio, historial, detalle, bancos, voz, equipo, diagnóstico,
  negocio y configuración; base de datos Room; anuncios en el celular y en el equipo con ACK y reintentos.
- ✅ Firmware del equipo: BLE con vinculación cifrada, pantalla, voz por Wi-Fi local, tono de respaldo, batería,
  botones, LED, OTA con reversión y watchdog.
- ✅ Compilación y pruebas automáticas en cada cambio (APK y firmware descargables desde GitHub Actions).
- ✅ Diagnóstico de notificaciones con anonimización, adaptadores independientes por entidad y registro de
  compatibilidad con 8 estados respaldados por evidencia.
- ⏳ **Ninguna entidad anuncia pagos todavía.** Las 7 entidades están en «Esperando muestras»: sus formatos se
  escriben con notificaciones reales anonimizadas (ver [compatibilidad bancaria](docs/compatibilidad-bancaria.md)).
- ⏳ Pruebas en celulares reales y en el prototipo físico (ver [guía de pruebas físicas](docs/guia-pruebas-fisicas.md)).

## Compilar

```bash
./gradlew test                 # dominio, motor y backend (no necesita Android SDK)
./gradlew :backend:run         # backend de webhooks (ver backend/README.md)
./gradlew :app:assembleDebug   # app Android (necesita Android SDK)
```

Firmware: ver [firmware/README.md](firmware/README.md).
