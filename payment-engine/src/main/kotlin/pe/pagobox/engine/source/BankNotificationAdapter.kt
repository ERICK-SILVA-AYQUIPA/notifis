package pe.pagobox.engine.source

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.parser.AmountParser

/**
 * Adaptador basado en reglas deterministas (sección 2.2): sin IA ni OCR.
 * Recorre los formatos registrados de la entidad; si ninguno coincide, devuelve
 * UnknownFormat para que la app guarde una muestra anonimizada.
 */
abstract class BankNotificationAdapter(
    final override val sourceId: SourceId,
    final override val officialPackages: Set<String>,
    final override val formats: List<NotificationFormat>,
    final override val declaredStatus: CompatibilityStatus,
) : PaymentSourceAdapter {

    init {
        val ids = formats.map { it.id }
        require(ids.size == ids.toSet().size) { "Formatos repetidos en ${sourceId.displayName}" }
        require(declaredStatus != CompatibilityStatus.CONFIGURED || formats.isNotEmpty()) {
            "${sourceId.displayName} no puede declararse CONFIGURED sin formatos reales"
        }
    }

    override fun parse(notification: RawNotification): ParseResult {
        val text = notification.fullText
        for (format in formats) {
            val match = format.pattern.find(text) ?: continue
            val amountText = match.groupOrNull("monto")
            val money = amountText?.let { AmountParser.parse(it) }
            val sender = match.groupOrNull("remitente")?.trim()?.takeIf { it.isNotEmpty() }
            return ParseResult.Parsed(
                ParsedPayment(
                    source = sourceId,
                    eventType = format.eventType,
                    money = money,
                    senderName = sender,
                    eventTimestamp = notification.whenMillis
                        ?.takeIf { it > 0 }
                        ?.let { java.time.Instant.ofEpochMilli(it) }
                        ?: notification.postedAt,
                    formatId = format.id,
                ),
            )
        }
        return ParseResult.UnknownFormat(
            if (formats.isEmpty()) {
                "${sourceId.displayName}: aún no hay formatos registrados"
            } else {
                "${sourceId.displayName}: formato desconocido"
            },
        )
    }

    private fun MatchResult.groupOrNull(name: String): String? =
        try {
            groups[name]?.value
        } catch (_: IllegalArgumentException) {
            null
        }
}
