package pe.pagobox.backend.providers

import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.config.ProviderCredentials
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceId
import java.time.Instant

/** Solicitud HTTP tal como llegó (cabeceras en minúsculas). */
class WebhookRequest(
    val headers: Map<String, String>,
    val query: Map<String, String>,
    val body: ByteArray,
    val receivedAt: Instant,
) {
    fun header(name: String): String? = headers[name.lowercase()]?.trim()?.takeIf { it.isNotEmpty() }
    val bodyText: String get() = body.toString(Charsets.UTF_8)
}

sealed interface SignatureCheck {
    data object Valid : SignatureCheck
    data class Invalid(val reason: String) : SignatureCheck
    /** El proveedor no firma este tipo de notificación: solo vale como aviso; hay que consultar su API. */
    data class NotSigned(val reason: String) : SignatureCheck
}

sealed interface Extraction {
    /** Aviso de un pago: [resourceId] es el identificador del pago en el proveedor. */
    data class Payment(val resourceId: String) : Extraction
    /** Notificación válida pero que no es de un pago (p. ej. otra categoría). */
    data class Ignored(val reason: String) : Extraction
    data class Malformed(val reason: String) : Extraction
}

enum class ProviderPaymentStatus { APPROVED, PENDING, REJECTED }

/** Pago según el proveedor. Solo datos necesarios (privacidad). */
data class ProviderPayment(
    val externalTransactionId: String,
    val money: Money?,
    val status: ProviderPaymentStatus,
    /** Valor original del proveedor, para auditoría (p. ej. "approved"). */
    val providerStatus: String,
    val source: SourceId,
    val occurredAt: Instant?,
    val sender: String? = null,
    val recipient: String? = null,
    /** true si el proveedor declaró que es un pago de producción; null si no lo informa. */
    val liveMode: Boolean? = null,
    /** true si los datos se obtuvieron consultando la API del proveedor con credenciales privadas. */
    val fetchedFromApi: Boolean,
)

/** El proveedor no respondió o respondió con error: se reintenta (lo pide el propio proveedor reenviando). */
class ProviderUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Adaptador de un proveedor oficial. No conoce HTTP ni base de datos. */
interface ProviderAdapter {
    val id: ProviderId
    /** Claves de credencial imprescindibles; sin ellas el proveedor se considera «sin configurar». */
    val requiredCredentials: Set<String>

    /** Identificador de la entrega (para no procesar dos veces la misma), si el proveedor lo envía. */
    fun deliveryId(request: WebhookRequest): String?

    fun verifySignature(request: WebhookRequest, credentials: ProviderCredentials, toleranceSeconds: Long?): SignatureCheck

    fun extract(request: WebhookRequest): Extraction

    /** Obtiene el pago con la evidencia más fuerte disponible. Puede lanzar [ProviderUnavailableException]. */
    fun resolve(payment: Extraction.Payment, request: WebhookRequest, credentials: ProviderCredentials, environment: DeployEnvironment): ProviderPayment
}

/** El proveedor rechazó la consulta de forma definitiva (credencial inválida, pago inexistente): no se reintenta. */
class ProviderRejectedException(message: String) : Exception(message)
