# Fase 6 — Unificación de eventos Android y webhooks

Fecha: 13/11/2026.

## Flujo

```
Proveedor ──webhook firmado──► Backend (fase 4/5) ──GET /api/v1/events (HTTPS + token)──► App PAGOBOX
                                                                                          │
Notificación Android ─────────────────────────────────────────────────────────────────► PaymentProcessor
                                                                                          │ (en serie, misma secuencia)
                                                                                          ▼
                                                                  CrossChannelCorrelator → Room → anuncios / ESP32
```

## Cambios

| Pieza | Archivo | Qué hace |
|---|---|---|
| Correlación | `payment-engine/.../correlation/CrossChannelCorrelator.kt` | Compara un pago confirmado con los posibles ingresos Android recientes |
| API para el celular | `backend/.../http/DeviceApi.kt`, `Server.kt` | `GET /api/v1/events?after=<cursor>`; solo lectura, HTTPS, token por celular (el servidor guarda solo su SHA-256) ligado a **un** entorno |
| Sincronización | `app/.../payment/OfficialEventsSync.kt` | Consulta el backend cada 20 s con cursor; solo HTTPS; estado (última comunicación, último error) para el panel |
| Ingreso al motor | `PaymentProcessor.ingestOfficial` | Guarda/actualiza por (proveedor, id externo) con la misma secuencia monotónica; correlaciona; avisa a la cola de anuncios |
| Modelo | `PaymentEvent.possibleMatchOf`, `testMode`; columnas nuevas en la migración 2→3 | Relación sin fusión y marca de sandbox |
| Anuncio | `PhraseSelector` | Un evento de sandbox se anuncia con «Prueba.» delante |
| Interfaz | `EventDetailScreen` | «Mismo pago que», «Posible mismo pago que», «Prueba (sandbox)» |

## Regla de duplicados entre canales

Candidato (todas): Android `INCOME_CANDIDATE` + oficial `PROVIDER_CONFIRMED`, misma entidad conocida, mismo importe
exacto y moneda, ≤ 3 min de diferencia, mismo entorno (prueba con prueba).

| Situación | Resultado |
|---|---|
| Un solo candidato **y** mismo remitente en ambos | Android → `DUPLICATE` (`duplicateOf` = oficial); si aún no se anunció, ya no se anuncia como posible ingreso |
| Remitente ausente en alguno | `possibleMatchOf` en ambos; **no** se fusiona |
| Dos o más candidatos iguales | `possibleMatchOf`; **no** se fusiona (ambiguo) |
| Remitente distinto, otro importe, otra entidad, fuera de la ventana, Android rechazado | Sin relación |
| Evento de sandbox vs pago real | Sin relación |

Importe + hora aproximada **nunca** bastan para fusionar.

## Pruebas ejecutadas

| Prueba | Resultado real |
|---|---|
| `CrossChannelCorrelatorTest` (5 casos de la tabla) | ✅ |
| `DeviceApiTest`: sin token → 401; token sandbox ve solo sandbox; token producción no ve sandbox; cursor | ✅ |
| `./gradlew test` total | domain 4 · payment-engine 66 · backend 19 — **89 pruebas, 0 fallos** |
| Compilación de la app y pruebas de Room con las columnas nuevas | CI del PR |
| Prueba extremo a extremo con un proveedor real | **No ejecutada** (sin credenciales ni servidor) |

## Limitaciones

- Sin push: el celular consulta cada 20 s mientras el proceso de la app está vivo. Con Doze, Android puede
  espaciar las consultas; un anuncio confirmado puede llegar con retraso. Push (FCM) requiere un proyecto
  Firebase del comercio.
- Hoy Mercado Pago no entrega el remitente en nuestro adaptador (privacidad + no documentado para Yape), así que
  con Mercado Pago la correlación quedará en «posible mismo pago», no en fusión automática. Es lo esperado por la
  regla.
- El token del celular se configura a mano (falta un flujo de registro de dispositivos).
- `network_security_config` sigue permitiendo HTTP para la red local del equipo; la app exige HTTPS para el
  backend en código (`SettingsRepository.setBackend`, `OfficialEventsSync`).
