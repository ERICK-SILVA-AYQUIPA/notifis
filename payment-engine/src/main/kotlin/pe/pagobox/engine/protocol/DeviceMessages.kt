package pe.pagobox.engine.protocol

import pe.pagobox.domain.model.PaymentEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Mensajes estructurados Android <-> dispositivo (sección 4). Ver docs/protocolo.md.
 * Un ACK confirma la recepción técnica del mensaje, no el ingreso del dinero.
 */
object DeviceMessages {
    private val isoOffset = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun paymentEvent(event: PaymentEvent, zone: ZoneId = ZoneId.of("America/Lima")): String {
        val fields = linkedMapOf<String, Any?>(
            "type" to "PAYMENT_EVENT",
            "eventId" to event.eventId,
            "sequence" to event.sequence,
            "source" to event.source.name,
            "eventType" to event.eventType.name,
            "amount" to event.money?.normalized?.toPlainString()?.let { RawJson(it) },
            "currency" to event.money?.currency?.code,
            "sender" to event.senderName,
            "timestamp" to isoOffset.format(event.eventTimestamp.atZone(zone)),
            "verificationStatus" to event.verificationStatus.name,
            "announcementStatus" to event.announcementStatus.name,
        )
        return toJson(fields)
    }

    /**
     * Prueba de entrega completa (fase 7): recorre BLE, ACK, audio y reproducción, pero el
     * equipo la muestra como «PRUEBA» y no la registra como pago ni avanza la secuencia.
     */
    fun testEvent(testId: String): String = toJson(linkedMapOf("type" to "TEST_EVENT", "testId" to testId))

    private class RawJson(val value: String)

    private fun toJson(fields: Map<String, Any?>): String =
        fields.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            "\"${escape(k)}\":" + when (v) {
                null -> "null"
                is RawJson -> v.value
                is Number -> v.toString()
                else -> "\"${escape(v.toString())}\""
            }
        }

    fun escape(s: String): String = buildString {
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
}
