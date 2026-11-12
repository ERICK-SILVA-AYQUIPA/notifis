package pe.pagobox.engine.source

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.parser.AmountParser

/**
 * Regla de reconocimiento de una notificación concreta. El patrón puede usar los
 * grupos con nombre `monto` y `remitente`. Las reglas deben escribirse a partir de
 * notificaciones reales recopiladas con autorización (fase 1), nunca supuestas.
 */
data class NotificationRule(
    val id: String,
    val eventType: EventType,
    val pattern: Regex,
)

/**
 * Adaptador genérico basado en reglas deterministas (sección 2.2): sin IA ni
 * reconocimiento probabilístico. Si ninguna regla coincide, la notificación se rechaza.
 */
class RuleBasedNotificationAdapter(
    override val sourceId: SourceId,
    override val packageNames: Set<String>,
    private val rules: List<NotificationRule>,
    override val compatibility: CompatibilityStatus =
        if (rules.isEmpty()) CompatibilityStatus.NOT_IMPLEMENTED else CompatibilityStatus.IN_TESTING,
) : PaymentSourceAdapter {

    override fun parse(notification: RawNotification): ParseResult {
        if (notification.packageName !in packageNames) {
            return ParseResult.NotRecognized("Paquete no pertenece a ${sourceId.displayName}")
        }
        val text = notification.fullText
        for (rule in rules) {
            val match = rule.pattern.find(text) ?: continue
            val amountText = match.groupOrNull("monto")
            val money = amountText?.let { AmountParser.parse(it) }
            val sender = match.groupOrNull("remitente")?.trim()?.takeIf { it.isNotEmpty() }
            return ParseResult.Parsed(
                ParsedPayment(
                    source = sourceId,
                    eventType = rule.eventType,
                    money = money,
                    senderName = sender,
                    eventTimestamp = notification.postedAt,
                ),
            )
        }
        return ParseResult.NotRecognized("Formato desconocido para ${sourceId.displayName}")
    }

    private fun MatchResult.groupOrNull(name: String): String? =
        try {
            groups[name]?.value
        } catch (_: IllegalArgumentException) {
            null
        }
}
