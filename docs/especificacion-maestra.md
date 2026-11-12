# PAGOBOX PERÚ — Documento maestro del proyecto

**Sistema inteligente de avisos de pagos para Perú**
Especificación funcional · Arquitectura técnica · Android + IoT

- **Nombre provisional:** PAGOBOX PERÚ
- **Tipo de producto:** Sistema inteligente de detección, notificación y anuncio de pagos digitales.
- **Objetivo:** Desarrollar una solución tecnológica que permita a los negocios peruanos escuchar automáticamente los pagos recibidos mediante Yape, Plin y aplicaciones bancarias compatibles, utilizando un celular Android conectado a un dispositivo físico con pantalla y parlante.

La solución estará diseñada para ser comercializada a tiendas, minimarkets, restaurantes, bodegas, farmacias, mercados y otros negocios.

---

## 1. Visión y objetivos del producto

No construiremos solamente un lector de notificaciones. Construiremos un sistema compuesto por dos productos conectados:

- **PAGOBOX Android:** aplicación que detecta, procesa, clasifica y administra los eventos de pago.
- **PAGOBOX Device:** dispositivo electrónico independiente que muestra información y reproduce los anuncios de voz.

La comunicación entre ambos será automática. El comerciante no tendrá que abrir la aplicación para escuchar los avisos.

### Objetivos principales

- Detectar notificaciones de pagos recibidos de entidades compatibles.
- Identificar el importe, banco, remitente y hora cuando esos datos estén disponibles.
- Diferenciar ingresos, egresos, solicitudes y otras notificaciones.
- Evitar anuncios duplicados.
- Reproducir mensajes de voz personalizados.
- Mostrar la información en una pantalla física.
- Mantener la conexión y recuperarse de errores.
- Diferenciar pagos detectados de pagos verificados.
- Registrar el historial de operaciones.
- Permitir incorporar nuevas entidades financieras sin reconstruir todo el sistema.

> **Principio fundamental:** nunca anunciar un pago como verificado si solamente existe una notificación que no ofrece una confirmación bancaria confiable.

## 2. Alcance funcional

### 2.1 Aplicaciones financieras compatibles

El sistema tendrá una arquitectura modular denominada **PaymentSourceAdapter**. Cada entidad tendrá su propio adaptador de lectura y clasificación.

| Entidad | Prioridad | Método inicial |
|---|---|---|
| Yape | P0 | Notificaciones Android |
| Plin | P0 | Notificaciones Android |
| BCP | P1 | Notificaciones Android |
| Interbank | P1 | Notificaciones Android |
| BBVA | P1 | Notificaciones Android |
| Scotiabank | P1 | Notificaciones Android |
| Banco de la Nación | P1 | Notificaciones Android |
| Otros bancos | P2 | Adaptadores incorporables |

La compatibilidad será comprobada individualmente. No se asumirá que todas las entidades entregan el mismo formato de notificación ni los mismos datos.

Se contemplarán dos mecanismos de integración:

- **Notification Adapter:** interpreta notificaciones recibidas por Android.
- **Official Payment Adapter:** recibe confirmaciones mediante APIs o webhooks oficiales, cuando existan y el comercio tenga autorización para utilizarlos.

No se automatizará el inicio de sesión en aplicaciones bancarias, ni se extraerán contraseñas, tokens, PIN o códigos de seguridad.

### 2.2 Motor de detección de pagos

El motor deberá:

- Reconocer el paquete de origen de la notificación.
- Identificar el tipo de entidad.
- Detectar si el evento corresponde a un ingreso.
- Extraer el importe cuando esté disponible.
- Extraer el nombre del remitente cuando esté disponible.
- Extraer la fecha y hora.
- Detectar monedas y formatos peruanos.
- Rechazar notificaciones no reconocidas.
- Clasificar eventos incompletos.
- Evitar falsos positivos.

