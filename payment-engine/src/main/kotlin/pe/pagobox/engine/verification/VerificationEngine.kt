package pe.pagobox.engine.verification

import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.EventStateMachine
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.VerificationStatus

/** Confirmación recibida de una integración oficial autorizada (API o webhook). */
data class OfficialConfirmation(
    val providerId: String,
    val externalReference: String,
)

/**
 * Único punto que puede marcar un evento como VERIFIED, y solo con una confirmación
 * oficial. Una notificación Android por sí sola nunca verifica un pago.
 */
class VerificationEngine {
    fun verify(event: PaymentEvent, confirmation: OfficialConfirmation): PaymentEvent {
        require(confirmation.externalReference.isNotBlank()) { "Confirmación sin referencia" }
        val newState = EventStateMachine.transition(event.state, EventState.VERIFIED)
        return event.copy(state = newState, verificationStatus = VerificationStatus.VERIFIED)
    }
}
