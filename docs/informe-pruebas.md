# Informe de pruebas

Este informe separa lo que ya se comprueba automáticamente en cada cambio (GitHub Actions → **CI**) de lo
que debe probarse en celulares y hardware reales. Los objetivos de calidad de la sección 9 **no** se dan por
cumplidos hasta completar las pruebas manuales.

## Pruebas automáticas

| Módulo | Archivo | Qué comprueba |
|---|---|---|
| Dominio | `EventStateMachineTest` | No se puede pasar de RECEIVED a VERIFIED; estados finales sin salida |
| Importes | `AmountParserTest` | Formatos peruanos (S/, S/., miles con coma, decimales), dólares, textos ambiguos rechazados |
| Motor | `PaymentPipelineTest` | Ingresos → candidato (nunca verificado); duplicados; egresos, solicitudes, publicidad y formatos desconocidos rechazados; notificaciones antiguas; ingreso sin importe → revisión; paquetes no supervisados ignorados; ninguna entidad real declarada compatible |
| Voz | `VoiceTest` | Importes hablados (sol/soles, céntimos), frases predeterminadas, partes opcionales, plantillas inválidas, hora de Lima |
| Cola | `AnnouncementQueueTest` | Orden de llegada, sin interrupciones, sin repetir anunciados, reintento tras fallo, silencio, prioridades, restauración tras reinicio |
| Verificación y protocolo | `VerificationAndProtocolTest` | Solo una confirmación oficial verifica; JSON del evento con escape correcto |
| App | `AppLogicTest` | Prioridad de frases por entidad, remitente opcional, plantillas desactivadas, persistencia sin pérdida de precisión |
| Firmware (host) | `firmware/test_host` | Lectura de eventos y comandos, campos opcionales, mensajes inválidos, ACK, control de secuencia, estados de pantalla, texto sin tildes, formato de importes, STATUS sin token cuando no hay Wi-Fi, lectura de WAV, curva de batería |
| Compilación | CI | APK de pruebas y firmware ESP32-S3 compilados en cada cambio |

## Pruebas manuales pendientes (sección 10)

Registrar para cada una: fecha, modelo de celular, versión de Android, versión de la app bancaria y resultado.

### Funcionales

| Prueba | Estado |
|---|---|
| Notificación real de Yape | Pendiente (requiere reglas de fase 1) |
| Notificación real de Plin | Pendiente (requiere reglas de fase 1) |
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
