package pe.pagobox.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.BuildConfig
import pe.pagobox.app.data.preferences.AnnouncementOutput
import pe.pagobox.app.data.preferences.AppSettings
import pe.pagobox.app.ui.components.SectionCard

/** Menú de configuración: accesos a las demás secciones y preferencias generales. */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBanks: () -> Unit,
    onVoice: () -> Unit,
    onDiagnostics: () -> Unit,
    onBusiness: () -> Unit,
    onNotificationDiagnostics: () -> Unit,
    onPhysicalTests: () -> Unit,
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    var retention by remember(settings.retentionDays) { mutableFloatStateOf(settings.retentionDays.toFloat()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard {
            MenuItem("Negocio", "Nombre y datos del establecimiento", onBusiness)
            MenuItem("Bancos", "Estado de compatibilidad por entidad", onBanks)
            MenuItem("Voz", "Voces, velocidad y frases", onVoice)
            MenuItem("Diagnóstico", "Estado del sistema y registro de eventos", onDiagnostics)
            MenuItem("Diagnóstico de notificaciones", "Identificar apps bancarias y guardar muestras anonimizadas", onNotificationDiagnostics)
            MenuItem("Pruebas físicas", "Protocolo de pruebas en celular y equipo reales", onPhysicalTests)
        }
        SectionCard("Dónde se anuncian los pagos") {
            AnnouncementOutput.entries.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().selectable(settings.output == o) { scope.launch { container.settings.setOutput(o) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(settings.output == o, onClick = null)
                    Text(o.label, Modifier.padding(start = 8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Si el equipo no responde, anunciar en el celular", Modifier.weight(1f))
                Switch(settings.phoneFallback, { scope.launch { container.settings.setPhoneFallback(it) } })
            }
        }
        SectionCard("Privacidad y seguridad") {
            Text(
                "PAGOBOX no guarda el texto completo de las notificaciones, no almacena credenciales bancarias " +
                    "ni accede a operaciones privadas. El historial se conserva en este celular.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Conservar historial: ${retention.toInt()} días")
            Slider(
                retention, { retention = it }, valueRange = 7f..365f,
                onValueChangeFinished = { scope.launch { container.settings.setRetentionDays(retention.toInt()) } },
            )
        }
        Text("PAGOBOX ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MenuItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
