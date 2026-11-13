package pe.pagobox.engine.verification

import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceChannel
import pe.pagobox.domain.model.VerificationStateMachine
import pe.pagobox.domain.model.VerificationStatus

/**
 * Evidencia de que un proveedor autorizado confirmó un pago. La construye **solo el backend**, después de:
 * - validar la firma del webhook con el secreto del proveedor ([signatureVerified]), y/o
 * - consultar el pago en la API del proveedor con credenciales privadas ([fetchedFromProviderApi]).
 * El celular nunca construye esta evidencia: lo que envía la app no es prueba de pago.
 */
data class ProviderConfirmation(
    val provider: ProviderId,
    val externalTransactionId: String,
    val money: Money,
    /** Estado del pago según el proveedor (p. ej. "approved", "PAID"). */
    val providerStatus: String,
    val signatureVerified: Boolean,
    val fetchedFromProviderApi: Boolean,
)

/**
 * Único punto que puede marcar un evento como PROVIDER_CONFIRMED. Exige que el evento sea del canal
 * oficial, del mismo proveedor y pago, por el mismo importe, y que haya evidencia criptográfica o
 * una consulta autenticada al proveedor. Una notificación Android nunca se confirma por esta vía.
 */
class VerificationEngine {
    fun confirm(event: PaymentEvent, proof: ProviderConfirmation): PaymentEvent {
        require(event.sourceChannel == SourceChannel.OFFICIAL_WEBHOOK) { "Solo un evento oficial puede confirmarse" }
        require(event.provider == proof.provider) { "El proveedor no coincide" }
        require(proof.externalTransactionId.isNotBlank() && event.externalTransactionId == proof.externalTransactionId) {
            "El identificador del pago no coincide"
        }
        val money = event.money
        require(money != null && money.normalized == proof.money.normalized && money.currency == proof.money.currency) {
            "El importe no coincide con el informado por el proveedor"
        }
        require(proof.signatureVerified || proof.fetchedFromProviderApi) { "Sin firma válida ni consulta al proveedor" }
        val next = VerificationStateMachine.transition(event.sourceChannel, event.verificationStatus, VerificationStatus.PROVIDER_CONFIRMED)
        return event.copy(verificationStatus = next)
    }
}
