package pe.pagobox.app.ui.banks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.ui.components.Pill
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.ui.theme.StatusError
import pe.pagobox.app.ui.theme.StatusNeutral
import pe.pagobox.app.ui.theme.StatusOk
import pe.pagobox.app.ui.theme.StatusWarn
import pe.pagobox.domain.model.CompatibilityStatus

fun CompatibilityStatus.color(): Color = when (this) {
    CompatibilityStatus.VERIFIED_COMPATIBLE, CompatibilityStatus.DEVICE_TESTED -> StatusOk
    CompatibilityStatus.CONFIGURED, CompatibilityStatus.LAB_TESTED, CompatibilityStatus.WAITING_FOR_SAMPLE -> StatusWarn
    CompatibilityStatus.FORMAT_CHANGED, CompatibilityStatus.TEMPORARILY_UNAVAILABLE -> StatusError
    CompatibilityStatus.NOT_CONFIGURED -> StatusNeutral
}

private fun CompatibilityStatus.explanation(): String = when (this) {
    CompatibilityStatus.NOT_CONFIGURED ->
        "No se identificó la app de esta entidad en el celular. Use Diagnóstico de notificaciones para asignarla."
    CompatibilityStatus.WAITING_FOR_SAMPLE ->
        "App identificada. Aún no hay formatos verificados: se guardan muestras anonimizadas y NO se anuncian pagos."
    CompatibilityStatus.CONFIGURED -> "Formatos escritos con muestras reales. Pendiente de pruebas documentadas."
    CompatibilityStatus.LAB_TESTED -> "Formatos probados con muestras reales en pruebas automáticas."
    CompatibilityStatus.DEVICE_TESTED -> "Probado en un celular físico con evidencia documentada."
    CompatibilityStatus.VERIFIED_COMPATIBLE -> "Superó el protocolo completo de pruebas reales."
    CompatibilityStatus.FORMAT_CHANGED ->
        "Llegaron varias notificaciones con un formato desconocido. Solo se anuncia lo reconocido; revise las muestras."
    CompatibilityStatus.TEMPORARILY_UNAVAILABLE -> "Pausada: no se leen ni anuncian sus pagos."
}

/**
 * Registro de compatibilidad por entidad. El estado lo decide la evidencia (código + pruebas),
 * no el usuario: aquí solo se puede pausar una entidad o reiniciar el aviso de formato cambiado.
 */
@Composable
fun BankSourcesScreen(container: AppContainer, onOpenNotificationDiagnostics: () -> Unit) {
    val registry by container.processor.registry.collectAsStateWithLifecycle()
    val untrusted by container.processor.untrustedPackages.collectAsStateWithLifecycle()
    val counts by container.database.notificationSamples().observeCountsBySource().collectAsStateWithLifecycle(initialValue = emptyList())
    val statusRows by container.database.sourceStatus().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val countBySource = counts.associate { it.sourceId to it.count }
    val rowBySource = statusRows.associateBy { it.sourceId }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "PAGOBOX solo anuncia pagos de entidades cuyos formatos se escribieron con notificaciones reales. " +
                    "Una notificación no es una confirmación bancaria: verifique siempre en la app oficial.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenNotificationDiagnostics) { Text("Abrir diagnóstico de notificaciones") }
        }
        items(container.processor.adapters, key = { it.sourceId.name }) { adapter ->
            val id = adapter.sourceId
            val status = registry.statuses[id] ?: CompatibilityStatus.NOT_CONFIGURED
            val packages = registry.packagesOf(id)
            val paused = status == CompatibilityStatus.TEMPORARILY_UNAVAILABLE
            val row = rowBySource[id.name]
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(id.displayName, style = MaterialTheme.typography.titleMedium)
                        Pill(status.label, status.color())
                    }
                    if (packages.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(
                                checked = !paused,
                                onCheckedChange = { on -> scope.launch { container.processor.setTemporarilyUnavailable(id, !on) } },
                            )
                            Text(if (paused) "Pausada" else "Activa", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(status.explanation(), style = MaterialTheme.typography.bodySmall)
                Text(
                    "Paquetes: " + (packages.takeIf { it.isNotEmpty() }?.joinToString() ?: "ninguno"),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Formatos verificados: ${adapter.formats.size} · Muestras guardadas: ${countBySource[id.name] ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if ((row?.consecutiveUnknown ?: 0) > 0) {
                    Text("Formatos desconocidos seguidos: ${row!!.consecutiveUnknown}", style = MaterialTheme.typography.bodySmall, color = StatusWarn)
                }
                if (status == CompatibilityStatus.FORMAT_CHANGED) {
                    OutlinedButton(onClick = { scope.launch { container.processor.clearFormatChanged(id) } }) {
                        Text("Ya revisé el formato")
                    }
                }
            }
        }
        if (untrusted.isNotEmpty()) {
            item {
                SectionCard("Paquetes bloqueados") {
                    Text(
                        "La firma de estas apps ya no coincide con la registrada (reinstalación o app no oficial). " +
                            "No se leen hasta volver a asignarlas: ${untrusted.joinToString()}",
                        color = StatusError,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
