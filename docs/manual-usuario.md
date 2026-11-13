# Manual de usuario

## Pantalla de inicio

- **Estado del sistema:** Operativo, Degradado, Desconectado o Sin supervisión. La app nunca muestra
  «Operativo» si no puede comprobar todos los componentes.
- **Hoy:** ingresos **detectados** (por notificación) e ingresos **verificados** (por una fuente oficial),
  siempre separados. Un ingreso detectado **no es una confirmación bancaria**: revisa tu app del banco antes
  de entregar mercadería de alto valor.
- **Silenciar:** 15, 30 o 60 minutos. Los pagos se guardan y se anuncian al terminar el silencio.

## Historial y detalle

Cada evento muestra importe, entidad, remitente (si la notificación lo incluye), hora, estado y cada intento de
anuncio (en el celular o en el equipo). Lo que la notificación no trae aparece como «No disponible».

| Estado | Significado |
|---|---|
| Ingreso detectado | Notificación de ingreso reconocida, pendiente de anunciar |
| Anunciado | Se reprodujo el aviso |
| Verificado | Confirmado por una integración oficial |
| Información insuficiente | Ingreso sin importe: no se anuncia |
| No entregado | No se pudo anunciar a tiempo |

## Bancos

Cada entidad muestra su estado real (Sin configurar, Esperando muestras, Configurado, Probado en laboratorio,
Probado en celular, Compatible verificado, Formato cambiado o No disponible temporalmente), los paquetes de su
app, cuántos formatos verificados tiene y cuántas muestras se guardaron. Puedes **pausar** una entidad. Si
aparece «Formato cambiado», revisa las muestras en el diagnóstico de notificaciones y luego toca «Ya revisé el
formato». El estado no se puede subir a mano: depende de pruebas reales (ver compatibilidad-bancaria.md).

## Diagnóstico de notificaciones

1. Toca **15 min** o **60 min** para activar el modo diagnóstico.
2. Pide que te envíen un pago pequeño. La notificación aparece con app, paquete, fecha, categoría, extras,
   estado del lector y resultado. El texto se ve anonimizado; «Ver original» lo muestra solo en pantalla.
3. Toca **Asignar a entidad** para indicar de qué banco es la app. Revisa que diga «Instalada desde: Google Play».
4. Las muestras guardadas están anonimizadas. Puedes **exportarlas** (tú eliges con quién compartir), borrar una o
   **borrar todo**. Los posibles códigos de seguridad nunca se muestran ni se guardan.

## Pruebas físicas

Lista de pruebas en celular y equipo reales. Marca cada una como Aprobada, Fallida o Bloqueada, agrega
observaciones técnicas (sin datos personales) y exporta el resultado.

## Voz

Elige la voz (las voces masculinas o femeninas dependen de las instaladas en el celular), la velocidad, el tono
y el volumen, y si se anuncia el nombre del remitente. Puedes crear frases propias:

| Variable | Ejemplo |
|---|---|
| `{monto_hablado}` | 35 soles con 50 céntimos |
| `{monto}` | 35.50 |
| `{moneda}` | soles |
| `{banco}` | Yape |
| `{remitente}` | Juan |
| `{hora}` | 15:30 |
| `{tipo_operacion}` | transferencia recibida |

Pon entre corchetes lo que deba omitirse si falta un dato: `¡Atención! [{remitente} te envió ]{monto_hablado}`.
Si una variable obligatoria falta, se usa la frase predeterminada.

## Equipo

Vinculación, batería, volumen, Wi-Fi, firmware, último evento confirmado, prueba de sonido y desvinculación.
**Autoprueba** revisa pantalla, audio, Bluetooth, Wi-Fi, batería, botones y el motivo del último reinicio.
**Extremo a extremo** envía un evento marcado «PRUEBA - No es un pago» y mide cuánto tarda en recibirse y
reproducirse.

## Diagnóstico

Comprobaciones en vivo, pruebas de voz y del equipo, accesos directos a los permisos y el registro técnico.

## Privacidad

- No se guarda el texto completo de las notificaciones, solo los datos del pago y una huella para evitar duplicados.
- Las muestras de diagnóstico se guardan anonimizadas (importes → 9, dígitos → #, nombres → `<NOMBRE>`), se
  borran según el plazo configurado o cuando tú quieras, y nunca se envían a servidores.
- No se guardan contraseñas, PIN, tokens ni códigos bancarios, y no se inicia sesión en apps bancarias.
- El historial queda en el celular (excluido de copias en la nube) y se borra según el plazo configurado.
