# Informe de auditoría técnica — PAGOBOX

Fecha: 12/11/2026 · Revisión auditada: `d64d16a` · Alcance: app Android, motor de pagos, firmware ESP32-S3 y pruebas.

**Criterio:** solo se marca como «terminado» lo que está implementado y cubierto por una prueba automática o
una compilación verificada en CI. Nada de este informe se ha comprobado todavía en un celular físico ni en el
equipo armado.

## 1. Funciones terminadas (implementadas y verificadas por compilación y/o pruebas automáticas)

| Área | Estado | Evidencia |
|---|---|---|
| Arquitectura modular (`domain`, `payment-engine`, `app`, `firmware`) | Terminada | Compila en CI |
| Máquina de estados (no RECEIVED → VERIFIED) | Terminada | `EventStateMachineTest` |
| Lectura de importes en formatos peruanos | Terminada | `AmountParserTest` |
| Clasificación genérica (ingreso / egreso / solicitud / antiguo / sin importe) | Terminada | `PaymentPipelineTest` |
| Cola de anuncios (orden, sin repetición, reintento, silencio) | Terminada | `AnnouncementQueueTest` |
| Frases con variables y partes opcionales | Terminada | `VoiceTest`, `AppLogicTest` |
| Base de datos Room (9 tablas) | Compila | Generación KSP en CI; sin prueba de migración |
| Protocolo JSON + ACK + secuencia | Terminada | Pruebas Kotlin y pruebas del firmware en host |
| Firmware completo (BLE, pantalla, audio, Wi-Fi, OTA, batería, botones) | Compila | `idf.py build` en CI |

## 2. Funciones incompletas

| Función | Qué falta |
|---|---|
| Adaptadores bancarios | Existe un adaptador genérico configurado 7 veces, **sin paquete ni reglas**: ninguna entidad procesa notificaciones. No son adaptadores independientes ni tienen pruebas propias. |
| Identificación de paquetes oficiales | No hay mecanismo para registrar el paquete real de cada app sin modificar código. |
| Diagnóstico de notificaciones | No existe. Sin él no hay forma segura de recopilar formatos reales (bloquea la fase 1 del producto). |
| Registro de formatos desconocidos | Las notificaciones no reconocidas solo se registran como texto de log; no se guarda una muestra anonimizada. |
| Estados de compatibilidad | Solo 3 estados (`NOT_IMPLEMENTED`, `IN_TESTING`, `VERIFIED`) y sin evidencia de pruebas asociada. |
| Verificación oficial (`Official Payment Adapter`) | El motor la soporta, pero no hay ninguna integración: ningún pago llegará a VERIFIED. |
| Pruebas de integración | No hay pruebas de flujo completo ni de contrato entre el JSON de Android y el firmware. |
| Pruebas físicas | No hay protocolo ni registro de resultados por modelo de celular. |
| Autoprueba del equipo | No existe un comando para comprobar pantalla, audio, batería, Wi-Fi y motivo del último reinicio. |
| Prueba de entrega completa | No hay forma de medir celular → equipo → ACK → reproducción sin crear un pago. |

## 3. Errores encontrados

| # | Severidad | Error | Consecuencia | Corrección |
|---|---|---|---|---|
| E1 | **Crítica** | La secuencia de eventos se calcula como `MAX(sequence)+1` de la tabla. Al borrar historial (retención) o reinstalar la app vuelve a 1. | El equipo guarda la última secuencia en NVS y trata los nuevos eventos como repetidos: responde `PLAYED` **sin anunciar**, y la app los marca como anunciados. Además el `eventId` repetido viola el índice único y el pago se pierde. | Contador monotónico persistente que además nunca baja de la última secuencia confirmada por el equipo. |
| E2 | **Alta** | La deduplicación usa paquete + texto normalizado en una ventana de 30 min. | Dos pagos legítimos con texto idéntico (mismo remitente y monto) se descartan como duplicados. | Huella con clave de la notificación de Android y su marca de tiempo propia (`when`), además del texto. |
| E3 | Media | El lector ignora `EXTRA_TEXT_LINES` y `EXTRA_SUB_TEXT`. | Notificaciones con estilo «bandeja» pueden llegar sin el texto del pago. | Leer todos los campos de texto disponibles. |
| E4 | Media | Validación de ingresos depende solo del tipo que declara la regla. | Una regla mal escrita podría anunciar un egreso. | Validación independiente: si el texto contiene términos de egreso o solicitud, no se acepta como ingreso. |
| E5 | Media | `MainActivity` lee la base de datos con `runBlocking` en el hilo principal. | Riesgo de ANR en celulares lentos. | Lectura asíncrona con pantalla de carga mientras el splash está visible. |
| E6 | Media | El resumen «Hoy» fija el inicio del día al suscribirse. | Si la app queda abierta después de medianoche, mezcla días. | Recalcular el día con un temporizador. |
| E7 | Media | Firmware: el manejador de eventos Wi-Fi bloquea con `vTaskDelay` hasta 2 s. | Retrasa otros eventos del sistema. | Reintento con temporizador `esp_timer`. |
| E8 | Baja | BLE: `createBond()` y `requestMtu()` se lanzan a la vez al conectar. | En algunos celulares la segunda operación GATT falla y el enlace no llega a «listo». | Esperar a que termine la vinculación antes de negociar MTU. |
| E9 | Baja | `AmountParser` no acepta el prefijo `PEN`. | Formato bancario posible no reconocido. | Agregar `PEN` y `USD` como prefijos. |

## 4. Módulos simulados o sin implementación real

