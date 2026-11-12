package pe.pagobox.app.ui.diagnostics

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.announce.AnnouncementCoordinator
import pe.pagobox.app.ui.components.CheckRow
import pe.pagobox.app.ui.components.Pill
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.ui.components.color
import pe.pagobox.app.ui.components.formatDateTime
import pe.pagobox.app.ui.components.label
import pe.pagobox.app.ui.theme.StatusError
import pe.pagobox.app.ui.theme.StatusNeutral
import pe.pagobox.app.ui.theme.StatusWarn

@SuppressLint("BatteryLife")
@Composable
fun DiagnosticsScreen(container: AppContainer) {
    val report by container.health.report.collectAsStateWithLifecycle(initialValue = null)
    val logs by container.database.diagnosticLogs().observeRecent(100).collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Comprobaciones") {
            val r = report
            if (r == null) {
                Text("Comprobando…")
            } else {
                Pill(r.health.label(), r.health.color())
                Text(r.summary)
                r.checks.forEach { CheckRow(it.title, it.detail, it.status) }
            }
        }
        SectionCard("Pruebas") {
            OutlinedButton(onClick = {
                scope.launch {
                    val p = container.database.voiceProfiles().get() ?: AnnouncementCoordinator.DEFAULT_VOICE
                    if (!container.voice.speak("Prueba de voz de PAGOBOX", p)) container.log.error("Diagnóstico", "La prueba de voz falló")
                }
            }) { Text("Probar voz del celular") }
            OutlinedButton(onClick = {
                scope.launch {
                    if (!container.devices.playTest()) container.log.warn("Diagnóstico", "Prueba del equipo: no conectado")
                }
            }) { Text("Probar equipo PAGOBOX") }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                Text("Acceso a notificaciones")
            }
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            }) { Text("Quitar restricción de batería") }
        }
        SectionCard("Registro técnico") {
            if (logs.isEmpty()) Text("Sin registros.")
            logs.forEach { l ->
                val color = when (l.level) {
                    "ERROR" -> StatusError
                    "WARN" -> StatusWarn
                    else -> StatusNeutral
                }
                Text("${formatDateTime(l.timestamp)} · ${l.component}", style = MaterialTheme.typography.labelSmall, color = color)
                Text(l.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
