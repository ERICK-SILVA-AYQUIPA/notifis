# Fase 7 — Equipo ESP32, voz por confianza y panel de integraciones

Fecha: 13/11/2026.

## Protocolo v2 con el equipo PAGOBOX

El celular sigue siendo el único que decide; el ESP32 solo recibe eventos ya procesados.

| Campo | Antes (v1) | Ahora (v2) |
|---|---|---|
| `verificationStatus` | `DETECTED` / `VERIFIED` (traducido) | estado real: `INCOME_CANDIDATE`, `PENDING_VERIFICATION`, `PROVIDER_CONFIRMED`… |
| `channel` | — | `ANDROID_NOTIFICATION` / `OFFICIAL_WEBHOOK` |
| `provider` | — | `MERCADO_PAGO`, `IZIPAY` o `null` |
| `test` | — | booleano JSON; `true` para eventos de sandbox |

Firmware (`firmware/main/protocol.c`): solo `PROVIDER_CONFIRMED` (o `VERIFIED`, compatibilidad v1) cuenta como
confirmado; cualquier otro valor, en minúsculas o desconocido, se muestra como posible ingreso. `test` solo se
activa con el booleano `true`, no con la cadena `"true"`. Nueva función `pb_verification_label`:

| Evento | Pantalla del equipo |
|---|---|
| Notificación Android | «Posible ingreso (no verificado)» |
| Confirmado por proveedor | «Confirmado por proveedor» |
| Sandbox | «PRUEBA - …» delante de cualquiera de los dos |

Sin cambios (ya cubiertos y probados antes): ACK `RECEIVED`/`PLAYED`, volumen, estado de conexión, reconexión,
cola persistente en el celular (si el equipo se desconecta, el evento queda `PENDING` y se entrega al
reconectar) y descarte de secuencias ya anunciadas en el equipo.

Contrato compartido: `protocol-fixtures/payment_event.json` (posible ingreso Android) y el nuevo
`payment_event_confirmed.json` (confirmado por proveedor, sandbox). Kotlin los genera y compara byte a byte;
el firmware los interpreta en sus pruebas de host.

## Voz según confianza

- Posible ingreso (Android): «Se detectó un posible ingreso de 25 soles por Yape».
- Confirmado por proveedor: «¡Pago confirmado de 25 soles!».
- Sandbox: se antepone «Prueba.».
- Nuevo ajuste **«Anunciar posibles ingresos detectados por notificación»** (Ajustes, activado por defecto).
  Apagado: solo se anuncian los pagos con `PROVIDER_CONFIRMED`; los demás quedan en el historial como
  «Omitido» (`SKIPPED`), sin sonido ni envío al equipo.

## Panel de integraciones (Ajustes → Integraciones oficiales)

- **Backend:** `GET /api/v1/integrations` (HTTPS y token del celular, como `/api/v1/events`). Por proveedor
  devuelve si está implementado, si hay credenciales (sí/no, **nunca** sus valores), la ruta del webhook,
  última comunicación, último evento comprobado, último error y un estado:

  | Estado | Cuándo |
  |---|---|
  | `NOT_IMPLEMENTED` | No hay adaptador (Izipay hoy) |
  | `NOT_CONFIGURED` | Faltan credenciales en el servidor para ese entorno |
  | `AWAITING_FIRST_EVENT` | Credenciales presentes, ningún webhook procesado |
  | `ACTIVE` | Se procesó un webhook con firma válida o pago consultado en la API, sin error posterior |
  | `ERROR` | El último error es posterior al último evento comprobado |

- **App:** pantalla con el servidor de integraciones (URL HTTPS + token, «Guardar y probar», «Desconectar»)
  y una tarjeta por proveedor con métodos de cobro, compatibilidad (qué **no** cubre), afiliación,
  credenciales, sandbox/producción, webhook, última comunicación, último evento, último error.
  La información descriptiva sale de `proveedores-canal-b.md`; lo no verificado se dice como tal.
- «Activa» solo aparece si el backend responde `ACTIVE`. Sin servidor, sin respuesta o con otro estado, la
  tarjeta dice «No conectada», «Sin datos», «Esperando primer webhook», «Con errores» o «No disponible».

## Archivos modificados

- `payment-engine`: `protocol/DeviceMessages.kt` (v2, booleanos JSON reales),
  `integrations/IntegrationCatalog.kt` (catálogo y `IntegrationPanel.summarize`); pruebas
  `IntegrationPanelTest`, `EndToEndIntegrationTest` (contrato confirmado/sandbox), `VerificationAndProtocolTest`.
- `firmware`: `main/protocol.h`, `main/protocol.c`, `main/display/display.c`, `test_host/test_main.c`.
- `protocol-fixtures`: `payment_event.json`, `payment_event_confirmed.json` (nuevo).
- `backend`: `http/DeviceApi.kt` (`integrationsJson`, `integrationStatus`), `http/Server.kt`
  (`/api/v1/integrations`, autorización común); `DeviceApiTest` (+2).
- `app`: `AppSettings.kt` (`announceCandidates`), `AnnouncementCoordinator.kt`, `SettingsScreen.kt`,
  `OfficialEventsSync.kt` (lee `/api/v1/integrations`), `ui/integrations/IntegrationsScreen.kt` (nuevo),
  `PagoboxApp.kt` (ruta).
- `docs/protocolo.md`.

## Pruebas ejecutadas

| Prueba | Resultado real |
|---|---|
| `./gradlew :domain:test :payment-engine:test` | domain 4, payment-engine **70** pruebas, 0 fallos |
| `./gradlew :backend:test` | **21** pruebas, 0 fallos (nuevas: panel sin «activo» antes de un webhook real, error tras firma inválida, nunca devuelve credenciales; tabla de estados) |
| Firmware en host (`cmake` + `pagobox_host_tests`) | **101 comprobaciones correctas, 0 fallidas** (nuevas: valores de `verificationStatus`, `test` booleano, contrato confirmado/sandbox) |
| App Android y compilación del firmware ESP32-S3 | En CI (no hay SDK de Android ni ESP-IDF completo en este entorno) |

## Limitaciones

- El firmware se compila para ESP32-S3 en CI (ESP-IDF); aquí solo se ejecutan las pruebas de host. No se probó
  en un equipo físico.
- El panel muestra el estado que el backend conoce; no hay backend desplegado ni credenciales reales, así que
  hoy todo proveedor aparece como «No conectada».
- El celular consulta el backend cada 20 s mientras la app vive; sin push, la confirmación oficial puede tardar
  ese intervalo.