Se implementará una arquitectura basada en reglas específicas por entidad, evitando depender exclusivamente de inteligencia artificial o reconocimiento de texto probabilístico.

### 2.3 Clasificación de eventos

| Estado | Significado |
|---|---|
| RECEIVED | Notificación recibida |
| PARSED | Información extraída |
| INCOME_CANDIDATE | Posible ingreso |
| VERIFIED | Confirmación de fuente confiable |
| ANNOUNCED | Anuncio reproducido |
| DUPLICATE | Evento repetido |
| REJECTED | Evento descartado |
| PENDING_REVIEW | Información insuficiente |
| DELIVERY_FAILED | No se pudo entregar el aviso al dispositivo |

No se permitirá pasar automáticamente de RECEIVED a VERIFIED solo por reconocer una notificación.

### 2.4 Sistema de voz personalizable

Esta será una de las funcionalidades principales del producto.

**Frases predeterminadas:**

- «¡Te yapearon [monto] soles!»
- «¡Recibiste [monto] soles por Plin!»
- «¡Te hicieron una transferencia de [monto] soles!»
- «¡Recibiste un pago de [monto] soles!»
- «¡Pago verificado por [monto] soles!»

**Frases personalizadas:** el administrador podrá crear y editar frases utilizando variables:
`{monto}`, `{moneda}`, `{banco}`, `{remitente}`, `{hora}`, `{tipo_operacion}`.

Ejemplo: «¡Atención! {remitente} te envió {monto} soles mediante {banco}».

Si no existe el nombre del remitente, se eliminará esa parte de la frase sin generar mensajes incorrectos.

**Configuraciones:**

- Voz masculina.
- Voz femenina.
- Voces disponibles según el motor TTS.
- Velocidad de pronunciación.
- Volumen.
- Tono.
- Frases por entidad.
- Frases para pagos verificados.
- Frases para pagos detectados.
- Anuncio del nombre del remitente opcional.
- Prueba de voz desde la aplicación.
- Selección de idioma español.

**Requisito técnico importante:** el texto dinámico se generará mediante Text-to-Speech (TTS). Para evitar depender de Internet, se deberá probar la disponibilidad de voces españolas instaladas localmente en Android.

Para el dispositivo físico se implementará un transporte de audio adecuado. No se asumirá que BLE puede transmitir voz continua con la calidad y estabilidad requeridas.

### 2.5 Sistema de pantalla

La pantalla mostrará:

- Nombre del producto.
- Hora del último pago.
- Importe recibido.
- Entidad financiera.
- Nombre del remitente, cuando esté disponible.
- Estado del evento.
- Estado de conexión con Android.
- Nivel de batería.
- Volumen.

La interfaz tendrá tres estados:

1. Esperando pagos.
2. Nuevo pago recibido.
3. Sistema desconectado o con errores.

La pantalla deberá volver automáticamente a la pantalla principal después de mostrar el último evento.

### 2.6 Motor de anuncios

El sistema tendrá una cola de reproducción.

| Orden | Evento | Anuncio |
|---|---|---|
| 1 | Yape S/20 | Te yapearon 20 soles |
| 2 | Plin S/35 | Recibiste 35 soles por Plin |
| 3 | BCP S/100 | Recibiste una transferencia de 100 soles |

Requisitos:

- No interrumpir una frase para reproducir otra.
- Ordenar anuncios por llegada.
- Permitir configurar prioridades.
- Controlar anuncios repetidos.
- Permitir silenciar temporalmente.
- Conservar eventos pendientes si se interrumpe la conexión.
- Evitar que se reproduzca dos veces un mismo evento al reconectar.
- Mostrar el estado de entrega y reproducción.

Los avisos pendientes no se anunciarán como pagos nuevos si ya fueron reproducidos anteriormente.

## 3. Arquitectura técnica

