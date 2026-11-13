# PAGOBOX — Backend de integraciones (Canal B)

Recibe webhooks de proveedores oficiales, verifica su firma, consulta el pago en la API del proveedor y
registra eventos normalizados (`PaymentEvent`, canal `OFFICIAL_WEBHOOK`). Es el **único** lugar donde puede
nacer un `PROVIDER_CONFIRMED`. El celular nunca envía pruebas de pago.

## Ejecutar

```bash
./gradlew :backend:test     # pruebas (no necesita credenciales ni red)
./gradlew :backend:run      # servidor en :8080
```

## Variables de entorno (secretos solo aquí, nunca en el repositorio ni en el APK)

| Variable | Uso | Por defecto |
|---|---|---|
| `PAGOBOX_ENVIRONMENTS` | Entornos habilitados: `sandbox`, `production` o ambos separados por coma | `sandbox` |
| `PAGOBOX_DATABASE_URL` | JDBC. SQLite para desarrollo; PostgreSQL en producción | `jdbc:sqlite:pagobox-backend.db` |
| `PAGOBOX_PORT` | Puerto HTTP interno | `8080` |
| `PAGOBOX_REQUIRE_HTTPS` | Rechaza webhooks que no llegaron por HTTPS (según `X-Forwarded-Proto` del proxy) | `true` |
| `PAGOBOX_MAX_BODY_BYTES` | Tamaño máximo del cuerpo | `65536` |
| `PAGOBOX_SIGNATURE_TOLERANCE_SECONDS` | Antigüedad máxima de la marca de tiempo firmada | sin límite (ver limitaciones) |
| `PAGOBOX_MERCADO_PAGO_SANDBOX_WEBHOOK_SECRET` | Clave secreta del webhook (Tus integraciones → Webhooks), cuenta de prueba | — |
| `PAGOBOX_MERCADO_PAGO_SANDBOX_ACCESS_TOKEN` | Access token **de prueba** | — |
| `PAGOBOX_MERCADO_PAGO_PRODUCTION_*` | Lo mismo para producción | — |

Sin las dos credenciales de un proveedor/entorno, sus webhooks responden `503 provider not configured` y se
registra en auditoría. No hay credenciales por defecto.

## Endpoints

| Método y ruta | Descripción |
|---|---|
| `POST /webhooks/{sandbox|production}/{proveedor}` | Webhook, p. ej. `/webhooks/sandbox/mercado-pago` |
| `GET /health` | Estado del proceso |

TLS: el servidor se publica detrás de un proxy inverso con HTTPS (Caddy, Nginx, balanceador del proveedor de
nube) que agrega `X-Forwarded-Proto: https`.

## Controles por cada webhook

1. Entorno habilitado y proveedor conocido (`404` si no).
2. Credenciales configuradas (`503` si no).
3. Tamaño del cuerpo (`413`).
4. **Firma criptográfica** con el secreto del proveedor; inválida → `401`, se guarda solo la huella del
   cuerpo (no el contenido) y se audita.
5. **Idempotencia por entrega** (`x-request-id` o huella del cuerpo): una entrega ya atendida responde `200`
   sin reprocesar (protección contra repeticiones).
6. **Consulta al proveedor** con credenciales privadas y **reintentos** con espera exponencial (3 intentos).
   Si sigue fallando → `503` para que el proveedor reenvíe; la entrega queda `RETRY_PENDING`. Rechazo
   definitivo (token inválido, pago inexistente) → `200` y `FAILED`, para no recibir reenvíos sin fin.
7. **Separación sandbox/producción**: rutas, secretos y datos distintos; además, si el proveedor declara
   `live_mode` distinto al entorno → `409`.
8. Motor central: `VerificationEngine.confirm` exige canal oficial, mismo proveedor, id e importe, y firma o
   consulta a la API. **Idempotencia por pago** (`UNIQUE(provider, environment, external_id)`).
9. Registro del **evento original** (`raw_webhook_events`), del pago (`payment_events`), **auditoría**
   (`audit_log`) y **estado del proveedor** (`provider_status`: última comunicación, último evento, último error).

Cabeceras guardadas: solo `content-type`, `user-agent`, `x-request-id`, `x-signature`. Nunca `Authorization`,
cookies ni IP.
