package pe.pagobox.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.pagobox.app.AppContainer
import pe.pagobox.app.data.local.PaymentEventEntity
import pe.pagobox.app.ui.components.Pill
import pe.pagobox.app.ui.components.formatDateTime
import pe.pagobox.app.ui.components.formatMoney
import pe.pagobox.app.ui.components.announcementLabel
import pe.pagobox.app.ui.components.channelLabel
import pe.pagobox.app.ui.components.verificationLabel
import pe.pagobox.app.ui.theme.StatusError
import pe.pagobox.app.ui.theme.StatusNeutral
import pe.pagobox.app.ui.theme.StatusOk
import pe.pagobox.app.ui.theme.StatusWarn
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceId

@Composable
fun HistoryScreen(container: AppContainer, onOpenEvent: (String) -> Unit) {
    val events by container.payments.observeRecent().collectAsStateWithLifecycle(initialValue = emptyList())
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Todavía no hay eventos registrados.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(events, key = { it.eventId }) { e -> EventRow(e, onClick = { onOpenEvent(e.eventId) }) }
    }
}

@Composable
private fun EventRow(e: PaymentEventEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatMoney(e.amountCents, e.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        SourceId.valueOf(e.sourceId).displayName,
                        e.provider?.let { runCatching { ProviderId.valueOf(it).displayName }.getOrDefault(it) },
                        e.senderName,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(formatDateTime(e.eventTimestamp) + " · " + channelLabel(e.sourceChannel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(verificationLabel(e.verificationStatus), verificationColor(e.verificationStatus))
                Pill(announcementLabel(e.announcementStatus), if (e.announcementStatus == "ANNOUNCED") StatusOk else StatusNeutral)
            }
        }
    }
}

fun verificationColor(status: String) = when (status) {
    "PROVIDER_CONFIRMED" -> StatusOk
    "INCOME_CANDIDATE", "PENDING_VERIFICATION" -> StatusWarn
    "REJECTED" -> StatusError
    else -> StatusNeutral
}
