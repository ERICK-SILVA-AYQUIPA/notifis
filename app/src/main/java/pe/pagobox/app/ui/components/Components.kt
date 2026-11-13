package pe.pagobox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.pagobox.app.diagnostics.CheckStatus
import pe.pagobox.app.ui.theme.StatusError
import pe.pagobox.app.ui.theme.StatusNeutral
import pe.pagobox.app.ui.theme.StatusOk
import pe.pagobox.app.ui.theme.StatusWarn
import pe.pagobox.domain.model.SystemHealth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val LIMA: ZoneId = ZoneId.of("America/Lima")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

fun formatTime(epochMs: Long): String = timeFmt.format(Instant.ofEpochMilli(epochMs).atZone(LIMA))
fun formatDateTime(epochMs: Long): String = dateTimeFmt.format(Instant.ofEpochMilli(epochMs).atZone(LIMA))

fun formatMoney(cents: Long?, currency: String?): String {
    if (cents == null) return "Importe no disponible"
    val symbol = if (currency == "USD") "US$" else "S/"
    return "$symbol " + String.format(Locale.US, "%,.2f", cents / 100.0)
}

fun CheckStatus.color(): Color = when (this) {
    CheckStatus.OK -> StatusOk
    CheckStatus.WARNING -> StatusWarn
    CheckStatus.ERROR -> StatusError
    CheckStatus.NOT_APPLICABLE -> StatusNeutral
}

fun SystemHealth.color(): Color = when (this) {
    SystemHealth.OPERATIONAL -> StatusOk
    SystemHealth.DEGRADED -> StatusWarn
    SystemHealth.DISCONNECTED, SystemHealth.UNSUPERVISED -> StatusError
}

fun SystemHealth.label(): String = when (this) {
    SystemHealth.OPERATIONAL -> "Operativo"
    SystemHealth.DEGRADED -> "Degradado"
    SystemHealth.DISCONNECTED -> "Desconectado"
    SystemHealth.UNSUPERVISED -> "Sin supervisión"
}

/** Etiqueta del estado de verificación (independiente del anuncio). */
fun verificationLabel(status: String): String =
    runCatching { pe.pagobox.domain.model.VerificationStatus.valueOf(status).label }.getOrDefault(status)

fun channelLabel(channel: String): String =
    runCatching { pe.pagobox.domain.model.SourceChannel.valueOf(channel).label }.getOrDefault(channel)

fun announcementLabel(status: String): String = when (status) {
    "PENDING" -> "Anuncio pendiente"
    "DELIVERED" -> "Entregado al equipo"
    "ANNOUNCED" -> "Anunciado"
    "FAILED" -> "Anuncio fallido"
    "SKIPPED" -> "Sin anuncio"
    else -> status
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(color, CircleShape))
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

@Composable
fun CheckRow(title: String, detail: String, status: CheckStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        StatusDot(status.color())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
