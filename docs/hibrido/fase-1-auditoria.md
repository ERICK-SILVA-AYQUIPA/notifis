# Fase 1 — Auditoría para la arquitectura híbrida

Fecha: 13/11/2026 · Revisión auditada: `32fea91` (rama `main`) · Alcance: todo el repositorio, contrastado con
los 10 puntos de la decisión de arquitectura híbrida (Canal A Android + Canal B integraciones oficiales).

**Criterio:** «Implementado» significa que existe código y está cubierto por una prueba automática o una
compilación en CI. Nada se ha comprobado aún en un celular físico ni en el equipo armado.

## 1. Resumen

PAGOBOX ya tiene un **Canal A** sólido a nivel de código (lector de notificaciones, adaptadores por entidad sin
formatos inventados, diagnóstico anonimizado, deduplicación, cola de anuncios, ESP32 con ACK y secuencia), pero
**no existe nada del Canal B**: no hay backend, ni endpoints, ni proveedores, ni verificación de firmas, ni
panel de integraciones. El modelo de eventos tampoco distingue canal ni proveedor, y los estados no coinciden
con los que pide la arquitectura híbrida.

Hoy el sistema **no anuncia ningún pago real**: las 7 entidades están en «Esperando muestras» porque no hay
notificaciones reales anonimizadas (correcto: no se inventaron formatos).

## 2. Pruebas ejecutadas en esta auditoría

| Prueba | Comando | Resultado real |
|---|---|---|
| Motor + dominio (JVM) | `./gradlew test` | Ver §2.1 |
| Firmware en host (protocolo, pantalla, WAV, batería) | `cmake` + `pagobox_host_tests` (cJSON v1.7.18 descargado aparte, igual que en ESP-IDF) | **65 comprobaciones correctas, 0 fallidas** |
| App Android + APK | CI de GitHub (`ci.yml`), no hay Android SDK en este entorno | Última ejecución en `main` (`32fea91`): **success** |
| Firmware ESP-IDF | CI (`idf.py build`) | Mismo run: **success** |

### 2.1 Pruebas JVM

`./gradlew test` en este entorno (JDK 21, sin Android SDK, por lo que solo se incluyen `:domain` y
`:payment-engine`): **11 clases, 55 pruebas, 0 fallos, 0 errores**. Los dos primeros intentos fallaron antes de
compilar porque Maven Central respondió `429 Too Many Requests` al descargar dependencias; el tercero pasó.
Las 6 pruebas de `:app` (`AppLogicTest`, `MigrationSchemaTest`) solo corren en CI.

## 3. Estado real por punto de la arquitectura

Leyenda: ✅ implementado · 🟡 parcial · ❌ no existe

### 3.1 Canal A — Android

| Requisito | Estado | Evidencia / comentario |
|---|---|---|
| `NotificationListenerService` como captura | ✅ | `notification_listener/PaymentNotificationListener.kt` |
| `packageName` real | ✅ | `sbn.packageName`; además se guarda versión, instalador y huella SHA-256 de firma al asignar (`PackageInspector`) |
| Título, texto, timestamp y datos disponibles | ✅ | `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES`, `EXTRA_SUB_TEXT`, `postTime`, `when`, `key`, categoría, nombres de extras |
| Reconocer apps conocidas | 🟡 | Mecanismo listo (`SourcePackageRegistry`), pero **ningún paquete está declarado en código** (no hay evidencia); se asignan desde el diagnóstico |
| Registrar apps desconocidas | 🟡 | Solo durante el **modo diagnóstico** (15 min, manual). Fuera de él, una app financiera nueva se ignora sin dejar rastro. Falta un registro permanente y mínimo (paquete + etiqueta + contador, sin texto) |
| Procesar sin abrir PAGOBOX | ✅ (código) | `PagoboxApplication.onCreate` arranca el contenedor cuando Android enlaza el servicio; no requiere la `Activity`. Pendiente prueba física (AND-xx) |
| Registrar aunque el ESP32 esté desconectado | ✅ | El evento se guarda en Room antes de anunciar; la cola reintenta al reconectar |
| Pantalla apagada | 🟡 | Sin servicio en primer plano ni `WakeLock` durante el anuncio; la exención de batería se pide. Sujeto a pruebas por fabricante |
| Adaptadores independientes por app | ✅ | `source/banks/*Adapter.kt` (7), sin formatos ni paquetes inventados |

### 3.2 Canal B — Integraciones oficiales

| Requisito | Estado | Comentario |
|---|---|---|
| Arquitectura de proveedores | ❌ | Solo existe `VerificationEngine` + `OfficialConfirmation(providerId, externalReference)`, sin proveedores |
| Investigación documentada de Izipay / Mercado Pago / bancos | ❌ → hecho en esta fase | Ver [proveedores-canal-b.md](proveedores-canal-b.md) |
| Integración real | ❌ | Ninguna (correcto: no hay credenciales ni contrato) |