```
APLICACIONES FINANCIERAS
  Yape · Plin · BCP · BBVA · Interbank · otros
        │
ANDROID — CAPA DE INGRESO
  NotificationListenerService · Official Payment API Adapter · Source Package Registry
        │
PAYMENT CORE
  Notification Parser · Payment Classifier · Verification Engine
  Deduplication Engine · Event Queue
        │
  ├── VOICE ENGINE (TTS y frases)
  └── LOCAL DATABASE (Room y eventos)
        │
DEVICE COMMUNICATION (protocolo)
  Bluetooth LE GATT · Wi-Fi audio transport · Acknowledgements · Retries y secuencias
        │
PAGOBOX DEVICE
  ESP32 · Pantalla · Parlante · Batería
```

### 3.1 Aplicación Android

| Tecnología | Responsabilidad |
|---|---|
| Kotlin | Lenguaje principal |
| Jetpack Compose | Interfaz de usuario |
| MVVM + Clean Architecture | Organización del código |
| NotificationListenerService | Lectura de notificaciones |
| Room | Base de datos local |
| DataStore | Preferencias |
| Android TextToSpeech | Generación de voz |
| WorkManager | Tareas diferibles de mantenimiento |
| Bluetooth GATT | Comunicación con dispositivo |
| Coroutines + Flow | Procesamiento asíncrono |

La aplicación deberá funcionar sin necesidad de mantener la pantalla encendida.

Se implementará una sección de permisos y diagnóstico para que el usuario pueda comprobar:

- Acceso a notificaciones.
- Permisos Bluetooth.
- Servicio de lectura conectado.
- Restricciones de batería.
- Estado del dispositivo.
- Disponibilidad del motor TTS.

No se prometerá funcionamiento ininterrumpido en todos los modelos Android sin pruebas específicas.

### 3.2 Dispositivo electrónico

| Componente | Especificación |
|---|---|
| Microcontrolador | ESP32-S3 |
| Pantalla | TFT IPS a color |
| Amplificador | Clase D (p. ej. I2S MAX98357) |
| Parlante | 3 W, según diseño acústico |
| Alimentación | USB-C |
| Batería | Li-ion protegida |
| Indicadores | LED RGB |
| Controles | Botones físicos |
| Conectividad | BLE + Wi-Fi |

El ESP32-S3 dispone de Wi-Fi y Bluetooth LE. Se utilizará BLE para control y comunicación de eventos, y Wi-Fi para transferencia de audio cuando sea necesario.

**Decisión de ingeniería:** la generación de voz se realizará inicialmente en Android. El dispositivo recibirá el audio mediante un canal Wi-Fi local, mientras BLE manejará la conexión, estado y comandos. Si no hay Wi-Fi local, se utilizará un paquete de mensajes de voz preinstalados para anuncios básicos. Se deberá validar este mecanismo en un prototipo antes de cerrar el diseño electrónico.

**Firmware:** ESP-IDF · FreeRTOS · BLE GATT · Wi-Fi · Gestión de energía · Watchdog · Actualización OTA · Almacenamiento de configuración · Control de pantalla y audio.

## 4. Protocolo de comunicación

El sistema deberá utilizar mensajes estructurados, no enviar texto sin identificar ni depender de una conexión permanente a Internet.

Cada evento tendrá: identificador único, número de secuencia, identificador del dispositivo, fecha de creación, tipo de evento, importe y moneda, banco de origen, remitente (si está disponible), estado de verificación y estado de reproducción.

```json
{
  "eventId": "evt_000001",
  "sequence": 1,
  "source": "YAPE",
  "eventType": "INCOMING_TRANSFER",
  "amount": 35.00,
  "currency": "PEN",
  "sender": "Juan",
  "timestamp": "2026-11-12T15:30:00-05:00",
  "verificationStatus": "DETECTED",
  "announcementStatus": "PENDING"
}
```

Este ejemplo es un evento de prueba, no una confirmación real de pago.

