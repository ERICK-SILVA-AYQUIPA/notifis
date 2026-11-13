# Guía de pruebas físicas

Protocolo para validar PAGOBOX en celulares y equipos **reales**. Ningún resultado de esta guía se ha dado por
cumplido: cada prueba debe ejecutarse, observarse y registrarse. La misma lista está en la app
(**Ajustes → Pruebas físicas**), que toma automáticamente el modelo, la versión de Android y la de PAGOBOX, y
exporta los resultados junto con las versiones de las apps bancarias.

## Antes de empezar

1. Instalar el APK de la última compilación de CI (artefacto `pagobox-debug-apk`).
2. Grabar el firmware (artefacto `pagobox-firmware`) en el prototipo; ver [manual-instalacion.md](manual-instalacion.md).
3. Tener al menos una cuenta real de prueba por entidad y otra persona que envíe pagos pequeños (S/ 0.10 – S/ 1.00).
4. No anotar en las observaciones nombres, números de cuenta, teléfonos ni códigos: solo lo técnico.

### Ficha del celular (una por celular)

| Campo | Valor |
|---|---|
| Fabricante y modelo | |
| Versión de Android / capa del fabricante | |
| Versión de PAGOBOX | |
| Apps bancarias instaladas y versiones | |
| Ahorro de batería del fabricante (nombre) | |
| Fecha y técnico | |

Matriz mínima recomendada: un Samsung (One UI), un Xiaomi/Redmi (HyperOS/MIUI), un Motorola o similar con
Android casi puro, y al menos un equipo con Android 10–11 y otro con Android 14–15.

## 1. Celular Android

| Id | Prueba | Pasos | Resultado esperado | Resultado | Observaciones |
|---|---|---|---|---|---|
| AND-01 | Permiso de lectura de notificaciones | Instalar la app limpia, completar la configuración inicial y conceder el acceso a notificaciones. | Diagnóstico muestra «Lector conectado». Al quitar el permiso, el Inicio pasa a «Sin supervisión». | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-02 | Notificación entrante con pantalla encendida | Activar el modo diagnóstico 15 min. Recibir una notificación real de la app bancaria asignada. | La captura aparece con app, paquete, fecha, categoría y resultado; la muestra queda anonimizada. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-03 | Pantalla bloqueada | Bloquear el celular 5 min y recibir un pago real de una entidad configurada. | Se crea el evento y se anuncia en menos de 10 s. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-04 | Pantalla apagada 30 minutos | Dejar el celular con pantalla apagada 30 min, sin cargar. Recibir un pago real. | Se anuncia. Anotar la demora observada. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-05 | Reinicio del celular | Reiniciar el celular sin abrir la app. Recibir un pago real. | El lector se reconecta solo y se anuncia el pago. Si no, anotar la marca/capa del sistema. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-06 | Ahorro de batería | Activar el ahorro de batería del sistema (y del fabricante, si existe). Recibir un pago real. | Se anuncia, o Diagnóstico advierte la restricción de batería. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-07 | App cerrada desde recientes | Deslizar la app fuera de recientes. Recibir un pago real. | El lector sigue activo y se anuncia. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-08 | Voz en el celular | Salida: solo celular. Recibir un pago real. | Se escucha la frase con monto y remitente correctos. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-09 | Recuperación del servicio | Quitar y volver a dar el acceso a notificaciones. | El Inicio refleja el cambio en menos de 1 min y vuelve a «Operativo». | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-10 | Sin Internet | Activar modo avión y luego Wi-Fi local sin Internet. Probar anuncio de prueba en celular y en el equipo. | La app y el equipo funcionan sin Internet (los pagos reales requieren que la app bancaria reciba la notificación). | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-11 | Notificación que no es un ingreso | Hacer un pago/yapeo SALIENTE real desde la misma cuenta. | No se anuncia. Queda «Rechazado» o «Formato desconocido» en diagnóstico. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-12 | Código de seguridad | Solicitar un código OTP real de la app bancaria con el modo diagnóstico activo. | La captura muestra «[OMITIDO: posible código de seguridad]» y no se guarda el código. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-13 | Borrado de registros | Borrar una muestra y luego «Borrar todo». | Las muestras desaparecen y la exportación queda vacía. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-14 | App financiera no asignada | Con el modo diagnóstico APAGADO, recibir un pago real en una app bancaria que no esté asignada a ninguna entidad. | Aparece en «Posibles apps financieras sin asignar» con paquete y contador; no se anuncia ni se guarda el texto. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| AND-15 | Pantalla apagada y equipo desconectado | Apagar el equipo PAGOBOX, apagar la pantalla del celular 10 min y recibir un pago real de una entidad configurada. | El evento queda en el historial; se anuncia en el celular (si está habilitado) o queda pendiente y se entrega al reconectar el equipo. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |

