package pe.pagobox.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.pagobox.app.AppContainer
import pe.pagobox.app.ui.components.LabeledValue
import pe.pagobox.app.ui.components.Pill
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.ui.components.announcementLabel
import pe.pagobox.app.ui.components.formatDateTime
import pe.pagobox.app.ui.components.formatMoney
import pe.pagobox.app.ui.components.channelLabel
import pe.pagobox.app.ui.components.verificationLabel
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceId

@Composable
fun EventDetailScreen(container: AppContainer, eventId: String) {
    val event by container.payments.observe(eventId).collectAsStateWithLifecycle(initialValue = null)
    val history by container.database.announcementHistory().observeFor(eventId).collectAsStateWithLifecycle(initialValue = emptyList())
    val e = event ?: run {
        Text("Evento no encontrado.", Modifier.padding(16.dp))
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(formatMoney(e.amountCents, e.currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Pill(verificationLabel(e.verificationStatus), verificationColor(e.verificationStatus))
        SectionCard("Información disponible") {
            // Solo se muestran datos extraídos; lo que no vino en la notificación se indica como tal.
            LabeledValue("Entidad", SourceId.valueOf(e.sourceId).displayName)
            LabeledValue("Canal de detección", channelLabel(e.sourceChannel))
            e.provider?.let { LabeledValue("Proveedor", runCatching { ProviderId.valueOf(it).displayName }.getOrDefault(it)) }
            e.sourceApplication?.let { LabeledValue("App de origen", it) }
            e.externalTransactionId?.let { LabeledValue("ID del proveedor", it) }
            e.recipient?.let { LabeledValue("Destinatario", it) }
            e.duplicateOf?.let { LabeledValue("Mismo pago que", it) }
            e.possibleMatchOf?.let { LabeledValue("Posible mismo pago que", "$it (sin evidencia suficiente para unirlos)") }
            if (e.testMode) LabeledValue("Entorno", "Prueba (sandbox): no es un pago real")
            LabeledValue("Remitente", e.senderName ?: "No disponible")
            LabeledValue("Fecha del pago", formatDateTime(e.eventTimestamp))
            LabeledValue("Detectado", formatDateTime(e.detectedTimestamp))
            LabeledValue(
                "Verificación",
                if (e.verificationStatus == "PROVIDER_CONFIRMED") "Confirmado por el proveedor oficial" else "${verificationLabel(e.verificationStatus)} (no verificado por un proveedor)",
            )
            LabeledValue("Anuncio", announcementLabel(e.announcementStatus))
            LabeledValue("Motivo", e.stateReason)
            LabeledValue("Identificador", "${e.eventId} (secuencia ${e.sequence})")
        }
        SectionCard("Entrega y reproducción") {
            if (history.isEmpty()) Text("Sin intentos de anuncio.")
            history.forEach { h ->
                val where = if (h.output == "DEVICE") "Equipo" else "Celular"
                val result = when (h.result) {
                    "PLAYED" -> "Reproducido"
                    "DELIVERED" -> "Entregado"
                    else -> "Falló"
                }
                Text("${formatDateTime(h.timestamp)} · $where · $result", style = MaterialTheme.typography.bodyMedium)
                Text("«${h.phrase}»" + (h.detail?.let { "\n$it" } ?: ""), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