### Reglas de comunicación

1. Android genera el evento.
2. El motor local lo registra en Room.
3. El dispositivo recibe el evento.
4. El dispositivo confirma su recepción mediante ACK.
5. Android envía el audio o el identificador de voz correspondiente.
6. El dispositivo reproduce el anuncio.
7. El dispositivo confirma la reproducción.
8. Android actualiza el historial.
9. Si se pierde la conexión, se conserva el evento y se intenta recuperar la comunicación.

> **Importante:** recibir un ACK confirma la recepción técnica del mensaje, no que el dinero haya ingresado a una cuenta.

## 5. Diseño de base de datos

La base de datos local utilizará Room.

| Tabla | Contenido |
|---|---|
| PaymentEvents | Eventos de pago detectados |
| PaymentSources | Entidades financieras |
| Devices | Dispositivos vinculados |
| AnnouncementHistory | Historial de anuncios |
| VoiceProfiles | Configuración de voz |
| PhraseTemplates | Frases personalizadas |
| AppSettings | Configuración general |
| DiagnosticLogs | Errores y diagnósticos |

**Campos principales de PaymentEvents:** id, eventId, sourceId, eventType, amount, currency, senderName, eventTimestamp, detectedTimestamp, verificationStatus, announcementStatus, rawNotificationHash, deviceId, createdAt, updatedAt.

No se guardará indiscriminadamente el contenido completo de las notificaciones. Se conservarán solamente los datos necesarios, con controles de privacidad y retención.

## 6. Arquitectura del software

```
pagobox/
├── app/              navigation/ · ui/ · MainActivity.kt
├── core/             common/ · database/ · bluetooth/ · audio/ · security/
├── domain/           models/ · repositories/ · usecases/
├── data/             local/ · preferences/ · repositories/
├── features/         dashboard/ · payment_history/ · bank_sources/ · voice_settings/
│                     device_management/ · diagnostics/ · business_settings/
├── payment_engine/   notification_listener/ · source_adapters/ · parsers/ · classifiers/
│                     verification/ · deduplication/ · event_queue/
└── firmware/         bluetooth/ · display/ · audio/ · power/ · storage/ · diagnostics/
```

### Principios de desarrollo

Separación de responsabilidades · Código reutilizable · Inyección de dependencias · Interfaces para cada entidad bancaria · Manejo centralizado de errores · Pruebas unitarias · Pruebas de integración · Registro de eventos · Documentación técnica · Control de versiones mediante Git.

## 7. Seguridad y confiabilidad

Este apartado será obligatorio antes de comercializar el producto.

### Seguridad

- Vinculación mediante código de emparejamiento.
- Autenticación del dispositivo.
- Cifrado de las comunicaciones.
- Protección de las configuraciones.
- Sin almacenamiento de credenciales bancarias.
- Sin acceso a operaciones bancarias privadas.
- Separación entre eventos detectados y confirmados.
- Protección contra mensajes repetidos.
- Registro de modificaciones importantes.

### Recuperación automática

| Situación | Comportamiento |
|---|---|
| Pantalla apagada | Continuar escuchando notificaciones |
| Aplicación cerrada por el sistema | Recuperar el servicio cuando Android lo permita |
| Reinicio del celular | Comprobar permisos y recuperar conexión |
| Bluetooth desactivado | Detectar el problema y solicitar activación |
| Equipo apagado | Conservar eventos pendientes |
| Internet interrumpido | Seguir procesando eventos locales |
| Batería baja | Emitir alerta |
| Error de audio | Mostrar error y permitir nueva prueba |
| Notificación incompleta | No inventar información |

No se prometerá recuperación silenciosa de permisos revocados ni funcionamiento garantizado después de que el usuario fuerce la detención de la aplicación.

### Política de confiabilidad

El sistema deberá comunicar claramente cuatro estados:

- **Operativo:** los componentes principales están disponibles.
- **Degradado:** existe una limitación conocida.
- **Desconectado:** el dispositivo no está comunicándose.
- **Sin supervisión:** el lector no está recibiendo eventos.

La aplicación nunca deberá mostrar «Todo funcionando» cuando no pueda comprobarlo.

## 8. Interfaz de usuario

La aplicación tendrá una experiencia profesional, sencilla y pensada para comerciantes.

| Pantalla | Funcionalidad |
|---|---|
| Splash | Identidad visual de PAGOBOX |
| Configuración inicial | Permisos y activación |
| Inicio | Estado del sistema |
| Historial | Transacciones detectadas |
| Detalle del evento | Información disponible |
| Bancos | Entidades compatibles |
| Voz | Configuración de voces y frases |
| Dispositivos | Vinculación y administración |
| Diagnóstico | Pruebas de funcionamiento |
| Negocio | Nombre y datos del establecimiento |
| Configuración | Preferencias y seguridad |

**Panel principal:** total de ingresos detectados del día, número de eventos, número de pagos verificados, último pago recibido, estado del lector, estado del dispositivo, estado del audio y estado de las entidades supervisadas.

Los totales de pagos detectados y pagos verificados estarán separados para evitar confusiones contables.

## 9. Especificaciones de calidad

Estos serán los objetivos de aceptación del producto, no resultados que podamos afirmar antes de realizar pruebas.

| Indicador | Objetivo |
|---|---|
| Detección | Al menos 99% en el conjunto de pruebas compatibles |
| Anuncios duplicados | Cero duplicados en pruebas de reintentos |
| Entrega de eventos | Confirmación mediante ACK |
| Recuperación de Bluetooth | Reconexión automática en condiciones probadas |
| Funcionamiento con pantalla apagada | Prueba continua de 24 horas |
| Persistencia | Ningún evento registrado debe perderse por un cierre normal |
| Voz | Pronunciación correcta de importes en soles |
| Historial | Coherencia entre eventos y anuncios |
| Seguridad | Sin credenciales bancarias almacenadas |

La tasa de detección se medirá por banco, modelo de celular y versión de aplicación bancaria. No se utilizará una tasa global para ocultar incompatibilidades particulares.

## 10. Plan de pruebas

**Pruebas funcionales:** recibir una notificación de Yape · recibir una notificación de Plin · recibir notificaciones bancarias · detectar ingresos · ignorar egresos · detectar eventos duplicados · identificar importes con decimales · reconocer cantidades grandes · procesar nombres con caracteres especiales · generar mensajes de voz · mostrar el importe en la pantalla física.

**Pruebas de resistencia:** 24 horas con pantalla apagada · reinicio del celular · reinicio del dispositivo · pérdida de Bluetooth · pérdida de Wi-Fi · pérdida de Internet · batería baja · varias transferencias simultáneas · cien notificaciones de prueba · actualización de firmware · actualización de aplicaciones bancarias.

**Pruebas contra fraudes:** capturas de pantalla falsas · notificaciones de egresos · solicitudes de dinero · mensajes de publicidad · notificaciones antiguas · mensajes sin importe · mensajes con formato desconocido · repetición artificial de eventos.

Ninguna de estas pruebas deberá provocar un anuncio de pago confirmado sin evidencia válida.

## 11. Fases de desarrollo

1. **Investigación y prototipo Android** (primera prioridad): identificar paquetes oficiales de las aplicaciones, recopilar notificaciones reales autorizadas, estudiar sus formatos, crear el lector Android, crear los primeros adaptadores de Yape y Plin.
2. **Motor de procesamiento:** clasificación, extracción de información, detección de ingresos, deduplicación, registro en Room, motor de verificación.
3. **Voz e interfaz:** Text-to-Speech, frases personalizadas, historial, panel principal, configuración.
4. **Comunicación con hardware:** BLE GATT, protocolo de eventos, ACK y reintentos, transferencia de audio, reconexión.
5. **Fabricación del prototipo:** ESP32-S3, pantalla, parlante, batería, carcasa, firmware.
6. **Compatibilidad bancaria:** Yape y Plin, BCP, Interbank, BBVA, Scotiabank, Banco de la Nación, incorporación progresiva de otras entidades.
7. **Validación comercial:** pruebas en negocios reales con consentimiento, pruebas de resistencia, seguridad, documentación, preparación para fabricación.