### 3.3 Backend de integraciones

| Requisito | Estado |
|---|---|
| Endpoints HTTPS de webhooks | ❌ No hay módulo de backend |
| Firma criptográfica, validación de proveedor, idempotencia, anti-repetición | ❌ |
| Registro de eventos originales, reintentos, auditoría | ❌ |
| Separación sandbox / producción | ❌ |
| Credenciales solo en backend | ✅ por omisión: la app no contiene credenciales. ⚠️ `network_security_config` permite HTTP en claro **para todos los destinos** (pensado para la red local del equipo); cuando la app hable con el backend habrá que restringirlo |

### 3.4 Motor central y modelo normalizado

`domain/model/PaymentEvent.kt` tiene: `eventId`, `sequence`, `source` (enum de 7 entidades), `eventType`,
`money` (BigDecimal + moneda), `senderName`, `eventTimestamp`, `detectedTimestamp`, `state`,
`verificationStatus`, `announcementStatus`, `rawNotificationHash`, `deviceId`.

| Campo pedido | Estado |
|---|---|
| `eventId`, `amount`, `currency`, `sender`, `timestamp`, `eventType`, `verificationStatus`, `announcementStatus` | ✅ |
| `sourceChannel` (ANDROID_NOTIFICATION / OFFICIAL_WEBHOOK) | ❌ |
| `provider` (Izipay, Mercado Pago…) | ❌ |
| `sourceApplication` (paquete Android) | ❌ (solo `SourceId` de la entidad) |
| `externalTransactionId` | ❌ |
| `recipient` | ❌ |
| `rawEventReference` | 🟡 `rawNotificationHash` (solo Android) |
| Duplicados **entre canales** | ❌ La deduplicación actual es solo intra-Android (huella de la notificación) |

### 3.5 Verificación y estados

Estados actuales (`EventState`): `RECEIVED, PARSED, INCOME_CANDIDATE, VERIFIED, ANNOUNCED, DUPLICATE, REJECTED,
PENDING_REVIEW, DELIVERY_FAILED`. Verificación: `DETECTED | VERIFIED`.

| Estado pedido | Equivalente actual |
|---|---|
| `DETECTED` | `RECEIVED`/`PARSED` (y `VerificationStatus.DETECTED`) |
| `INCOME_CANDIDATE` | ✅ igual |
| `PROVIDER_CONFIRMED` | `VERIFIED` (pero sin proveedor ni canal que lo respalde) |
| `PENDING_VERIFICATION` | ❌ (`PENDING_REVIEW` significa «sin importe», no «esperando proveedor») |
| `REJECTED`, `DUPLICATE`, `ANNOUNCED` | ✅ |

Lo bueno: la máquina de estados ya impide `RECEIVED → VERIFIED` y solo `VerificationEngine` verifica.
El problema: `VerificationEngine.verify()` **no comprueba el canal ni la firma**: cualquier código que construya
un `OfficialConfirmation` con una referencia no vacía verifica un evento Android. En el modelo híbrido, la
confirmación debe nacer exclusivamente de un webhook con firma válida (o de una consulta autenticada al
proveedor) procesado en el backend.

Además, el estado de anuncio y el de verificación están mezclados en `EventState` (`ANNOUNCED` es terminal):
un evento anunciado como «detectado» no puede pasar después a confirmado. Hay que separar las dos dimensiones.

### 3.6 Historial y anuncios

| Requisito | Estado |
|---|---|
| Importe, fecha, hora, entidad, remitente, estado de verificación y de anuncio | ✅ (`HistoryScreen`, `EventDetailScreen`) |
| Canal de detección / proveedor | ❌ (no existe el dato) |
| Voz por tipo de evento y nivel de confianza | 🟡 Plantillas separadas `forVerified` true/false |
| Mensaje distinto para detectado y confirmado | 🟡 Sí son distintos, **pero las frases por defecto de un evento solo detectado afirman el pago**: «¡Te yapearon S/ 25!», «¡Recibiste … por Plin!». Contradice el principio del propio proyecto y el pedido («Se detectó un posible ingreso…») |

### 3.7 Dispositivo físico (ESP32-S3)

| Requisito | Estado |
|---|---|
| No detecta operaciones bancarias | ✅ Solo recibe `PAYMENT_EVENT` por BLE |
| Pantalla, audio, volumen | ✅ LVGL, I2S, `SET_VOLUME` (compila; sin prueba física) |
| Estado de conexión, ACK técnico, reconexión | ✅ ACK `RECEIVED`/`PLAYED`, reconexión BLE |
| Prevención de anuncios duplicados | ✅ Secuencia monotónica en NVS (`pb_is_new_sequence`) + secuencia persistente en la app (E1 corregido) |
| Diferencia detectado / confirmado en pantalla | ✅ «Detectado (no verificado)» / «Verificado» |
| Desconexión sin pérdida | ✅ Room es la fuente de verdad. ⚠️ Un anuncio con más de 30 min de atraso se marca «no entregado» (decisión de diseño, no pérdida del registro) |