## 2. Bluetooth

| Id | Prueba | Pasos | Resultado esperado | Resultado | Observaciones |
|---|---|---|---|---|---|
| BLE-01 | Vinculación con clave | Vincular el equipo desde Equipo PAGOBOX e ingresar la clave mostrada en pantalla. | Vinculación exitosa; con clave incorrecta se rechaza. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| BLE-02 | Reconexión Bluetooth | Apagar el Bluetooth del celular 1 min y volver a encenderlo; luego alejarse 15 m y volver. | El equipo se reconecta solo y el estado vuelve a «Conectado». | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| BLE-03 | Prueba de extremo a extremo | Equipo PAGOBOX > Prueba de extremo a extremo. | Se reciben ACK RECEIVED y PLAYED; se registran las latencias. El equipo muestra «PRUEBA - No es un pago». | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |

## 3. Equipo PAGOBOX (hardware)

Ficha del equipo: número de serie / MAC, versión de firmware, versión de placa, batería (mAh), fecha.

| Id | Prueba | Pasos | Resultado esperado | Resultado | Observaciones |
|---|---|---|---|---|---|
| HW-01 | Autoprueba de componentes | Equipo PAGOBOX > Autoprueba. Observar pantalla y escuchar el tono. | Informe con pantalla, audio, BLE, Wi-Fi, batería; el técnico confirma lo visto y oído. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HW-02 | Pantalla | Revisar brillo, colores, texto legible del monto a 1 m. | Monto y entidad legibles; sin píxeles muertos. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HW-03 | Parlante y audio por Wi-Fi | Configurar Wi-Fi del equipo y enviar un anuncio de prueba. | Se escucha la voz completa sin cortes ni saturación al 100 % de volumen. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HW-04 | Botones | Pulsar volumen +, volumen - y silencio; ejecutar la autoprueba. | Cada botón actúa y la autoprueba cuenta las pulsaciones. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HW-05 | Batería y carga | Usar el equipo desconectado hasta batería baja; luego conectar el cargador. | Se muestra batería baja, el celular avisa y el indicador de carga cambia. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HW-06 | Watchdog y recuperación | Desconectar la alimentación durante una reproducción y volver a encender. | El equipo inicia, informa el motivo de reinicio y no repite eventos ya confirmados. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HW-07 | Duplicados | Recibir dos pagos reales del mismo monto y remitente con 1 min de diferencia. | Se anuncian ambos (son pagos distintos); una notificación repetida por el sistema se anuncia una sola vez. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HW-08 | Wi-Fi caído | Apagar el router y recibir un pago real. | El equipo muestra el pago y reproduce el tono; la app no se bloquea. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |

### Mediciones adicionales del equipo

| Medición | Cómo | Valor |
|---|---|---|
| Latencia de recepción (ACK RECEIVED) | Prueba de extremo a extremo × 10, promedio | ms |
| Latencia hasta reproducción (ACK PLAYED) | Prueba de extremo a extremo × 10, promedio | ms |
| Autonomía con Wi-Fi + BLE | Batería llena hasta apagado, 1 anuncio cada 10 min | h |
| Reconexión BLE tras salir de rango | 5 intentos | s |
| Arranque tras corte de energía | 5 intentos | s |

## 3b. Integraciones oficiales (Canal B)

