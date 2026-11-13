# Fase 2 — Captura automática Android (Canal A)

Fecha: 13/11/2026 · Base: hallazgos H2, H5 y H7 de la [Fase 1](fase-1-auditoria.md).

## Cambios

| Hallazgo | Cambio | Archivos |
|---|---|---|
| H5 — apps financieras desconocidas solo se registraban en modo diagnóstico | El lector revisa **en memoria** cada notificación de apps no asociadas. Si el texto trae un importe con moneda (`S/`, `PEN`, `US$`, `USD`), registra **solo la app**: paquete, nombre, versión, instalador, primera y última vez y contador. Nunca guarda el texto. Nueva sección «Posibles apps financieras sin asignar» con «Asignar a entidad» y «No es financiera». Al asignarla, sale de la lista | `MonetarySignal.kt` (motor), `PaymentNotificationListener`, `PaymentProcessor`, `UnknownFinancialAppEntity/Dao`, migración Room 2→3, `NotificationDiagnosticsScreen` |
| H2 — frases de eventos detectados afirmaban el pago | Frases por defecto: «Se detectó un posible ingreso de {monto} por {entidad}» (detectado) y «¡Pago confirmado de {monto}!» (solo con verificación oficial). Una prueba garantiza que nunca coinciden | `PhraseTemplateRenderer.DefaultPhrases`, `VoiceSettingsScreen`, `especificacion-maestra.md` |
| H7 — pantalla apagada | `WakeLock` parcial con tiempo máximo: 10 s al procesar notificaciones de apps de una entidad y 120 s durante cada anuncio (síntesis, entrega al equipo con ACK y voz). Permiso `WAKE_LOCK`. No enciende la pantalla | `system/ShortWakeLock.kt`, `PaymentProcessor`, `AnnouncementCoordinator`, `AndroidManifest.xml` |
| — | Caché de `PackageInspector` segura entre hilos (la usan el lector y el procesador) | `PackageInspector.kt` |
| — | Nuevas pruebas físicas AND-14 (app no asignada) y AND-15 (pantalla apagada con equipo desconectado) | `PhysicalTestCatalog.kt`, `guia-pruebas-fisicas.md` |

Lo que ya cumplía y no se tocó: `packageName` real, título/texto/líneas/subtexto/`when`/clave, adaptadores
independientes sin formatos inventados, procesamiento sin abrir la app (arranca con el servicio) y registro aunque
el ESP32 esté desconectado (Room es la fuente de verdad).

## Pruebas ejecutadas

| Prueba | Resultado real |
|---|---|
| `./gradlew test` (domain + payment-engine, JDK 21) | **12 clases, 59 pruebas, 0 fallos** (antes 55: +3 `MonetarySignalTest`, +1 frase detectada ≠ confirmada) |
| Pruebas de la app (`AppLogicTest`, `MigrationSchemaTest` con la nueva migración 2→3) y compilación del APK | Solo en CI (no hay Android SDK en este entorno): ver el estado del PR |
| Pruebas físicas AND-01…AND-15 | **No ejecutadas** (requieren celulares reales) |

## Limitaciones pendientes

- **Privacidad:** el lector ahora mira (en memoria) el texto de todas las apps para detectar importes. No guarda
  ni envía ese texto, pero conviene explicarlo en la configuración inicial y en la política de privacidad.
- La señal de importe es genérica: una app de mensajería con «te mando S/ 20» también aparecerá en la lista. El
  usuario la descarta con «No es financiera».
- El `WakeLock` no garantiza nada frente a fabricantes que matan el proceso (Xiaomi, Huawei, Oppo, Samsung): se
  valida con AND-03, AND-04, AND-06 y AND-15.
- No se agregó un servicio en primer plano: `NotificationListenerService` ya lo mantiene enlazado el sistema; se
  decidirá con datos de las pruebas físicas.
- Yape, Plin y bancos siguen sin formatos: **ninguna entidad anuncia pagos reales** hasta tener muestras.
