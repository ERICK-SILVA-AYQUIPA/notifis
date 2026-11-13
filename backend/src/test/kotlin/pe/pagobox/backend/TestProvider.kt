package pe.pagobox.backend

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.config.ProviderCredentials
import pe.pagobox.backend.providers.Extraction
import pe.pagobox.backend.providers.ProviderAdapter
import pe.pagobox.backend.providers.ProviderPayment
import pe.pagobox.backend.providers.ProviderPaymentStatus
import pe.pagobox.backend.providers.ProviderUnavailableException
import pe.pagobox.backend.providers.SignatureCheck
import pe.pagobox.backend.providers.WebhookRequest
import pe.pagobox.backend.security.Crypto
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceId

/**
 * Proveedor **ficticio, solo para pruebas** del mecanismo genérico (firma, idempotencia, reintentos,
 * entornos). No representa el formato de ningún proveedor real. Ocupa el identificador IZIPAY solo
 * porque el modelo exige un ProviderId; no se usa fuera de las pruebas.
 *
 * Firma: cabecera `x-test-signature` = HMAC-SHA256(SECRET, cuerpo).
 * Cuerpo: {"paymentId": "..."}; el «API» del proveedor es el mapa [api].
 */
class TestProvider : ProviderAdapter {
    override val id = ProviderId.IZIPAY
    override val requiredCredentials = setOf("SECRET")

    data class ApiPayment(val amount: String, val status: String, val live: Boolean)
    val api = mutableMapOf<String, ApiPayment>()
    var failuresBeforeSuccess = 0
    var calls = 0

    override fun deliveryId(request: WebhookRequest): String? = request.header("x-test-delivery")

    override fun verifySignature(request: WebhookRequest, credentials: ProviderCredentials, toleranceSeconds: Long?): SignatureCheck {
        val received = request.header("x-test-signature") ?: return SignatureCheck.Invalid("sin firma")
        val expected = Crypto.hmacSha256Hex(credentials["SECRET"]!!, request.bodyText)
        return if (Crypto.constantTimeEquals(expected, received)) SignatureCheck.Valid else SignatureCheck.Invalid("firma no coincide")
    }

    override fun extract(request: WebhookRequest): Extraction {
        val json = runCatching { Json.parseToJsonElement(request.bodyText).jsonObject }.getOrNull()
            ?: return Extraction.Malformed("JSON inválido")
        val id = json["paymentId"]?.jsonPrimitive?.content ?: return Extraction.Ignored("no es un pago")
        return Extraction.Payment(id)
    }

    override fun resolve(payment: Extraction.Payment, request: WebhookRequest, credentials: ProviderCredentials, environment: DeployEnvironment): ProviderPayment {
        calls++
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess--
            throw ProviderUnavailableException("HTTP 500 simulado")
        }
        val p = api[payment.resourceId] ?: throw ProviderUnavailableException("pago no encontrado")
        return ProviderPayment(
            externalTransactionId = payment.resourceId,
            money = Money.pen(p.amount),
            status = when (p.status) {
                "approved" -> ProviderPaymentStatus.APPROVED
                "rejected" -> ProviderPaymentStatus.REJECTED
                else -> ProviderPaymentStatus.PENDING
            },
            providerStatus = p.status,
            source = SourceId.YAPE,
            occurredAt = null,
            liveMode = p.live,
            fetchedFromApi = true,
        )
    }
}
