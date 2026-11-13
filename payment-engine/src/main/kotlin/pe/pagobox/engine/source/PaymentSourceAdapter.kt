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
    /** Identificador del formato que reconoció la notificación (trazabilidad). */
    val formatId: String = "",
)

sealed interface ParseResult {
    data class Parsed(val payment: ParsedPayment) : ParseResult
    /** La notificación es de la entidad, pero su formato no está registrado. */
    data class UnknownFormat(val reason: String) : ParseResult
}

/**
 * Adaptador por entidad (sección 2.1). Cada banco/billetera tiene el suyo:
 * no se asume que todas las entidades usan el mismo formato.
 */
interface PaymentSourceAdapter {
    val sourceId: SourceId
    /** Paquetes oficiales conocidos en código. Vacío si aún no se confirmaron con evidencia. */
    val officialPackages: Set<String>
    /** Formatos escritos a partir de muestras reales anonimizadas. */
    val formats: List<NotificationFormat>
    /** Estado declarado en código según la evidencia disponible (ver CompatibilityRegistry). */
    val declaredStatus: CompatibilityStatus
    fun parse(notification: RawNotification): ParseResult
}
