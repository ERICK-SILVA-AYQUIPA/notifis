package pe.pagobox.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.data.preferences.AppSettings
import pe.pagobox.app.data.repositories.DailySummary
import pe.pagobox.app.diagnostics.HealthReport
import pe.pagobox.app.ui.components.CheckRow
import pe.pagobox.app.ui.components.LabeledValue
import pe.pagobox.app.ui.components.Pill
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.ui.components.color
import pe.pagobox.app.ui.components.formatMoney
import pe.pagobox.app.ui.components.formatTime
import pe.pagobox.app.ui.components.label
import pe.pagobox.domain.model.SourceId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DashboardViewModel(private val c: AppContainer) : ViewModel() {
    val summary = c.payments.observeToday().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val health = c.health.report.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val lastError = c.announcements.lastError

    fun mute(minutes: Long?) {
        viewModelScope.launch {
            c.settings.setMutedUntil(minutes?.let { Instant.now().plusSeconds(it * 60) })
            c.announcements.notifyNewIncome()
        }
    }
}

@Composable
fun DashboardScreen(container: AppContainer, onOpenEvent: (String) -> Unit, onOpenDiagnostics: () -> Unit) {
    val vm: DashboardViewModel = viewModel { DashboardViewModel(container) }
    val summary by vm.summary.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lastError by vm.lastError.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            settings.businessName.ifBlank { "PAGOBOX" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        HealthCard(health, onOpenDiagnostics)
        summary?.let { TotalsCard(it, onOpenEvent) }
        MuteCard(settings, vm::mute)
        lastError?.let {
            SectionCard("Último problema de anuncio") { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun HealthCard(report: HealthReport?, onOpenDiagnostics: () -> Unit) {
    SectionCard("Estado del sistema") {
        if (report == null) {
            Text("Comprobando…")
            return@SectionCard
        }
        Pill(report.health.label(), report.health.color())
        Text(report.summary, style = MaterialTheme.typography.bodyMedium)
        report.checks.filter { it.title in MAIN_CHECKS }.forEach { CheckRow(it.title, it.detail, it.status) }
        OutlinedButton(onClick = onOpenDiagnostics) { Text("Ver diagnóstico completo") }
    }
}

private val MAIN_CHECKS = setOf("Servicio de lectura", "Equipo PAGOBOX", "Voz en español", "Entidades supervisadas")

@Composable
private fun TotalsCard(s: DailySummary, onOpenEvent: (String) -> Unit) {
    SectionCard("Hoy") {
        // Totales separados para evitar confusiones contables (sección 8).
        LabeledValue("Posibles ingresos (notificaciones)", formatMoney(s.detectedTotalCents, "PEN"))
        LabeledValue("Confirmados por proveedor", formatMoney(s.verifiedTotalCents, "PEN"))
        LabeledValue("Eventos registrados", s.eventCount.toString())
        LabeledValue("Pagos confirmados", s.verifiedCount.toString())
        Text(
            "Los ingresos detectados provienen de notificaciones y no son una confirmación bancaria.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val last = s.lastPayment
        if (last != null) {
            Text("Último pago", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(formatMoney(last.amountCents, last.currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(SourceId.valueOf(last.sourceId).displayName, last.senderName, formatTime(last.eventTimestamp))
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = { onOpenEvent(last.eventId) }) { Text("Detalle") }
            }
        } else {
            Text("Aún no hay pagos hoy.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MuteCard(settings: AppSettings, onMute: (Long?) -> Unit) {
    val now = Instant.now()
    val muted = settings.mutedUntil?.isAfter(now) == true
    SectionCard("Anuncios") {
        if (muted) {
            val until = DateTimeFormatter.ofPattern("HH:mm").format(settings.mutedUntil!!.atZone(ZoneId.of("America/Lima")))
            Text("Silenciado hasta las $until. Los pagos se guardan y se anunciarán al terminar.")
            Button(onClick = { onMute(null) }) { Text("Reactivar anuncios") }
        } else {
            Text("Silenciar temporalmente:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L, 30L, 60L).forEach { m -> OutlinedButton(onClick = { onMute(m) }) { Text("$m min") } }
            }
        }
    }
}
