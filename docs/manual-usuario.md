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

Lista de entidades y su estado real: **Compatible**, **En pruebas** o **No disponible**. Solo se pueden activar
las entidades cuya lectura fue probada.

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

## Diagnóstico

Comprobaciones en vivo, pruebas de voz y del equipo, accesos directos a los permisos y el registro técnico.

## Privacidad

- No se guarda el texto completo de las notificaciones, solo los datos del pago y una huella para evitar duplicados.
- No se guardan contraseñas, PIN, tokens ni códigos bancarios, y no se inicia sesión en apps bancarias.
- El historial queda en el celular (excluido de copias en la nube) y se borra según el plazo configurado.
