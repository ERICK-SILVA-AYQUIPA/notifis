# Canal B — Investigación de proveedores oficiales

Fecha de consulta: 13/11/2026. Solo se registra lo que se leyó en fuentes oficiales (páginas del proveedor o
código de sus repositorios oficiales en GitHub). Lo que no se pudo leer se marca como **no verificado**.
Ninguna de estas integraciones está implementada ni contratada.

## Pregunta central

> ¿El proveedor permite **cobrar** con Yape/Plin, o permite **consultar todos los pagos recibidos** en la
> cuenta del comerciante?

| Proveedor | Cobrar con Yape | Cobrar con Plin | Consultar **todos** los Yape/Plin recibidos en la cuenta del comercio |
|---|---|---|---|
| Mercado Pago | Sí (Checkout API, `payment_method_id: "yape"`) | No documentado | **No.** Solo pagos creados con su API (`/v1/payments`) |
| Izipay | Sí («Pago con Yape») | Sí («Plin Interbank») | **No documentado.** Solo transacciones creadas en su pasarela (IPN por pago) |
| BBVA Perú (APIs empresa) | API QR («cobra con cualquier billetera») | API QR | **No documentado.** Ofrece «API Notificaciones» de movimientos bancarios, sin detalle técnico público |
| BCP / Yape, Interbank | No verificado | No verificado | No verificado (páginas no accesibles desde este entorno) |

**Conclusión:** con lo publicado hoy, la confirmación oficial solo cubre los cobros que el cliente paga
**a través del canal del proveedor** (checkout, link de pago o QR del proveedor). Un yapeo directo al número
personal del comerciante no tiene confirmación oficial disponible y queda en Canal A como posible ingreso.

## 1. Mercado Pago — Yape en Checkout API

Fuentes:
- https://www.mercadopago.com.pe/developers/en/docs/checkout-api-payments/integration-configuration/yape
- https://www.mercadopago.com.pe/developers/es/docs/your-integrations/notifications/webhooks
- SDK oficial `mercadopago/sdk-nodejs`, `src/utils/webhook/index.ts` (commit `cdfecc3`, 10/11/2026) y
  `mercadopago/sdk-python`, `mercadopago/webhook/validator.py`

**Qué permite:** cobrar a un cliente que tiene Yape. Flujo:
1. El cliente escribe su número de celular y el **OTP de 6 dígitos que ve en su app Yape**.
2. Se genera un token de un solo uso: `POST https://api.mercadopago.com/platforms/pci/yape/v1/payment?public_key=…`
   con `{phoneNumber, otp, requestId}` (o `MercadoPago.js`).
3. El servidor crea el pago: `POST https://api.mercadopago.com/v1/payments` con el token,
   `transaction_amount`, `installments: 1`, `payment_method_id: "yape"`, `payer.email` y el *access token*.
4. Estados posibles: `approved` o `rejected` (se procesa como débito).

**Qué no permite:** ver los yapeos que llegan directamente a la cuenta Yape del comerciante. El OTP lo ingresa
el **pagador**; PAGOBOX jamás debe pedirlo al comerciante.

**Requisitos y credenciales:** cuenta de Mercado Pago Perú y una aplicación en «Tus integraciones». *Public key*
(frontend) y *access token* (solo backend). No hay sandbox separado: se usan las **credenciales de prueba de la
cuenta productiva**. Números de prueba `111111111`–`111111118` con OTP `123456` (`111111111` aprueba, el resto
devuelve rechazos específicos).

**Límites:** el pagador tiene un tope configurado en su app Yape: S/ 500, S/ 900 o S/ 2000.

**Costos:** no publicados en las páginas leídas. Pendiente de consultar en la cuenta del comercio.

**Webhooks:**
- Se configuran en «Tus integraciones → Webhooks»; al guardar se genera una **clave secreta por aplicación**
  (sin caducidad; se puede restablecer).
- Respuesta esperada: HTTP 200 o 201 en menos de 22 s. Si no, reintenta cada 15 min; tras el tercer intento
  espacia los envíos, pero sigue enviando.
- Firma (según el validador oficial del SDK): encabezado `x-signature: ts=<segundos>,v1=<hex>`; manifiesto
  `id:<data.id>;request-id:<x-request-id>;ts:<ts>;` (se **omite** cada par cuyo valor falte);
  `HMAC-SHA256(secreto, manifiesto)` en hexadecimal; comparación en tiempo constante; tolerancia de tiempo
  opcional (el SDK sugiere 300 s contra repeticiones).
