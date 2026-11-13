package pe.pagobox.engine.correlation

import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.SourceChannel
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import java.text.Normalizer
import java.time.Duration

/** Resultado de comparar un evento oficial con los posibles ingresos Android. */
sealed interface Correlation {
    /** Evidencia suficiente: el evento Android pasa a DUPLICATE del oficial. */
    data class SamePayment(val androidEventId: String, val evidence: List<String>) : Correlation
    /** Coincidencia parcial: se relacionan, pero **no** se fusionan. */
    data class PossibleMatch(val androidEventIds: List<String>, val reason: String) : Correlation
    data object NoMatch : Correlation
}

/**
 * Detecta duplicados entre canales sin fusionar por casualidad.
 *
 * Condiciones mínimas para que un evento Android sea **candidato** (todas):
 * - Android en INCOME_CANDIDATE y oficial del canal OFFICIAL_WEBHOOK;
 * - misma entidad conocida (Yape con Yape; «Otra entidad» nunca coincide);
 * - mismo importe exacto y misma moneda;
 * - diferencia de hora ≤ [window].
 *
 * Fusión automática (SamePayment) solo si además:
 * - hay **exactamente un** candidato (si hay dos pagos iguales en la ventana, es ambiguo), y
 * - existe **evidencia adicional independiente**: el remitente coincide en ambos (normalizado).
 *   Importe + hora aproximada nunca bastan por sí solos.
 * Si el remitente falta en alguno, o hay varios candidatos → PossibleMatch (sin fusionar).
 * Si el remitente existe en ambos y es distinto → no es el mismo pago.
 */
class CrossChannelCorrelator(private val window: Duration = Duration.ofMinutes(3)) {

    fun correlate(official: PaymentEvent, androidRecent: List<PaymentEvent>): Correlation {
        if (official.sourceChannel != SourceChannel.OFFICIAL_WEBHOOK) return Correlation.NoMatch
        val money = official.money ?: return Correlation.NoMatch
        if (official.source == SourceId.OTHER) return Correlation.NoMatch

        val candidates = androidRecent.filter { a ->
            a.sourceChannel == SourceChannel.ANDROID_NOTIFICATION &&
                a.verificationStatus == VerificationStatus.INCOME_CANDIDATE &&
                a.testMode == official.testMode &&
                a.source == official.source &&
                a.money != null && a.money!!.currency == money.currency && a.money!!.normalized == money.normalized &&
                Duration.between(a.eventTimestamp, official.eventTimestamp).abs() <= window
        }.filterNot { a ->
            val x = normalize(a.senderName)
            val y = normalize(official.senderName)
            x != null && y != null && x != y
        }
        if (candidates.isEmpty()) return Correlation.NoMatch

        val officialSender = normalize(official.senderName)
        val withSameSender = candidates.filter { officialSender != null && normalize(it.senderName) == officialSender }
        if (candidates.size == 1 && withSameSender.size == 1) {
            return Correlation.SamePayment(
                candidates.single().eventId,
                listOf("misma entidad", "mismo importe", "hora dentro de ${window.toMinutes()} min", "mismo remitente", "único candidato"),
            )
        }
        val reason = when {
            candidates.size > 1 -> "Hay ${candidates.size} posibles ingresos iguales en la ventana: ambiguo"
            else -> "Mismo importe, entidad y hora, pero sin remitente en ambos canales para confirmarlo"
        }
        return Correlation.PossibleMatch(candidates.map { it.eventId }, reason)
    }

    private fun normalize(name: String?): String? = name
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "") }
        ?.lowercase()?.replace(Regex("[^a-z0-9 ]"), " ")?.replace(Regex("\\s+"), " ")?.trim()
        ?.takeIf { it.isNotEmpty() }
}
