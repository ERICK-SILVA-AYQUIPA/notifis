package pe.pagobox.backend.providers.mercadopago

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.config.ProviderCredentials
import pe.pagobox.backend.providers.Extraction
import pe.pagobox.backend.providers.ProviderAdapter
import pe.pagobox.backend.providers.ProviderPayment
import pe.pagobox.backend.providers.ProviderPaymentStatus
import pe.pagobox.backend.providers.ProviderRejectedException
import pe.pagobox.backend.providers.ProviderUnavailableException
import pe.pagobox.backend.providers.SignatureCheck
import pe.pagobox.backend.providers.WebhookRequest
import pe.pagobox.domain.model.Currency
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceId
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Mercado Pago (Perú), notificaciones Webhooks de pagos — p. ej. cobros con Yape mediante Checkout API.
 *
 * Fuentes: documentación de Webhooks y de Yape en mercadopago.com.pe/developers, y SDK oficiales
 * (validador de firma y `GET /v1/payments/{id}`). Ver docs/hibrido/proveedores-canal-b.md.
 *
 * Alcance real: solo pagos **creados a través de Mercado Pago**. No permite ver los yapeos que llegan
 * directamente a la cuenta Yape del comerciante.
 *
 * Flujo: firma `x-signature` válida → `GET /v1/payments/{data.id}` con el access token (nunca se confía
 * en el cuerpo del webhook) → estado, importe, moneda, medio de pago y `live_mode`.
 *
 * Credenciales (variables de entorno del servidor): `WEBHOOK_SECRET` (clave secreta del webhook en
 * «Tus integraciones») y `ACCESS_TOKEN` (de prueba en sandbox, productivo en producción).
 * Opcional: `API_BASE_URL` (solo para pruebas automáticas contra un servidor local).
 */
class MercadoPagoAdapter(
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
    private val now: () -> Instant = Instant::now,
) : ProviderAdapter {
    override val id = ProviderId.MERCADO_PAGO
    override val requiredCredentials = setOf(WEBHOOK_SECRET, ACCESS_TOKEN)

    override fun deliveryId(request: WebhookRequest): String? = request.header("x-request-id")

    override fun verifySignature(request: WebhookRequest, credentials: ProviderCredentials, toleranceSeconds: Long?): SignatureCheck =
        MercadoPagoSignature.verify(
            xSignature = request.header("x-signature"),
            xRequestId = request.header("x-request-id"),
            dataId = request.query["data.id"],
            secret = credentials[WEBHOOK_SECRET]!!,
            now = now(),
            toleranceSeconds = toleranceSeconds,
        )

    override fun extract(request: WebhookRequest): Extraction {
        val body = runCatching { Json.parseToJsonElement(request.bodyText).jsonObject }.getOrNull()
        val type = request.query["type"] ?: body?.string("type")
        if (type != "payment") return Extraction.Ignored("Tipo de notificación no soportado: ${type ?: "sin tipo"}")
        // El id debe venir en la query: es el valor que la firma protege.
        val queryId = request.query["data.id"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return Extraction.Malformed("Falta data.id en la query")
        val bodyId = (body?.get("data") as? JsonObject)?.string("id")
        if (bodyId != null && bodyId != queryId) return Extraction.Malformed("data.id de la query y del cuerpo no coinciden")
        if (!queryId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return Extraction.Malformed("data.id con caracteres inválidos")
        return Extraction.Payment(queryId)
    }

    override fun resolve(payment: Extraction.Payment, request: WebhookRequest, credentials: ProviderCredentials, environment: DeployEnvironment): ProviderPayment {
        val base = (credentials[API_BASE_URL] ?: DEFAULT_API).trimEnd('/')
        val url = "$base/v1/payments/${URLEncoder.encode(payment.resourceId, Charsets.UTF_8)}"
        val response = try {
            http.send(
                HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer ${credentials[ACCESS_TOKEN]}")
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        } catch (e: java.io.IOException) {
            throw ProviderUnavailableException("Sin respuesta de Mercado Pago (${e.javaClass.simpleName})", e)
        }
        when (response.statusCode()) {
            200 -> Unit
            401, 403 -> throw ProviderRejectedException("Mercado Pago rechazó el access token (HTTP ${response.statusCode()})")
            404 -> throw ProviderRejectedException("Pago ${payment.resourceId} no existe para esta cuenta (HTTP 404)")
            else -> throw ProviderUnavailableException("Mercado Pago respondió HTTP ${response.statusCode()}")
        }
        val json = runCatching { Json.parseToJsonElement(response.body()).jsonObject }
            .getOrElse { throw ProviderUnavailableException("Respuesta de Mercado Pago no es JSON") }
        return parsePayment(json)
    }

    internal fun parsePayment(json: JsonObject): ProviderPayment {
        val idValue = json.string("id") ?: throw ProviderUnavailableException("Pago sin id")
        val status = json.string("status") ?: "desconocido"
        val currency = when (json.string("currency_id")) {
            "PEN" -> Currency.PEN
            "USD" -> Currency.USD
            else -> null
        }
        val amount = json.string("transaction_amount")?.toBigDecimalOrNull()
        // Sin moneda conocida o con más de 2 decimales no se inventa un importe: queda pendiente.
        val money = if (currency != null && amount != null && amount.signum() >= 0 && amount.stripTrailingZeros().scale() <= 2) {
            Money(amount.setScale(2, RoundingMode.UNNECESSARY), currency)
        } else {
            null
        }
        return ProviderPayment(
            externalTransactionId = idValue,
            money = money,
            status = when (status) {
                "approved" -> ProviderPaymentStatus.APPROVED
                "rejected", "cancelled", "refunded", "charged_back" -> ProviderPaymentStatus.REJECTED
                else -> ProviderPaymentStatus.PENDING
            },
            providerStatus = listOfNotNull(status, json.string("status_detail")).joinToString("/"),
            source = if (json.string("payment_method_id") == "yape") SourceId.YAPE else SourceId.OTHER,
            occurredAt = (json.string("date_approved") ?: json.string("date_created"))
                ?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
            liveMode = (json["live_mode"] as? JsonPrimitive)?.booleanOrNull,
            fetchedFromApi = true,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.content != "null" }?.content

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()

    companion object {
        const val WEBHOOK_SECRET = "WEBHOOK_SECRET"
        const val ACCESS_TOKEN = "ACCESS_TOKEN"
        const val API_BASE_URL = "API_BASE_URL"
        const val DEFAULT_API = "https://api.mercadopago.com"
    }
}
