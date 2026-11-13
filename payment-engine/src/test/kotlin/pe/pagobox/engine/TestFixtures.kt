package pe.pagobox.engine

import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceChannel
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.engine.source.BankNotificationAdapter
import pe.pagobox.engine.source.NotificationFormat
import java.time.Instant

/** Adaptador sintético: formatos FICTICIOS, solo para probar el motor. No representan a ninguna entidad. */
class SyntheticAdapter(formats: List<NotificationFormat>) : BankNotificationAdapter(
    sourceId = SourceId.OTHER,
    officialPackages = setOf(TestFixtures.TEST_PACKAGE),
    formats = formats,
    declaredStatus = CompatibilityStatus.CONFIGURED,
)

object TestFixtures {
    const val TEST_PACKAGE = "test.pagobox.fakewallet"

    private fun synthetic(id: String, type: EventType, pattern: String, sample: String, amount: String?) =
        NotificationFormat(
            id = id,
            eventType = type,
            pattern = Regex(pattern),
            anonymizedSample = sample,
            expectedAmount = amount,
            provenance = "Sintético para pruebas del motor",
            synthetic = true,
        )

    val formats = listOf(
        synthetic(
            "test-ingreso", EventType.INCOMING_TRANSFER,
            """PRUEBA: (?<remitente>[^\n]+?) te envió (?<monto>(?:S/\.?|PEN|US\$|USD) ?[\d,.]+)""",
            "Billetera de prueba \n PRUEBA: <NOMBRE> te envió S/ 99.99", "99.99",
        ),
        synthetic("test-ingreso-sin-monto", EventType.INCOMING_TRANSFER, """PRUEBA: recibiste un pago""", "PRUEBA: recibiste un pago", null),
        synthetic(
            "test-egreso", EventType.OUTGOING_TRANSFER, """PRUEBA: enviaste (?<monto>S/\.? ?[\d,.]+)""",
            "PRUEBA: enviaste S/ 99.99", "99.99",
        ),
        synthetic(
            "test-solicitud", EventType.PAYMENT_REQUEST, """PRUEBA: .+ te solicita (?<monto>S/\.? ?[\d,.]+)""",
            "PRUEBA: <NOMBRE> te solicita S/ 99.99", "99.99",
        ),
        // Regla deliberadamente mal declarada: dice ingreso pero el texto es un egreso.
        synthetic(
            "test-regla-erronea", EventType.INCOMING_TRANSFER, """PRUEBA-MAL: pagaste (?<monto>S/ ?[\d,.]+)""",
            "PRUEBA-MAL: pagaste S/ 99.99", "99.99",
        ),
    )

    val testAdapter = SyntheticAdapter(formats)

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
        sourceChannel = SourceChannel.ANDROID_NOTIFICATION,
        sourceApplication = "app.de.prueba",
        source = source,
        eventType = EventType.INCOMING_TRANSFER,
        money = Money.pen(amount),
        senderName = sender,
        eventTimestamp = at,
        detectedTimestamp = at,
        verificationStatus = VerificationStatus.INCOME_CANDIDATE,
        announcementStatus = AnnouncementStatus.PENDING,
        rawEventReference = "hash-$id",
    )

    /** Evento del canal oficial (Canal B) aún no confirmado. Datos de prueba, no de un proveedor real. */
    fun officialEvent(
        id: String,
        externalId: String = "TEST-1",
        amount: String = "25.00",
        provider: ProviderId = ProviderId.MERCADO_PAGO,
    ) = PaymentEvent(
        eventId = id,
        sequence = 0,
        sourceChannel = SourceChannel.OFFICIAL_WEBHOOK,
        provider = provider,
        source = SourceId.YAPE,
        externalTransactionId = externalId,
        eventType = EventType.INCOMING_TRANSFER,
        money = Money.pen(amount),
        senderName = null,
        eventTimestamp = Instant.parse("2026-11-12T20:30:00Z"),
        detectedTimestamp = Instant.parse("2026-11-12T20:30:01Z"),
        verificationStatus = VerificationStatus.DETECTED,
        announcementStatus = AnnouncementStatus.PENDING,
        rawEventReference = "raw-$id",
    )
}