### 3.8 Panel de integraciones

❌ No existe. Hay una pantalla «Bancos» (Canal A) con estados de compatibilidad, pero nada para proveedores
(afiliación, credenciales, entorno, webhook, última comunicación, errores).

### 3.9 Seguridad y privacidad

| Requisito | Estado |
|---|---|
| No pide claves bancarias, PIN ni OTP | ✅ |
| No extrae credenciales ni hace scraping | ✅ |
| Datos mínimos | ✅ No guarda texto crudo; muestras anonimizadas; códigos de seguridad omitidos |
| `allowBackup=false` | ✅ |
| Firma de paquetes asignados | ✅ Si cambia la firma, se deja de leer la app |

## 4. Errores y riesgos nuevos detectados

| # | Severidad | Hallazgo | Fase que lo corrige |
|---|---|---|---|
| H1 | Alta | `VerificationEngine` verifica sin comprobar canal, proveedor ni firma | 3 y 6 |
| H2 | Alta | Frases por defecto de eventos **no verificados** afirman el pago («¡Te yapearon…!») | 2 (texto) y 7 |
| H3 | Alta | No hay backend: el Canal B es inexistente | 4 |
| H4 | Media | `ANNOUNCED` es terminal y mezcla anuncio con verificación: un evento ya anunciado no puede confirmarse | 3 |
| H5 | Media | Apps financieras desconocidas solo se registran en modo diagnóstico | 2 |
| H6 | Media | `cleartextTrafficPermitted="true"` global; al añadir el backend debe limitarse a IPs locales y exigir HTTPS al servidor | 6 |
| H7 | Media | Sin servicio en primer plano ni `WakeLock` breve durante el anuncio: con pantalla apagada, Doze puede retrasar la cola | 2 (sujeto a prueba física) |
| H8 | Baja | `firmware/test_host` depende de `IDF_PATH` incluso para cJSON: no se puede ejecutar sin ESP-IDF | 8 |
| H9 | Baja | Las pruebas JVM descargan dependencias de Maven Central en cada entorno nuevo (en esta auditoría Maven devolvió `429 Too Many Requests` en los primeros intentos) | — (entorno) |

## 5. Hallazgo clave del Canal B (resumen de [proveedores-canal-b.md](proveedores-canal-b.md))

Ningún proveedor investigado permite **consultar todos los pagos Yape/Plin recibidos** en la cuenta personal o
de negocio del comerciante. Mercado Pago (Yape) e Izipay (Yape, Plin, QR) solo notifican y confirman los
**cobros creados a través de su propia pasarela** (checkout, link de pago o su QR). Consecuencia de diseño:

- `PROVIDER_CONFIRMED` solo será posible para pagos que el cliente haga **mediante el canal del proveedor**
  (por ejemplo, el QR o link de Izipay / Mercado Pago del comercio).
- Un yapeo directo al número del comerciante seguirá siendo solo Canal A (`INCOME_CANDIDATE`), sin confirmación.
- La fusión entre canales solo es segura cuando ambos eventos comparten evidencia fuerte (mismo proveedor e
  identificador, o importe exacto + ventana corta + una sola coincidencia + mismo medio). Nunca solo importe y hora.

## 6. Plan de las fases siguientes (sin reconstruir)

| Fase | Cambio principal | Archivos |
|---|---|---|
| 2 | Registro permanente mínimo de apps financieras desconocidas; frases de «posible ingreso»; `WakeLock` breve y reconexión del lector; pruebas | `PaymentNotificationListener`, `PaymentProcessor`, `PhraseTemplateRenderer`, nueva tabla |
| 3 | Modelo normalizado (`sourceChannel`, `provider`, `sourceApplication`, `externalTransactionId`, `recipient`, `rawEventReference`); estados híbridos; anuncio separado de verificación; migración Room | `domain`, `payment-engine`, `app/data` |
| 4 | Módulo `backend/` (Kotlin + Ktor, reutiliza `domain`): webhooks HTTPS, firma, idempotencia, anti-repetición, eventos originales, auditoría, sandbox/producción, secretos por variables de entorno | nuevo `backend/` |
| 5 | Primera integración real en modo prueba: **Mercado Pago (Yape vía Checkout API)**, porque tiene credenciales de prueba autoservicio y validador oficial de firmas. Requiere que el usuario cree la cuenta y la aplicación | `backend/providers/mercadopago` |
| 6 | Unificación: el backend envía eventos confirmados a la app; correlación entre canales con evidencia | `payment-engine/correlation`, app |
| 7 | Voz por confianza, historial con canal/proveedor, protocolo ESP32 con `CONFIRMED` | app, firmware, `protocol-fixtures` |
| 8 | Pruebas de integración y guía de pruebas físicas actualizada | `docs/`, pruebas |
