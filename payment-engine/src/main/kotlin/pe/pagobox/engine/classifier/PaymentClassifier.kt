package pe.pagobox.engine.classifier

import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.EventType
import pe.pagobox.engine.source.ParsedPayment
import java.text.Normalizer
import java.time.Duration
import java.time.Instant

/**
 * Decide si un evento extraído es un posible ingreso. Nunca produce VERIFIED.
 * - Egresos, solicitudes de dinero y publicidad: REJECTED.
 * - Validación independiente de la regla: si el texto contiene términos de egreso o de
 *   solicitud, no se acepta como ingreso aunque la regla lo haya declarado así (defensa
 *   ante una regla mal escrita).
 * - Notificaciones antiguas o con fecha futura: REJECTED.
 * - Ingreso sin importe: PENDING_REVIEW (no se inventa el monto).
 */
class PaymentClassifier(
    private val maxNotificationAge: Duration = Duration.ofMinutes(10),
    private val maxClockSkew: Duration = Duration.ofMinutes(2),
) {
    data class Classification(val state: EventState, val reason: String)

    fun classify(payment: ParsedPayment, rawText: String, now: Instant): Classification {
        if (payment.eventType != EventType.INCOMING_TRANSFER) {
            return Classification(EventState.REJECTED, "No es un ingreso (${payment.eventType})")
        }
        val normalized = normalize(rawText)
        EGRESS_TERMS.firstOrNull { it in normalized }?.let {
            return Classification(EventState.REJECTED, "El texto indica un egreso («$it»)")
        }
        REQUEST_TERMS.firstOrNull { it in normalized }?.let {
            return Classification(EventState.REJECTED, "El texto indica una solicitud de dinero («$it»)")
        }
        val age = Duration.between(payment.eventTimestamp, now)
        if (age > maxNotificationAge) {
            return Classification(EventState.REJECTED, "Notificación antigua (${age.toMinutes()} min)")
        }
        if (age.isNegative && age.abs() > maxClockSkew) {
            return Classification(EventState.REJECTED, "Fecha de la notificación en el futuro")
        }
        if (payment.money == null) {
            return Classification(EventState.PENDING_REVIEW, "Ingreso sin importe identificable")
        }
        if (payment.money.amount.signum() == 0) {
            return Classification(EventState.PENDING_REVIEW, "Importe cero")
        }
        return Classification(EventState.INCOME_CANDIDATE, "Ingreso detectado por notificación (formato ${payment.formatId})")
    }

    companion object {
        /** Términos genéricos del español que indican salida de dinero (no son formatos de una entidad). */
        val EGRESS_TERMS = listOf(
            "enviaste", "pagaste", "transferiste", "yapeaste", "plineaste", "retiraste", "retiro de",
            "consumo", "compraste", "cargo en", "se debito", "debito de", "realizaste un pago", "realizaste una transferencia",
        )

        /** Términos que indican una solicitud o cobro pendiente. */
        val REQUEST_TERMS = listOf("te solicita", "solicitud de", "te pide", "te pidio", "cobro pendiente", "pendiente de pago")

        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }
}
