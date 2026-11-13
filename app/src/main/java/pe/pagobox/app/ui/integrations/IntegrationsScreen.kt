package pe.pagobox.app.ui.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.data.preferences.AppSettings
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.ui.components.formatDateTime
import pe.pagobox.engine.integrations.IntegrationCatalog
import pe.pagobox.engine.integrations.IntegrationPanel
import pe.pagobox.engine.integrations.ProviderInfo
import pe.pagobox.engine.integrations.ProviderLiveStatus

/**
 * Panel de integraciones oficiales (Canal B). El estado «Activa» solo aparece cuando el backend informa
 * que procesó un webhook real comprobado; la app nunca lo deduce por su cuenta.
 */
@Composable
fun IntegrationsScreen(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val sync by container.officialSync.state.collectAsStateWithLifecycle()
    val live by container.officialSync.integrations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val backendConfigured = settings.backendUrl.isNotBlank() && settings.backendToken.isNotBlank()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Cómo funciona") {
            Text(
                "Las notificaciones del celular solo permiten anunciar «posibles ingresos». Un pago se anuncia como " +
                    "«confirmado» únicamente cuando el proveedor lo informa a tu servidor de integraciones. Hoy, " +
                    "ningún proveedor permite consultar todos los Yape o Plin que llegan a tu número: solo los cobros " +
                    "hechos por su propio canal (checkout, link o QR del proveedor).",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        BackendCard(container, settings.backendUrl, backendConfigured, sync, scope)

        IntegrationCatalog.providers.forEach { info ->
            ProviderCard(info, backendConfigured, live.firstOrNull { it.provider == info.id })
        }
    }
}

@Composable
private fun BackendCard(
    container: AppContainer,
    currentUrl: String,
    configured: Boolean,
    sync: pe.pagobox.app.payment.OfficialSyncState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    SectionCard("Servidor de integraciones") {
        Text(
            "Lo instala y administra quien gestiona tu cuenta de proveedor. Las credenciales del proveedor viven solo " +
                "en ese servidor; aquí solo se guarda el token de lectura de este celular.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(url, { url = it }, label = { Text("URL (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            token, { token = it },
            label = { Text(if (configured) "Token (déjalo vacío para mantener el actual)" else "Token del celular") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            scope.launch {
                val current = container.settings.settings.first()
                val newToken = token.ifBlank { current.backendToken }
                error = runCatching { container.settings.setBackend(url, newToken) }.exceptionOrNull()?.message
                if (error == null) {
                    token = ""
                    runCatching { container.officialSync.syncOnce() }
                }
            }
        }) { Text("Guardar y probar") }
        if (configured) {
            TextButton(onClick = { scope.launch { container.settings.setBackend("", "") } }) { Text("Desconectar") }
        }
        Text("Entorno: ${sync.environment ?: "—"}")
        Text("Última sincronización correcta: ${sync.lastSuccessAt?.let(::formatDateTime) ?: "nunca"}")
        sync.lastError?.let { Text("Último error: $it", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ProviderCard(info: ProviderInfo, backendConfigured: Boolean, live: ProviderLiveStatus?) {
    val summary = IntegrationPanel.summarize(info, backendConfigured, live)
    SectionCard(info.id.displayName) {
        Text(
            summary.label,
            fontWeight = FontWeight.Bold,
            color = if (summary.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(summary.detail, style = MaterialTheme.typography.bodySmall)
        Field("Métodos de cobro", info.methods)
        Field("Compatibilidad", info.coverage)
        Field("Afiliación", info.affiliation)
        Field("Credenciales", info.credentials + if (live != null) {
            if (live.credentialsConfigured) " Configuradas en el servidor." else " No configuradas en el servidor."
        } else "")
        Field("Sandbox / producción", info.environments)
        Field("Webhook", info.webhook + (live?.webhookPath?.let { " Ruta: $it" } ?: ""))
        Field("Última comunicación", live?.lastCommunicationAt?.let(::formatDateTime) ?: "—")
        Field("Último evento comprobado", live?.lastEventAt?.let(::formatDateTime) ?: "—")
        Field("Último error", live?.lastError ?: "—")
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