Requiere un backend desplegado con HTTPS y credenciales **de prueba** del proveedor configuradas por quien
administra el servidor (nunca en el celular). Sin eso, estas pruebas quedan «Bloqueada».

| Id | Prueba | Pasos | Resultado esperado | Resultado | Notas |
|---|---|---|---|---|---|
| HYB-01 | Posible ingreso en el equipo | Con el equipo conectado, recibir un pago real por notificación de una entidad configurada. | El equipo muestra «Posible ingreso (no verificado)» y la voz dice «Se detectó un posible ingreso de … por …». Nunca «confirmado». | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HYB-02 | Servidor de integraciones | Ajustes > Integraciones oficiales: ingresar la URL HTTPS del backend de sandbox y el token del celular; «Guardar y probar». | Entorno «sandbox» y «Última sincronización correcta» con hora. Con un token incorrecto: «Token rechazado por el backend». Mercado Pago figura «Esperando primer webhook (sandbox)», nunca «Activa». | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HYB-03 | Pago de prueba confirmado por Mercado Pago | Con credenciales DE PRUEBA de Mercado Pago en el servidor, cobrar con Yape en el checkout de prueba (número y OTP de prueba de la documentación oficial). Esperar hasta 40 s. | El historial muestra el pago «Confirmado por proveedor», canal webhook oficial, sandbox. Voz: «Prueba. ¡Pago confirmado de … !». Equipo: «PRUEBA - Confirmado por proveedor». El panel pasa a «Activa (sandbox)». | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HYB-04 | Webhook con firma inválida | Desde una terminal, enviar al webhook de sandbox una petición con x-signature inventada. | El servidor responde 401, no se crea ningún evento y el panel muestra «Con errores» con el motivo de la firma. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HYB-05 | Solo pagos confirmados | Ajustes: apagar «Anunciar posibles ingresos…». Recibir un pago real por notificación y luego hacer HYB-03. | El pago por notificación queda en el historial como «Omitido», sin voz ni equipo. El pago de HYB-03 sí se anuncia. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HYB-06 | Confirmación con el equipo desconectado | Apagar el equipo, hacer HYB-03 y volver a encender el equipo. | El pago queda pendiente y se entrega al reconectar; se anuncia una sola vez. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |
| HYB-07 | Mismo pago por los dos canales | Hacer HYB-03 con la app Yape del cobro instalada en el celular del comercio (si la notificación llega). | Si el remitente coincide, el posible ingreso queda «Duplicado» del confirmado y no se reanuncia; si falta el remitente, queda «posible mismo pago» sin fusionar. Anotar lo observado. | ☐ Aprobada ☐ Fallida ☐ Bloqueada | |

## 4. Prueba por entidad (para avanzar el estado de compatibilidad)

Para cada entidad, en cada celular de la matriz:

| Caso | Esperado | Resultado |
|---|---|---|
| Pago recibido (monto entero) | Se anuncia con el monto exacto | |
| Pago recibido con decimales | Monto exacto con céntimos | |
| Pago recibido con remitente con tildes/ñ | Remitente correcto en voz; en pantalla sin tildes | |
| Pago enviado (egreso) | No se anuncia | |
| Solicitud de pago / cobro | No se anuncia | |
| Publicidad de la app | No se anuncia | |
| Dos pagos iguales seguidos | Se anuncian ambos | |
| Notificación repetida por el sistema | Se anuncia una sola vez | |
| Código OTP | Se omite en diagnóstico, no se anuncia | |

Con todas aprobadas en un celular, se registra evidencia `DEVICE`; con todo el protocolo (secciones 1–4) en la
matriz mínima, evidencia `FULL_PROTOCOL` (ver [compatibilidad-bancaria.md](compatibilidad-bancaria.md)).

## 5. Qué hacer si una prueba falla

1. Marcarla como **Fallida** con la observación técnica.
2. Exportar el registro de Diagnóstico y el de notificaciones anonimizado.
3. Abrir una incidencia en GitHub con modelo, Android, versiones y pasos; **sin datos personales**.
