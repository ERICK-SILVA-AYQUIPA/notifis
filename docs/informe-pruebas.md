# Informe de pruebas

Este informe separa lo que ya se comprueba automáticamente en cada cambio (GitHub Actions → **CI**) de lo
que debe probarse en celulares y hardware reales. Los objetivos de calidad de la sección 9 **no** se dan por
cumplidos hasta completar las pruebas manuales.

## Pruebas automáticas

| Módulo | Archivo | Qué comprueba |
|---|---|---|
| Dominio | `EventStateMachineTest` | No se puede pasar de RECEIVED a VERIFIED; estados finales sin salida |
| Importes | `AmountParserTest` | Formatos peruanos (S/, S/., PEN, miles con coma, decimales), dólares (US$, USD), textos ambiguos rechazados |
| Motor | `PaymentPipelineTest` | Ingreso → candidato (nunca verificado); entidad sin formatos → solo muestra; formato desconocido; entidad pausada o con formato cambiado; secuencia solo al crear eventos |
| Suite sintética (fase 4) | `SyntheticNotificationSuiteTest` | Pago recibido, dinero enviado, solicitud, duplicado del sistema, dos pagos iguales legítimos, decimales, incompleta, desconocida, caracteres especiales, monedas, antiguas y futuras, regla mal escrita que dice «pagaste» bloqueada por la validación independiente de egresos |
| Adaptadores (fase 3) | `BankAdaptersTest` | Los 7 adaptadores existen, no tienen formatos inventados ni paquetes sin evidencia, devuelven «formato desconocido», y cada formato real (cuando exista) reconoce su muestra |
| Compatibilidad (fase 5) | `CompatibilityRegistryTest` | Ningún estado declarado sin evidencia; VERIFIED_COMPATIBLE exige protocolo completo; estado efectivo por celular |
| Privacidad | `AnonymizerTest` | Importes, dígitos, correos y nombres anonimizados; OTP/PIN/clave omitidos por completo |
| Integración | `EndToEndIntegrationTest` | Notificación → motor → cola → frase → JSON idéntico al fixture que lee el firmware |
| Voz | `VoiceTest` | Importes hablados, frases predeterminadas, plantillas inválidas, hora de Lima |
| Cola | `AnnouncementQueueTest` | Orden, sin interrupciones, sin repetir, reintentos, silencio, prioridades, restauración |
| Verificación y protocolo | `VerificationAndProtocolTest` | Solo una confirmación oficial verifica; JSON con escape correcto |
| App | `AppLogicTest` | Prioridad de frases, remitente opcional, persistencia sin pérdida de precisión |
| App (base de datos) | `MigrationSchemaTest` | La migración 1→2 crea exactamente las tablas e índices que Room exporta en el esquema v2 |
| Firmware (host) | `firmware/test_host` | Mensajes y comandos (incl. TEST_EVENT y SELF_TEST), ACK, secuencia, pantalla, importes, STATUS con motivo de reinicio, informe de autoprueba, WAV, batería y **contrato con Android** usando `protocol-fixtures/` |
| Compilación | CI | APK de pruebas y firmware ESP32-S3 compilados en cada cambio |

Resultado local de las pruebas del motor y dominio (JVM): ver la última ejecución de CI en GitHub Actions; las
pruebas de la app (`:app:testDebugUnitTest`) y la compilación del APK y del firmware solo se ejecutan en CI.

## Pruebas manuales pendientes (sección 10)

El protocolo completo, con pasos y resultados esperados, está en [guia-pruebas-fisicas.md](guia-pruebas-fisicas.md)
y en la app (**Ajustes → Pruebas físicas**). Registrar para cada una: fecha, modelo de celular, versión de
Android, versión de la app bancaria y resultado.

### Funcionales

| Prueba | Estado |
|---|---|
| Notificación real de Yape | Pendiente: no hay muestras reales (entidad en «Esperando muestras») |
| Notificación real de Plin | Pendiente: no hay muestras reales (entidad en «Esperando muestras») |
| Notificaciones bancarias | Pendiente |
| Ignorar egresos reales | Pendiente |
| Importes con decimales y cantidades grandes | Cubierto en pruebas automáticas; falta con notificaciones reales |
| Nombres con caracteres especiales | Cubierto en pruebas automáticas; falta en pantalla física |
| Voz en el celular | Pendiente en dispositivo |
| Importe en la pantalla física | Pendiente en prototipo |

### Resistencia

| Prueba | Estado |
|---|---|
| 24 horas con pantalla apagada | Pendiente |
| Reinicio del celular / del equipo | Pendiente |
| Pérdida de Bluetooth / Wi-Fi / Internet | Pendiente |
| Batería baja | Pendiente |
| Varias transferencias simultáneas y 100 notificaciones de prueba | Pendiente |
| Actualización de firmware (OTA) y reversión | Pendiente |
| Actualización de apps bancarias | Pendiente |

### Contra fraudes

| Prueba | Estado |
|---|---|
| Capturas de pantalla falsas | No aplica a la lectura de notificaciones; documentado en limitaciones |
| Egresos, solicitudes, publicidad, antiguas, sin importe, formato desconocido | Cubierto en pruebas automáticas con formatos de prueba |
| Repetición artificial de eventos | Cubierto (deduplicación, cola y secuencia del equipo) |
| Notificación falsificada por otra app | Pendiente de evaluar; ver limitaciones |