- **Ninguna lógica está simulada**, pero el sistema hoy **no anuncia pagos reales** porque no hay entidades configuradas.
- Las pruebas del motor usan un adaptador con formato **ficticio** (`TestFixtures`), explícitamente marcado como tal.
- La verificación oficial no tiene proveedor real.
- El equipo no tiene paquete de voces pregrabadas (sin Wi-Fi usa un tono).

## 5. Dependencias faltantes

| Dependencia | Para qué | Estado |
|---|---|---|
| Muestras reales de notificaciones por entidad | Escribir reglas | Faltan (se recopilarán con el diagnóstico de la fase 2) |
| Nombres de paquete oficiales | Identificar entidades | No se asumen; se registran desde el diagnóstico |
| Celulares de prueba (varias marcas y versiones Android) | Fase 6 | Faltan |
| Prototipo físico armado | Fase 7 | Falta |
| Emulador o dispositivo en CI | Pruebas instrumentadas (Room, servicio) | No configurado |

## 6. Riesgos técnicos

1. **Cambios de formato** de las apps bancarias sin aviso: se mitiga con registro de formatos desconocidos y el estado `FORMAT_CHANGED`.
2. **Fabricantes que cierran apps** en segundo plano (Xiaomi, Huawei, Oppo, Samsung): requiere pruebas por modelo.
3. **Notificaciones falsificadas** por otra app con acceso a notificaciones: una notificación nunca es confirmación bancaria.
4. **BLE + Wi-Fi simultáneos** en ESP32-S3 con audio: coexistencia por validar en el prototipo.
5. **Privacidad:** el diagnóstico de notificaciones manipula datos personales; debe anonimizar y no persistir texto crudo.
6. **Migraciones de Room** sin prueba instrumentada.

## 7. Plan de corrección (orden de ejecución)

| Fase | Cambio | Archivos principales |
|---|---|---|
| 1 | Corregir E1–E9 | `PaymentRepository`, `DeduplicationEngine`, `PaymentNotificationListener`, `PaymentClassifier`, `AmountParser`, `MainActivity`, `BleDeviceLink`, `wifi_audio.c` |
| 2 | Diagnóstico de notificaciones con anonimizador, filtro de códigos de seguridad, muestras borrables y exportación local | `diagnostics/`, `NotificationAnonymizer`, nueva tabla `notification_samples` |
| 3 | Un adaptador por entidad (`YapeAdapter`, `PlinAdapter`, …) con formatos versionados, validación de egresos y registro de desconocidos; paquetes asignables desde el diagnóstico | `payment-engine/source/banks/`, tabla `package_assignments` |
| 4 | Suite de notificaciones sintéticas (10 casos) y pruebas de integración + contrato de protocolo compartido con el firmware | `payment-engine/src/test`, `protocol-fixtures/` |
| 5 | Registro de compatibilidad con 8 estados y evidencia obligatoria para `VERIFIED_COMPATIBLE` | `CompatibilityRegistry`, `docs/compatibilidad-bancaria.md` |
| 6 | Protocolo de pruebas en Android real y registro de resultados en la app (modelo, versión, apps bancarias) | `docs/guia-pruebas-fisicas.md`, pantalla «Pruebas físicas» |
| 7 | Autoprueba del equipo, motivo de reinicio, prueba de entrega completa con latencias | firmware `SELF_TEST`/`TEST_EVENT`, `DeviceManager` |
| 8 | Compilación en CI, documentación y lista de pendientes | `docs/` |

## 8. Estado de las correcciones (13/11/2026)

| # | Estado | Dónde | Cómo se comprobó |
|---|---|---|---|
| E1 | Corregido | `SettingsRepository.peekNextSequence/commitSequence`, `PaymentProcessor`, `DeviceManager.lastAckedSequence` | `PaymentPipelineTest` (secuencia solo al crear eventos); en celular: pendiente (P4) |
| E2 | Corregido | `DeduplicationEngine` | `SyntheticNotificationSuiteTest`: dos pagos iguales legítimos se anuncian; repetición del sistema no |
| E3 | Corregido | `PaymentNotificationListener`, `RawNotification` | Pruebas del motor con `textLines`/`subText`; en celular: AND-02 |
| E4 | Corregido | `PaymentClassifier` (términos de egreso y solicitud) | Caso «regla mal escrita» de la suite sintética |
| E5 | Corregido | `MainActivity` (splash hasta leer la configuración, sin `runBlocking`) | Compilación en CI |
| E6 | Corregido | `PaymentRepository.observeToday` (cambia de día a medianoche) | Compilación en CI |
| E7 | Corregido | `wifi_audio.c` (reintento con `esp_timer`) | Compilación del firmware en CI |
| E8 | Corregido | `BleDeviceLink` (MTU tras `BOND_BONDED`, aviso si la clave se rechaza) | Compilación en CI; en equipo: BLE-01 |
| E9 | Corregido | `AmountParser` (PEN, USD) | `AmountParserTest` |

Funciones incompletas atendidas: adaptadores independientes por entidad (sin formatos inventados), diagnóstico de
notificaciones con anonimizador, asignación de paquetes con huella de firma, muestras de formatos desconocidos,
8 estados de compatibilidad con evidencia obligatoria, pruebas sintéticas e integración con contrato compartido,
protocolo y registro de pruebas físicas, autoprueba y prueba de extremo a extremo del equipo.

Sigue pendiente (ver [limitaciones.md](limitaciones.md#problemas-pendientes)): muestras reales por entidad,
pruebas en celulares y prototipo físicos, y verificación oficial de pagos.
