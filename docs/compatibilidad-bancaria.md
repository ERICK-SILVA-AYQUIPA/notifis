# Registro de compatibilidad bancaria

Cada entidad tiene su propio adaptador (`payment-engine/.../source/banks/`) y su propio estado. **Ninguna
entidad se activa artificialmente**: el estado se respalda con muestras reales y evidencia documentada, y
`CompatibilityRegistryTest` hace fallar la compilación en CI si un estado declarado no tiene respaldo.

> Una notificación **no** es una confirmación bancaria. PAGOBOX anuncia «pago detectado», nunca «pago verificado»,
> y no usa capturas de pantalla ni OCR como sustituto de una confirmación.

## Estados

| Estado | Etiqueta en la app | ¿Anuncia pagos? | Requisito para llegar aquí |
|---|---|---|---|
| `NOT_CONFIGURED` | Sin configurar | No | Estado inicial: no se conoce el paquete de la app en este celular |
| `WAITING_FOR_SAMPLE` | Esperando muestras | No | Paquete identificado (en código o asignado desde el diagnóstico con su firma). Se guardan muestras anonimizadas |
| `CONFIGURED` | Configurado | Sí | Al menos un formato escrito a partir de una muestra **real** anonimizada (no sintética) |
| `LAB_TESTED` | Probado en laboratorio | Sí | Lo anterior + evidencia `LAB` aprobada (pruebas unitarias con las muestras reales) |
| `DEVICE_TESTED` | Probado en celular | Sí | Lo anterior + evidencia `DEVICE` aprobada en un celular identificado (modelo, Android, versión de la app bancaria) |
| `VERIFIED_COMPATIBLE` | Compatible verificado | Sí | Lo anterior + evidencia `FULL_PROTOCOL`: protocolo completo de [guia-pruebas-fisicas.md](guia-pruebas-fisicas.md) |
| `FORMAT_CHANGED` | Formato cambiado | Solo lo reconocido | Automático: 3 formatos desconocidos seguidos en una entidad que procesa pagos. Se reinicia al revisar |
| `TEMPORARILY_UNAVAILABLE` | No disponible temporalmente | No | Pausa manual del usuario o por incidencia |

## Registro por entidad (estado al 13/11/2026)

| Entidad | Adaptador | Paquete oficial confirmado | Formatos reales | Estado declarado | Evidencia |
|---|---|---|---|---|---|
| Yape | `YapeAdapter` | Pendiente de muestra | 0 | `WAITING_FOR_SAMPLE` | — |
| Plin | `PlinAdapter` | Pendiente (Plin funciona dentro de apps bancarias; puede llegar desde varios paquetes) | 0 | `WAITING_FOR_SAMPLE` | — |
| BCP | `BcpAdapter` | Pendiente de muestra | 0 | `WAITING_FOR_SAMPLE` | — |
| Interbank | `InterbankAdapter` | Pendiente de muestra | 0 | `WAITING_FOR_SAMPLE` | — |
| BBVA | `BbvaAdapter` | Pendiente de muestra | 0 | `WAITING_FOR_SAMPLE` | — |
| Scotiabank | `ScotiabankAdapter` | Pendiente de muestra | 0 | `WAITING_FOR_SAMPLE` | — |
| Banco de la Nación | `BancoNacionAdapter` | Pendiente de muestra | 0 | `WAITING_FOR_SAMPLE` | — |

En un celular, una entidad sin paquete conocido aparece como `NOT_CONFIGURED`; al asignar su app desde
**Ajustes → Diagnóstico de notificaciones**, pasa a `WAITING_FOR_SAMPLE`. **Hoy ninguna entidad anuncia pagos
reales**: no se inventaron formatos ni nombres de paquete sin evidencia.

## Cómo activar una entidad (paso a paso)

1. **Identificar la app.** En el celular del comercio, activar el modo diagnóstico (15 min), pedir un pago real
   pequeño y tocar «Asignar a entidad» en la captura. La app guarda el paquete, la versión, el instalador
   (Google Play u otro) y la huella SHA-256 del certificado de firma. Si la firma cambia después, el paquete
   se bloquea hasta reasignarlo.
2. **Recopilar muestras.** Con la entidad en `WAITING_FOR_SAMPLE`, cada notificación de esa app se guarda
   anonimizada (importes → 9, otros dígitos → #, nombres → `<NOMBRE>`, códigos de seguridad omitidos).
   Reunir al menos: pago recibido, pago enviado, solicitud de pago, publicidad y, si existe, ingreso en dólares.
3. **Exportar** el registro anonimizado y revisarlo a mano antes de compartirlo.
4. **Escribir los formatos** en el adaptador (`NotificationFormat`) con `provenance` (modelo, Android, versión de
   la app, fecha), `anonymizedSample` y `expectedAmount`, y agregar el paquete en `PACKAGES` si se confirmó
   que es el oficial. `BankAdaptersTest` prueba cada formato con su muestra.
5. Cambiar `STATUS` a `CONFIGURED`. Para avanzar a `LAB_TESTED` / `DEVICE_TESTED` / `VERIFIED_COMPATIBLE`,
   registrar la evidencia en `CompatibilityRegistry.evidence` con la referencia al informe en `docs/pruebas/`.
6. Actualizar esta tabla con modelo, versión de Android y versión de la app bancaria.

## Qué pasa cuando la app bancaria cambia su formato

- Una notificación de la entidad que no coincide con ningún formato **no se anuncia**: se guarda como
  «Formato desconocido» (anonimizada) y se cuenta.
- Con 3 seguidas, la entidad pasa a `FORMAT_CHANGED`, se registra un error en Diagnóstico y la pantalla Bancos
  lo muestra en rojo. Solo se siguen anunciando las notificaciones con formato conocido.
- Tras revisar las muestras (y actualizar la app si hizo falta), el usuario toca «Ya revisé el formato».