- **Las notificaciones de pagos con código QR no van firmadas** (advertencia del SDK oficial): para esas hay que
  confirmar consultando `GET /v1/payments/{id}` con el *access token*.
- Recomendación oficial: tras recibir la notificación, consultar el recurso (`GET /v1/payments/{id}`). PAGOBOX
  hará siempre esa consulta antes de marcar `PROVIDER_CONFIRMED`.

## 2. Izipay (grupo Interbank)

Fuentes:
- https://developers.izipay.pe/ (portada; las páginas de producto, credenciales y notificaciones
  **no se pudieron leer** desde este entorno)
- Repositorios oficiales `izipay-pe/Embedded-PaymentForm-Php` (`keys.example.php`, `ipn.php`, `README.md`) y
  `izipay-pe/Server-IPN-Php`

**Qué permite (verificado en la portada):** pasarela con «Pago con tarjeta, Yape, Plin - Interbank, Apple Pay,
Link de Pago», «Pago con QR», «Dispersión de fondos»; SDK Web, Android, iOS y React Native; modos pop-up embebido
y redirección; «Ambiente sandbox disponible 24/7».

**Qué no se pudo confirmar:** que exista una API para listar todos los Yape/Plin recibidos fuera de la pasarela
(no aparece en nada de lo leído); requisitos de afiliación (RUC, contrato); comisiones (hay un tarifario en
`https://www.izipay.pe/pdf/tarifario`, no leído).

**Integración técnica (según los repositorios oficiales):**
- API REST V4 (plataforma Lyra «micuentaweb»): `POST https://api.micuentaweb.pe/api-payment/V4/Charge/CreatePayment`
  con autenticación *Basic* `usuario:contraseña` → devuelve `formToken`.
- Credenciales (test y producción por separado): identificador de tienda, contraseña, clave pública y
  clave HMAC-SHA-256.
- IPN: `POST` del servidor de Izipay al comercio con `kr-answer` (JSON), `kr-hash`, `kr-hash-algorithm=sha256_hmac`,
  `kr-hash-key=password`, `kr-answer-type=V4/Payment`.
- Firma de la IPN: `HMAC-SHA256(contraseña, kr-answer)` en hexadecimal, comparado con `kr-hash`.
  (La clave HMAC-SHA-256 se usa para el retorno del navegador, **no** para la IPN.)
- Datos útiles en `kr-answer`: `orderStatus` (`PAID`/`UNPAID`), `orderDetails.orderId`, `transactions[0].uuid`.
- URL de la IPN: Backoffice Vendedor → Configuración → Reglas de notificación → «URL de notificación al final del pago».
- Política de reintentos de la IPN: **no verificada**.

## 3. Bancos

- **BBVA Perú** (https://www.bbva.pe/empresas/apis-bbva.html): API QR, API Notificaciones («Automatiza las
  notificaciones de tus movimientos bancarios»), API Pay («Consulta tus saldos», «Revisa tus operaciones en tiempo
  real»), Recaudos, Dispersión, Pago de Servicios, Open HR. Acceso solo por formulario de contacto comercial. No
  hay documentación técnica pública ni mención de Yape/Plin. Es el candidato más cercano a «consultar
  movimientos» con autorización, pero requiere ser cliente empresa y negociar el acceso.
- **BCP (Open Economy / Yape), Interbank, BCRP (interoperabilidad):** sus páginas devolvieron error o bloqueo
  anti-bots desde este entorno. **No verificado**; queda pendiente revisarlas desde un navegador.

## 4. Recomendación para la Fase 5

Empezar por **Mercado Pago (Yape)** en modo prueba: credenciales de prueba autoservicio, números de prueba
oficiales y validador de firma publicado en su SDK. Izipay es la segunda opción (cubre Yape **y** Plin), pero
requiere afiliación comercial para obtener credenciales.

Para hacerlo de verdad hace falta que el comerciante:
1. Cree (o use) una cuenta de Mercado Pago Perú y una aplicación en «Tus integraciones».
2. Configure el secreto del webhook y la URL pública HTTPS del backend.
3. Entregue las credenciales **de prueba** solo como variables de entorno del servidor (nunca en el repositorio).

Sin esos pasos, la Fase 5 se limita a código probado con firmas generadas en las pruebas, y el panel mostrará
la integración como «No conectada».
