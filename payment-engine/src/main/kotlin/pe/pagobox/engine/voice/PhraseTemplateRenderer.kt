package pe.pagobox.engine.voice

import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Genera la frase a anunciar a partir de una plantilla con variables (sección 2.4):
 * {monto}, {monto_hablado}, {moneda}, {banco}, {remitente}, {hora}, {tipo_operacion}.
 *
 * Las partes opcionales van entre corchetes. Si alguna variable de esa parte no tiene
 * valor, se elimina la parte completa:
 *   "¡Atención! [{remitente} te envió ]{monto_hablado} mediante {banco}"
 * Si una variable fuera de corchetes no tiene valor, la plantilla no se usa (devuelve null)
 * para no generar un mensaje incorrecto.
 */
class PhraseTemplateRenderer(
    private val zone: ZoneId = ZoneId.of("America/Lima"),
    private val announceSender: Boolean = true,
) {
    private val variable = Regex("""\{([a-z_]+)\}""")
    private val optional = Regex("""\[([^\[\]]*)\]""")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun variablesFor(event: PaymentEvent): Map<String, String?> = mapOf(
        "monto" to event.money?.let { SpokenAmount.display(it) },
        "monto_hablado" to event.money?.let { SpokenAmount.of(it) },
        "moneda" to event.money?.currency?.let { if (it.code == "PEN") "soles" else "dólares" },
        "banco" to event.source.takeIf { it != SourceId.OTHER }?.displayName,
        "remitente" to event.senderName?.takeIf { announceSender },
        "hora" to timeFormat.format(event.eventTimestamp.atZone(zone)),
        "tipo_operacion" to when (event.eventType) {
            EventType.INCOMING_TRANSFER -> "transferencia recibida"
            else -> null
        },
    )

    fun render(template: String, event: PaymentEvent): String? {
        val values = variablesFor(event)
        val withOptionals = optional.replace(template) { part ->
            val body = part.groupValues[1]
            val names = variable.findAll(body).map { it.groupValues[1] }.toList()
            if (names.all { values[it] != null }) body else ""
        }
        var missing = false
        val rendered = variable.replace(withOptionals) { v ->
            val value = values[v.groupValues[1]]
            if (value == null) missing = true
            value ?: ""
        }
        if (missing) return null
        return rendered.replace(Regex("\\s+"), " ").trim()
    }

    /** Elige la frase: personalizada por entidad, luego la predeterminada. */
    fun phraseFor(event: PaymentEvent, custom: Map<SourceId, String> = emptyMap()): String? {
        custom[event.source]?.let { render(it, event) }?.let { return it }
        return render(DefaultPhrases.forEvent(event), event)
    }
}

object DefaultPhrases {
    const val YAPE = "¡Te yapearon {monto_hablado}!"
    const val PLIN = "¡Recibiste {monto_hablado} por Plin!"
    const val BANK_TRANSFER = "¡Te hicieron una transferencia de {monto_hablado}!"
    const val GENERIC = "¡Recibiste un pago de {monto_hablado}!"
    const val VERIFIED = "¡Pago verificado por {monto_hablado}!"

    fun forEvent(event: PaymentEvent): String = when {
        event.verificationStatus == VerificationStatus.VERIFIED -> VERIFIED
        event.source == SourceId.YAPE -> YAPE
        event.source == SourceId.PLIN -> PLIN
        event.source == SourceId.OTHER -> GENERIC
        else -> BANK_TRANSFER
    }
}