## 12. Futuras funcionalidades comerciales

No son necesarias para la primera versión, pero la arquitectura debe permitir incorporarlas: administración de múltiples establecimientos, aplicación de administración para propietarios, panel web, gestión de múltiples dispositivos, licencias comerciales, actualizaciones remotas, diagnóstico remoto autorizado, estadísticas de ventas, integración con sistemas de inventario, integración con sistemas POS, reportes descargables, personalización de marca para otros negocios.

## 13. Criterios de entrega del proyecto

El desarrollo no se considerará terminado hasta entregar: código fuente Android · proyecto de firmware ESP32 · base de datos y migraciones · protocolo de comunicación documentado · APK de pruebas · firmware compilado · diseño de la interfaz · esquema electrónico · lista de componentes · manual de instalación · manual de usuario · informe de pruebas · registro de entidades bancarias realmente compatibles · documentación de limitaciones conocidas · procedimiento de actualización y recuperación.

## 14. Definición de la primera versión comercial (V1)

PAGOBOX V1 incluirá: aplicación Android nativa · lectura de notificaciones permitidas · Yape y Plin como primeras integraciones · arquitectura extensible para bancos peruanos · clasificación de ingresos y egresos · estados de verificación · prevención de duplicados · voz masculina y femenina · frases personalizables · pantalla física con importe, hora, banco y remitente disponible · equipo ESP32 · Bluetooth y Wi-Fi · historial local · diagnóstico del sistema · recuperación ante desconexiones · seguridad y privacidad.

No se considerará compatible una entidad hasta que haya superado sus pruebas específicas.

## 15. Decisiones técnicas que deben validarse antes de fabricar

1. **Acceso a las notificaciones:** comprobar qué información expone realmente cada aplicación bancaria.
2. **Verificación oficial:** investigar qué integraciones permiten confirmar pagos, especialmente para comercios.
3. **Transporte de voz:** validar BLE para control y Wi-Fi para audio, incluyendo funcionamiento sin router.
4. **Compatibilidad Android:** comprobar diferentes fabricantes, políticas de ahorro de batería y versiones del sistema.

La documentación oficial de Android contempla estados de conexión y desconexión de los lectores de notificaciones. También existen restricciones de ejecución en segundo plano que deben respetarse en Android moderno.

## 16. Fuentes técnicas oficiales

- Android — NotificationListenerService.
- Android — Foreground Services.
- Espressif — ESP32-S3.
- ESP-IDF — Bluetooth LE.

## 17. Instrucción principal para el equipo de desarrollo

El proyecto deberá construirse con criterios de ingeniería de software profesional.

No se permitirá:

- Crear pantallas sin funcionalidad.
- Simular pagos reales.
- Inventar compatibilidad bancaria.
- Utilizar datos de prueba como operaciones confirmadas.
- Dejar botones sin implementar.
- Colocar información ficticia en el historial de producción.
- Introducir dependencias innecesarias.
- Mezclar el reconocimiento bancario con la interfaz.
- Ocultar errores de conexión.
- Entregar un prototipo sin documentación.

Cada módulo deberá tener responsabilidades definidas, pruebas y criterios de aceptación.

**Resultado esperado:** un sistema Android + dispositivo electrónico que pueda utilizarse en negocios peruanos para anunciar pagos recibidos de forma rápida, comprensible, segura y confiable, dentro de las capacidades verificadas de cada integración.
