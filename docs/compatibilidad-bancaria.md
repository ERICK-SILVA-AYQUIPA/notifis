# Registro de compatibilidad bancaria

Una entidad solo se considera compatible después de superar sus pruebas específicas
con notificaciones reales recopiladas con autorización (sección 14). Hasta entonces el
motor **no** procesa sus notificaciones.

| Entidad | Prioridad | Paquete Android | Estado | Modelo / versión probados |
|---|---|---|---|---|
| Yape | P0 | por confirmar | NOT_IMPLEMENTED | — |
| Plin | P0 | por confirmar (Plin vive dentro de apps bancarias) | NOT_IMPLEMENTED | — |
| BCP | P1 | por confirmar | NOT_IMPLEMENTED | — |
| Interbank | P1 | por confirmar | NOT_IMPLEMENTED | — |
| BBVA | P1 | por confirmar | NOT_IMPLEMENTED | — |
| Scotiabank | P1 | por confirmar | NOT_IMPLEMENTED | — |
| Banco de la Nación | P1 | por confirmar | NOT_IMPLEMENTED | — |

## Cómo agregar una entidad

1. Recopilar notificaciones reales (ingreso, egreso, solicitud, publicidad) con autorización.
2. Anonimizar los ejemplos y guardarlos como casos de prueba.
3. Escribir las reglas (`NotificationRule`) y el paquete en `KnownSources.kt`.
4. Pasar el estado a `IN_TESTING` y agregar pruebas que cubran ingresos, egresos, solicitudes y publicidad.
5. Solo después de las pruebas en celulares reales, marcar `VERIFIED` y completar esta tabla.
