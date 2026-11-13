# Fase 5 — Primera integración oficial: Mercado Pago (Yape)

Fecha: 13/11/2026 · Archivos: `backend/.../providers/mercadopago/MercadoPagoSignature.kt`,
`MercadoPagoAdapter.kt`, `Providers.kt`, prueba `MercadoPagoAdapterTest.kt`.

## Qué hace

1. Recibe `POST /webhooks/{sandbox|production}/mercado-pago?data.id=…&type=payment`.
2. Valida `x-signature` exactamente como el validador oficial de los SDK de Mercado Pago:
   manifiesto `id:<data.id>;request-id:<x-request-id>;ts:<ts>;` (omitiendo pares vacíos), HMAC-SHA256 con la clave
   secreta del webhook, comparación en tiempo constante.
3. Exige `data.id` en la query (es lo que la firma protege) y que coincida con el del cuerpo si viene.
4. Ignora lo que no es `type=payment`.
5. **No confía en el cuerpo**: consulta `GET https://api.mercadopago.com/v1/payments/{id}` con el access token del
   servidor y toma de ahí `status`, `status_detail`, `transaction_amount`, `currency_id`, `payment_method_id`,
   `date_approved` y `live_mode` (nombres de campo del `PaymentResponse` del SDK oficial).
6. `approved` → `PROVIDER_CONFIRMED` · `rejected/cancelled/refunded/charged_back` → `REJECTED` · otros → `PENDING_VERIFICATION`.
   `payment_method_id = "yape"` → entidad Yape. Moneda distinta de PEN/USD → sin importe, pendiente (no se inventa).
7. `live_mode` debe coincidir con el entorno de la ruta (`409` si no).
8. HTTP 5xx o sin respuesta → reintentos y luego `503`; `401/403/404` → definitivo (`200`, `FAILED`).

## Pruebas ejecutadas (resultados reales)

| Prueba | Resultado |
|---|---|
| Vector de firma del SDK oficial (`webhook.spec.ts`): secreto, request-id, data.id y ts oficiales; hash calculado aparte con Node.js y Python (idénticos) | ✅ coincide |
| `data.id` en mayúsculas, secreto distinto, cabecera ausente o sin `ts` | ✅ rechazados |
| Omisión de pares vacíos y tolerancia de tiempo | ✅ |
| Extracción: solo pagos con `data.id` en la query, sin discrepancias | ✅ |
| Flujo completo contra un **servidor HTTP local** que responde como `/v1/payments/{id}` con datos de prueba (campos del SDK): aprobado → confirmado; rechazado → rechazado; `live_mode` equivocado → `409`; 500 → `503`; 404 → sin pago; moneda desconocida → pendiente sin importe | ✅ 8/8 |

**No se ha hecho ninguna llamada real a Mercado Pago**: no hay cuenta ni credenciales de prueba. Ningún resultado
de este documento proviene de su API real.

## Lo que falta para la prueba real en sandbox (requiere al comerciante)

1. Cuenta Mercado Pago Perú y una aplicación en «Tus integraciones» (Checkout API).
2. Copiar las **credenciales de prueba** (access token) y configurar el webhook (evento «Pagos») apuntando a
   `https://<dominio>/webhooks/sandbox/mercado-pago`; copiar la clave secreta generada.
3. Desplegar el backend con HTTPS y las variables `PAGOBOX_MERCADO_PAGO_SANDBOX_WEBHOOK_SECRET` y
   `PAGOBOX_MERCADO_PAGO_SANDBOX_ACCESS_TOKEN`.
4. Crear un cobro Yape de prueba (número `111111111`, OTP `123456`, según la documentación) y comprobar en la
   base: `raw_webhook_events` con firma `VALID` y `payment_events` en `PROVIDER_CONFIRMED`.
5. Usar «Simular notificación» del panel de Mercado Pago para probar firma y reintentos.

## Limitaciones

- Solo cobros hechos **a través de Mercado Pago**; no ve yapeos directos a la cuenta Yape del comercio.
- Para cobrar falta el lado del cobro (Checkout API: token Yape + `POST /v1/payments`), que no es parte de
  PAGOBOX hoy. Sin él, la integración solo sirve si el comercio ya cobra con Mercado Pago.
- Las notificaciones de **QR de Mercado Pago no van firmadas** (advertencia del SDK): hoy se rechazan con `401`.
  Para aceptarlas habría que tratarlas como `NOT_SIGNED` y confirmar solo por la API.
- Un reembolso posterior a `PROVIDER_CONFIRMED` no revierte el estado (pendiente).
- Izipay: no implementado. Su firma IPN está documentada en sus repositorios oficiales, pero no se pudo leer la
  estructura de importes de `kr-answer` ni hay afiliación; no se agrega una integración sin esa evidencia.
