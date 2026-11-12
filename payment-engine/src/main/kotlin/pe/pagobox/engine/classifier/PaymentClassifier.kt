package pe.pagobox.engine.classifier

import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.EventType
import pe.pagobox.engine.source.ParsedPayment
import java.time.Duration
import java.time.Instant

/**
 * Decide si un evento extraído es un posible ingreso. Nunca produce VERIFIED.
 * - Egresos, solicitudes de dinero y publicidad: REJECTED.
 * - Notificaciones antiguas (re-publicadas): REJECTED.
 * - Ingreso sin importe: PENDING_REVIEW (no se inventa el monto).
 */
class PaymentClassifier(
    private val maxNotificationAge: Duration = Duration.ofMinutes(10),
) {
    data class Classification(val state: EventState, val reason: String)

    fun classify(payment: ParsedPayment, now: Instant): Classification {
        if (payment.eventType != EventType.INCOMING_TRANSFER) {
            return Classification(EventState.REJECTED, "No es un ingreso (${payment.eventType})")
        }
        if (Duration.between(payment.eventTimestamp, now) > maxNotificationAge) {
            return Classification(EventState.REJECTED, "Notificación antigua")
        }
        if (payment.money == null) {
            return Classification(EventState.PENDING_REVIEW, "Ingreso sin importe identificable")
        }
        if (payment.money.amount.signum() == 0) {
            return Classification(EventState.PENDING_REVIEW, "Importe cero")
        }
        return Classification(EventState.INCOME_CANDIDATE, "Ingreso detectado por notificación")
    }
}
