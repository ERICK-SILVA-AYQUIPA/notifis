# Fase 8 — Pruebas de integración e informe final

Fecha: 13/11/2026.

## Pruebas de integración nuevas

`backend/src/test/kotlin/pe/pagobox/backend/HybridFlowIntegrationTest.kt` recorre el flujo híbrido completo por
HTTP real (Ktor en memoria) con **datos de prueba**; el «API de Mercado Pago» es un servidor local que responde
con los campos del SDK oficial, no Mercado Pago:

1. Antes de cualquier webhook, `/api/v1/integrations` muestra Mercado Pago como `AWAITING_FIRST_EVENT`.
2. Webhook con firma falsificada → `401`, ningún evento.
3. Webhook firmado → consulta del pago → `PROVIDER_CONFIRMED`; la misma entrega repetida no crea otro evento;
   el panel pasa a `ACTIVE`.
4. El celular lee `/api/v1/events` con su token y arma el evento oficial.
5. Correlación con un posible ingreso Android del mismo importe, entidad y minuto: como el webhook no trae
   remitente, queda **posible mismo pago, sin fusionar**. Un evento de producción nunca se cruza con sandbox.
6. Mensaje al equipo: `PROVIDER_CONFIRMED`, `OFFICIAL_WEBHOOK`, `test: true` (booleano); el evento Android
   sale como `INCOME_CANDIDATE`.

Junto con las pruebas anteriores (contrato `protocol-fixtures` compartido por Kotlin y firmware, migraciones
de Room, pipeline de Android), cada eslabón queda probado sin credenciales ni red.

## Guía de pruebas físicas

`docs/guia-pruebas-fisicas.md`, nueva sección **3b. Integraciones oficiales** (HYB-01 a HYB-07), también en la
pantalla «Pruebas físicas» de la app. Ningún resultado se marca solo: el técnico registra lo observado. Las
HYB-02 a HYB-07 requieren un backend desplegado y credenciales de prueba; sin eso quedan «Bloqueada».

## Resultados de todas las pruebas automáticas (13/11/2026)

| Conjunto | Resultado real |
|---|---|
| `domain` | 4 pruebas, 0 fallos |
| `payment-engine` | 70 pruebas, 0 fallos |
| `backend` | 22 pruebas, 0 fallos |
| Firmware en host | 101 comprobaciones, 0 fallidas |
| App Android y firmware ESP32-S3 | Compilan y se prueban en CI (GitHub Actions); ver el estado del PR |

## Informe final: qué hay y qué no

### Hecho y probado
- **Canal A (Android):** lector de notificaciones con adaptadores por app; solo «posibles ingresos»; registro
  sin texto de apps financieras sin asignar; WakeLock acotado (fase 2).
- **Modelo normalizado** con canal, proveedor, id externo, estados de verificación separados del anuncio, e
  invariante: un evento Android **nunca** puede ser `PROVIDER_CONFIRMED` (fase 3).
- **Backend de webhooks:** HTTPS, firma, idempotencia por entrega y por pago, reintentos, registro original,
  auditoría, estado por proveedor, sandbox/producción separados, credenciales solo en el servidor (fase 4).
- **Mercado Pago:** firma `x-signature` como el SDK oficial (vector del SDK) y consulta `GET /v1/payments/{id}`
  (fase 5).
- **Unificación:** el celular lee eventos oficiales con un token de solo lectura; duplicados entre canales
  solo con remitente coincidente y candidato único, nunca por importe y hora solos (fase 6).
- **Equipo, voz y panel:** protocolo v2, pantalla y voz según la confianza, ajuste «solo confirmados», panel de
  integraciones que nunca muestra «Activa» sin un webhook real comprobado (fase 7).

### No hecho (y por qué)
- **Izipay:** no hay adaptador. Su IPN está documentada en sus repositorios oficiales, pero no se pudieron
  verificar los campos de importe ni hay afiliación; implementarlo sería inventar.
- **Bancos (BBVA, BCP, Interbank):** sin documentación técnica pública accesible.
- **Consultar todos los Yape/Plin recibidos:** ningún proveedor revisado lo permite. La confirmación oficial
  cubre solo cobros hechos por el canal del proveedor; un yapeo directo al número del comercio sigue siendo
  «posible ingreso».
- **Formatos de Yape/Plin:** no hay muestras reales; no se inventaron.

### Lo que hace falta para usarlo de verdad
1. Desplegar `backend` detrás de un proxy HTTPS con PostgreSQL.
2. Cuenta de Mercado Pago Perú, aplicación en «Tus integraciones», secreto del webhook y access token
   **de prueba** como variables de entorno del servidor; URL del webhook `/webhooks/sandbox/mercado-pago`.
3. Generar un token por celular y cargar su SHA-256 en `PAGOBOX_DEVICE_TOKENS_SANDBOX`.
4. Ejecutar HYB-01 a HYB-07 y, con muestras reales de las apps bancarias, la sección 4 de la guía.
5. Solo después, repetir con credenciales de producción.

### Limitaciones conocidas
- El celular consulta el backend cada 20 s (sin push): la confirmación puede tardar ese intervalo y solo
  mientras el proceso de la app esté vivo.
- La tolerancia de tiempo de la firma de Mercado Pago está desactivada por defecto (la semántica de `ts` en
  reintentos no está documentada); la protección contra repeticiones es la idempotencia por entrega y por pago.
- Reembolsos y contracargos posteriores a `PROVIDER_CONFIRMED` no se modelan.
- Nada se probó con dinero real, credenciales reales ni en un equipo físico.
