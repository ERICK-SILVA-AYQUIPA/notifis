# Fase 4 — Backend seguro para webhooks

Fecha: 13/11/2026 · Nuevo módulo `backend/` (Kotlin + Ktor, JVM 17). Reutiliza `domain` y `payment-engine`
(el mismo `VerificationEngine`), así que la regla «Android nunca se confirma» es la misma en ambos lados.
Detalle de uso y variables: [backend/README.md](../../backend/README.md).

## Archivos

| Archivo | Qué hace |
|---|---|
| `backend/build.gradle.kts`, `settings.gradle.kts` | Módulo nuevo, incluido siempre (no necesita Android SDK); CI lo prueba con `./gradlew test` |
| `config/BackendConfig.kt` | Entornos `sandbox`/`production`, credenciales por variables de entorno `PAGOBOX_<PROVEEDOR>_<ENTORNO>_<CLAVE>`; `toString` nunca muestra valores |
| `providers/ProviderAdapter.kt` | Contrato de proveedor: id de entrega, firma, extracción, consulta del pago; errores reintentables y definitivos |
| `ingest/WebhookIngestion.kt` | Flujo completo con los 9 controles (ver README) |
| `ingest/Retry.kt` | Reintentos con espera exponencial |
| `store/JdbcIntegrationStore.kt` | `raw_webhook_events`, `payment_events`, `audit_log`, `provider_status`; SQL válido en SQLite y PostgreSQL; `UNIQUE` para idempotencia |
| `security/Crypto.kt` | HMAC-SHA256, SHA-256, comparación en tiempo constante |
| `http/Server.kt`, `Main.kt` | `POST /webhooks/{entorno}/{proveedor}`, `GET /health`; exige HTTPS (vía proxy) y limita el cuerpo |

## Requisitos cubiertos

| Requisito | Implementación | Prueba |
|---|---|---|
| Endpoints HTTPS | Rechazo `403` si no llegó por HTTPS | `HttpRoutesTest` |
| Firma criptográfica | Por proveedor; inválida → `401`, sin consultar al proveedor y sin guardar el cuerpo | `firma invalida se rechaza…` |
| Validación del proveedor | Proveedor y entorno de la ruta deben existir y tener credenciales | `entorno deshabilitado, proveedor desconocido…` |
| Idempotencia | Por entrega y por pago (`UNIQUE`) | `la misma entrega repetida…`, `entregas distintas del mismo pago…` |
| Protección contra repeticiones | Entrega ya atendida → `200` sin reprocesar; tolerancia de tiempo opcional en la firma | ídem + `MercadoPagoAdapterTest` |
| Registro de eventos originales | `raw_webhook_events` con cuerpo, huella, cabeceras permitidas, estado y número de intentos | varios |
| Reintentos | 3 intentos con espera exponencial; luego `503` para que el proveedor reenvíe; el reenvío se procesa | `proveedor caido se reintenta…` |
| Manejo de errores | `400` malformado, `401` firma, `404`, `409` entorno equivocado, `413`, `503`; mensajes sin detalles internos | varios |
| Auditoría | `audit_log` por cada decisión | `pago aprobado…` |
| Sandbox / producción | Rutas, secretos y datos separados; `live_mode` debe coincidir | `sandbox y produccion estan separados` |
| Credenciales solo en backend | Variables de entorno; nada en repo/APK | revisión |
| No confiar en el cliente Android | No hay endpoint para que el celular «confirme» pagos | revisión |
| Estado para el panel | `provider_status`: última comunicación, último evento, último error | `estado del proveedor…` |

## Pruebas ejecutadas

`./gradlew test`: **domain 4, payment-engine 61, backend 18 — 83 pruebas, 0 fallos** (JDK 21 local).
Las pruebas de este módulo usan un proveedor **ficticio de prueba** (`TestProvider`, solo en `src/test`) para
ejercitar el mecanismo genérico; no representa a ningún proveedor real.

## Limitaciones

- No está desplegado: falta un servidor con dominio y certificado TLS, y PostgreSQL para producción.
- Un solo proceso (bloqueo en memoria sobre la conexión JDBC). Para varias instancias se necesita un pool y
  confiar solo en las restricciones `UNIQUE` de PostgreSQL.
- Tolerancia de la marca de tiempo desactivada por defecto: Mercado Pago reenvía durante horas y no documenta
  si renueva `ts` en cada reintento; la protección principal es la idempotencia + la consulta a su API.
- Aún no hay canal backend → app (Fase 6) ni panel de integraciones en la app (Fase 7).
