# Fase 3 — Modelo central normalizado

Fecha: 13/11/2026 · Corrige H1 y H4 de la [Fase 1](fase-1-auditoria.md).

## Modelo `PaymentEvent` (módulo `domain`)

| Campo pedido | Campo | Canal A (Android) | Canal B (proveedor) |
|---|---|---|---|
| eventId | `eventId` | `evt_000123` (secuencia local) | asignado al recibirlo |
| sourceChannel | `sourceChannel` | `ANDROID_NOTIFICATION` | `OFFICIAL_WEBHOOK` |
| provider | `provider` | `null` | `MERCADO_PAGO`, `IZIPAY` (**obligatorio**) |
| sourceApplication | `sourceApplication` | paquete Android real | `null` |
| — (entidad) | `source` | Yape, Plin, BCP… | medio de pago que informe el proveedor |
| externalTransactionId | `externalTransactionId` | `null` | id del pago en el proveedor (**obligatorio**) |
| amount + currency | `money` (`BigDecimal` exacto + `PEN`/`USD`) | | |
| sender | `senderName` | solo si la notificación lo trae | solo si el proveedor lo informa |
| recipient | `recipient` | | |
| timestamp | `eventTimestamp` (+ `detectedTimestamp`) | | |
| eventType | `eventType` | | |
| verificationStatus | `verificationStatus` | ver abajo | |
| announcementStatus | `announcementStatus` | `PENDING, DELIVERED, ANNOUNCED, FAILED, SKIPPED` | |
| rawEventReference | `rawEventReference` | huella SHA-256 de la notificación (sin texto) | id del registro original en el backend |
| — | `duplicateOf` | evento que representa el mismo pago (solo si `DUPLICATE`) | |

Invariantes comprobadas al construir el objeto (lanzan excepción):
- `ANDROID_NOTIFICATION` nunca puede estar en `PROVIDER_CONFIRMED`.
- `OFFICIAL_WEBHOOK` exige `provider` y `externalTransactionId`.
- `duplicateOf` existe si y solo si el estado es `DUPLICATE`.

## Estados de verificación (separados del anuncio)

```
                  ┌──────────────► REJECTED
DETECTED ─┬─► INCOME_CANDIDATE ──► DUPLICATE (duplicateOf = evento oficial)
          │        │  ▲
          │        ▼  │
          ├─► PENDING_VERIFICATION ──► PROVIDER_CONFIRMED   (solo OFFICIAL_WEBHOOK)
          └────────────────────────► PROVIDER_CONFIRMED    (solo OFFICIAL_WEBHOOK)
```

- `INCOME_CANDIDATE` solo existe en Canal A; `PROVIDER_CONFIRMED` solo en Canal B
  (`VerificationStateMachine.canTransition(channel, from, to)`).
- `ANNOUNCED` es un valor de `announcementStatus`, no de verificación: un evento anunciado como posible
  ingreso sigue pudiendo pasar a `DUPLICATE` de una confirmación oficial (antes era terminal, H4).
- Clasificación de Android: ingreso con importe → `INCOME_CANDIDATE`; ingreso sin importe → `PENDING_VERIFICATION`;
  egreso, solicitud, publicidad, notificación antigua o futura → `REJECTED`.

## Verificación (`VerificationEngine.confirm`)

Solo confirma si **todo** se cumple (si no, lanza excepción y el evento no cambia):
1. el evento es `OFFICIAL_WEBHOOK`;
2. `ProviderConfirmation.provider` = `event.provider`;
3. mismo `externalTransactionId`;
4. mismo importe y moneda;
5. `signatureVerified` **o** `fetchedFromProviderApi` (la evidencia la arma el backend, Fase 4).

## Persistencia (Room v3)

- Tabla `payment_events` reconstruida en la migración 2→3 con los nuevos campos e índice **único**
  `(provider, externalTransactionId)` para idempotencia del Canal B (en SQLite los `NULL` de Android no chocan).
- Conversión de datos antiguos sin inventar confirmaciones: `RECEIVED/PARSED → DETECTED`,
  `INCOME_CANDIDATE/ANNOUNCED/DELIVERY_FAILED → INCOME_CANDIDATE`, `PENDING_REVIEW/VERIFIED → PENDING_VERIFICATION`,
  `REJECTED/DUPLICATE → REJECTED`; todos con `sourceChannel = ANDROID_NOTIFICATION`.
- El protocolo con el ESP32 no cambia todavía: se envía `VERIFIED` solo si `PROVIDER_CONFIRMED` (Fase 7).

## Interfaz

Historial y detalle muestran canal de detección, proveedor, app de origen, id del proveedor, destinatario y
«mismo pago que». El inicio separa «Posibles ingresos (notificaciones)» de «Confirmados por proveedor».

## Pruebas ejecutadas

| Prueba | Resultado real |
|---|---|
| `./gradlew test` (domain + payment-engine) | **12 clases, 65 pruebas, 0 fallos** |
| Nuevas: `VerificationStateMachineTest` (4), `VerificationAndProtocolTest` (7: confirmación válida; Android nunca se confirma; rechazo por proveedor, id, importe o falta de evidencia; estados finales; invariantes) | incluidas arriba |
| App: `AppLogicTest` (ida y vuelta entidad↔dominio, también para un evento oficial) y `MigrationSchemaTest` (2→3 crea `payment_events` e índices exactamente como Room) | Solo en CI |

## Limitaciones

- No hay aún correlación entre canales (Fase 6): el modelo ya la soporta con `DUPLICATE` + `duplicateOf`.
- Reembolsos o contracargos posteriores a `PROVIDER_CONFIRMED` no se modelan (estado final).
