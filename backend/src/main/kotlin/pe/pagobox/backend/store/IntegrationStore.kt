package pe.pagobox.backend.store

import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.ProviderId
import java.time.Instant

enum class SignatureStatus { VALID, INVALID, NOT_SIGNED }

enum class ProcessingStatus {
    /** Registrado, aún sin procesar. */
    RECEIVED,
    /** Firma inválida: no se procesa. */
    REJECTED_SIGNATURE,
    /** No es un aviso de pago o no tiene el formato esperado. */
    IGNORED,
    /** El proveedor no respondió; se espera el reintento del proveedor. */
    RETRY_PENDING,
    /** El pago se registró o actualizó. */
    PROCESSED,
    /** Error de datos (p. ej. entorno equivocado): no se reintenta. */
    FAILED,
}

/** Evento original recibido (registro para auditoría e idempotencia de la entrega). */
data class RawWebhookEvent(
    val id: String,
    val provider: ProviderId,
    val environment: DeployEnvironment,
    val deliveryKey: String,
    val receivedAt: Instant,
    /** Solo cabeceras de una lista permitida (nunca Authorization ni cookies). */
    val headers: Map<String, String>,
    /** Cuerpo original; null cuando la firma es inválida (no se guarda contenido no autenticado). */
    val body: String?,
    val bodySha256: String,
    val signatureStatus: SignatureStatus,
    val processingStatus: ProcessingStatus,
    val detail: String?,
    val paymentEventId: String? = null,
    val attempts: Int = 1,
)

data class StoredPaymentEvent(val environment: DeployEnvironment, val event: PaymentEvent, val providerStatus: String)

data class AuditEntry(
    val at: Instant,
    val environment: DeployEnvironment?,
    val provider: ProviderId?,
    val action: String,
    val detail: String,
)

data class ProviderHealth(
    val provider: ProviderId,
    val environment: DeployEnvironment,
    val lastCommunicationAt: Instant?,
    val lastEventAt: Instant?,
    val lastError: String?,
    val lastErrorAt: Instant?,
)

interface IntegrationStore {
    /** Inserta el evento original; devuelve false si esa entrega ya estaba registrada. */
    fun insertRaw(event: RawWebhookEvent): Boolean
    fun rawByDeliveryKey(provider: ProviderId, environment: DeployEnvironment, deliveryKey: String): RawWebhookEvent?
    fun updateRaw(id: String, status: ProcessingStatus, detail: String?, paymentEventId: String?, incrementAttempts: Boolean = false)

    fun paymentByExternalId(provider: ProviderId, environment: DeployEnvironment, externalId: String): StoredPaymentEvent?
    /** Inserta o actualiza por (proveedor, entorno, id externo): idempotente. */
    fun savePayment(environment: DeployEnvironment, event: PaymentEvent, providerStatus: String, rawEventId: String)
    fun paymentsSince(environment: DeployEnvironment, since: Instant, limit: Int): List<StoredPaymentEvent>

    fun audit(entry: AuditEntry)
    fun auditTrail(limit: Int): List<AuditEntry>

    fun recordCommunication(provider: ProviderId, environment: DeployEnvironment, at: Instant, eventAt: Instant?, error: String?)
    fun providerHealth(provider: ProviderId, environment: DeployEnvironment): ProviderHealth
}
