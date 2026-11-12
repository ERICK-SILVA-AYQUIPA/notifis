package pe.pagobox.engine.source

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.SourceId
import java.time.Instant

/** Datos extraídos de una notificación. Los campos ausentes quedan en null: no se inventan. */
data class ParsedPayment(
    val source: SourceId,
    val eventType: EventType,
    val money: Money?,
    val senderName: String?,
    val eventTimestamp: Instant,
)

sealed interface ParseResult {
    data class Parsed(val payment: ParsedPayment) : ParseResult
    data class NotRecognized(val reason: String) : ParseResult
}

/**
 * Adaptador por entidad (sección 2.1). Cada banco/billetera tiene el suyo:
 * no se asume que todas las entidades usan el mismo formato.
 */
interface PaymentSourceAdapter {
    val sourceId: SourceId
    val packageNames: Set<String>
    val compatibility: CompatibilityStatus
    fun parse(notification: RawNotification): ParseResult
}
