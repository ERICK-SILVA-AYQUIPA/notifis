package pe.pagobox.engine

import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.classifier.PaymentClassifier
import pe.pagobox.engine.deduplication.DeduplicationEngine
import pe.pagobox.engine.source.ParseResult
import pe.pagobox.engine.source.RawNotification
import pe.pagobox.engine.source.SourcePackageRegistry
import java.time.Clock

sealed interface PipelineResult {
    /** Paquete no supervisado: se ignora sin registrar nada. */
    data object Ignored : PipelineResult
    data class Duplicate(val fingerprint: String) : PipelineResult
    data class Rejected(val reason: String) : PipelineResult
    /** Evento creado (INCOME_CANDIDATE o PENDING_REVIEW). Solo INCOME_CANDIDATE se anuncia. */
    data class Created(val event: PaymentEvent, val reason: String) : PipelineResult
}

/**
 * Flujo del Payment Core: registro de paquetes -> adaptador -> deduplicación -> clasificación.
 * No conoce Android ni la interfaz (sección 17: no mezclar reconocimiento con UI).
 */
class PaymentPipeline(
    private val registry: SourcePackageRegistry,
    private val deduplication: DeduplicationEngine,
    private val classifier: PaymentClassifier = PaymentClassifier(),
    private val clock: Clock = Clock.systemUTC(),
    private val nextSequence: () -> Long,
    private val newEventId: (Long) -> String = { "evt_%06d".format(it) },
) {
    fun process(raw: RawNotification): PipelineResult {
        val adapter = registry.adapterFor(raw.packageName) ?: return PipelineResult.Ignored
        val now = clock.instant()

        val parsed = when (val result = adapter.parse(raw)) {
            is ParseResult.NotRecognized -> return PipelineResult.Rejected(result.reason)
            is ParseResult.Parsed -> result.payment
        }

        if (!deduplication.registerIfNew(raw, now)) {
            return PipelineResult.Duplicate(deduplication.fingerprint(raw))
        }

        val classification = classifier.classify(parsed, now)
        if (classification.state == EventState.REJECTED) {
            return PipelineResult.Rejected(classification.reason)
        }

        val sequence = nextSequence()
        val event = PaymentEvent(
            eventId = newEventId(sequence),
            sequence = sequence,
            source = parsed.source,
            eventType = parsed.eventType,
            money = parsed.money,
            senderName = parsed.senderName,
            eventTimestamp = parsed.eventTimestamp,
            detectedTimestamp = now,
            state = classification.state,
            verificationStatus = VerificationStatus.DETECTED,
            announcementStatus = if (classification.state == EventState.INCOME_CANDIDATE) {
                AnnouncementStatus.PENDING
            } else {
                AnnouncementStatus.SKIPPED
            },
            rawNotificationHash = deduplication.fingerprint(raw),
        )
        return PipelineResult.Created(event, classification.reason)
    }
}
