package pe.pagobox.app.ui.banks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.data.local.PaymentSourceEntity
import pe.pagobox.app.ui.components.Pill
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.ui.theme.StatusNeutral
import pe.pagobox.app.ui.theme.StatusOk
import pe.pagobox.app.ui.theme.StatusWarn
import pe.pagobox.domain.model.CompatibilityStatus

/**
 * Entidades financieras y su compatibilidad real. Una entidad sin reglas probadas no se
 * puede activar: no se inventa compatibilidad (sección 17).
 */
@Composable
fun BankSourcesScreen(container: AppContainer) {
    val rows by container.database.paymentSources().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val enabledById = rows.associate { it.sourceId to it.enabled }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Solo se supervisan las entidades cuya lectura fue probada con notificaciones reales. " +
                    "Las demás aparecen como «No disponible».",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(container.processor.adapters, key = { it.sourceId.name }) { adapter ->
            val available = adapter.compatibility != CompatibilityStatus.NOT_IMPLEMENTED
            val enabled = available && (enabledById[adapter.sourceId.name] ?: true)
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(adapter.sourceId.displayName, style = MaterialTheme.typography.titleMedium)
                        when (adapter.compatibility) {
                            CompatibilityStatus.VERIFIED -> Pill("Compatible", StatusOk)
                            CompatibilityStatus.IN_TESTING -> Pill("En pruebas", StatusWarn)
                            CompatibilityStatus.NOT_IMPLEMENTED -> Pill("No disponible", StatusNeutral)
                        }
                    }
                    Switch(
                        checked = enabled,
                        enabled = available,
                        onCheckedChange = { value ->
                            scope.launch {
                                container.database.paymentSources().upsert(
                                    PaymentSourceEntity(adapter.sourceId.name, value, customPhrase = null),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
