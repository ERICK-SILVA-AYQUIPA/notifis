package pe.pagobox.engine

import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.source.NotificationRule
import pe.pagobox.engine.source.RuleBasedNotificationAdapter
import java.time.Instant

/**
 * Adaptador con un formato FICTICIO, solo para probar el motor.
 * No representa el formato real de ninguna entidad.
 */
object TestFixtures {
    const val TEST_PACKAGE = "test.pagobox.fakewallet"

    val testAdapter = RuleBasedNotificationAdapter(
        sourceId = SourceId.OTHER,
        packageNames = setOf(TEST_PACKAGE),
        rules = listOf(
            NotificationRule(
                id = "test-ingreso",
                eventType = EventType.INCOMING_TRANSFER,
                pattern = Regex("""PRUEBA: (?<remitente>[^\n]+?) te envió (?<monto>S/\.? ?[\d,.]+)"""),
            ),
            NotificationRule(
                id = "test-ingreso-sin-monto",
                eventType = EventType.INCOMING_TRANSFER,
                pattern = Regex("""PRUEBA: recibiste un pago"""),
            ),
            NotificationRule(
                id = "test-egreso",
                eventType = EventType.OUTGOING_TRANSFER,
                pattern = Regex("""PRUEBA: enviaste (?<monto>S/\.? ?[\d,.]+)"""),
            ),
            NotificationRule(
                id = "test-solicitud",
                eventType = EventType.PAYMENT_REQUEST,
                pattern = Regex("""PRUEBA: .+ te solicita (?<monto>S/\.? ?[\d,.]+)"""),
            ),
        ),
    )

    fun event(
        id: String,
        sequence: Long,
        amount: String = "20.00",
        source: SourceId = SourceId.YAPE,
        sender: String? = null,
        at: Instant = Instant.parse("2026-11-12T20:30:00Z"),
    ) = PaymentEvent(
        eventId = id,
        sequence = sequence,
        source = source,
        eventType = EventType.INCOMING_TRANSFER,
        money = Money.pen(amount),
        senderName = sender,
        eventTimestamp = at,
        detectedTimestamp = at,
        state = EventState.INCOME_CANDIDATE,
        verificationStatus = VerificationStatus.DETECTED,
        announcementStatus = AnnouncementStatus.PENDING,
        rawNotificationHash = "hash-$id",
    )
}
