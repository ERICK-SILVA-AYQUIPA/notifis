package pe.pagobox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.pagobox.app.PagoboxApplication
import pe.pagobox.app.voice.AndroidVoiceEngine
import pe.pagobox.domain.model.SystemHealth

/**
 * Pantalla de diagnóstico (sección 3.1). Solo muestra estados comprobados:
 * nunca dice «Todo funcionando» si no puede verificarlo (sección 7).
 */
@Composable
fun DiagnosticsScreen(app: PagoboxApplication, onOpenNotificationAccess: () -> Unit) {
    val context = LocalContext.current
    fun checkAccess() = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    // Se vuelve a comprobar al regresar de Ajustes.
    var hasAccess by remember { mutableStateOf(checkAccess()) }
    LifecycleResumeEffect(Unit) {
        hasAccess = checkAccess()
        onPauseOrDispose { }
    }
    val connected by app.status.connected.collectAsStateWithLifecycle()
    val lastResult by app.status.lastResult.collectAsStateWithLifecycle()
    val voiceState by app.voice.state.collectAsStateWithLifecycle()
    val supervised = app.registry.supervisedPackages.size

    val health = when {
        !hasAccess || !connected || supervised == 0 -> SystemHealth.UNSUPERVISED
        voiceState != AndroidVoiceEngine.State.READY -> SystemHealth.DEGRADED
        else -> SystemHealth.OPERATIONAL
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PAGOBOX — Diagnóstico", style = MaterialTheme.typography.headlineSmall)
                Text("Estado general: ${health.label()}", style = MaterialTheme.typography.titleMedium)
                Text("Acceso a notificaciones: ${if (hasAccess) "concedido" else "NO concedido"}")
                Text("Servicio de lectura: ${if (connected) "conectado" else "desconectado"}")
                Text(
                    "Entidades supervisadas: $supervised" +
                        if (supervised == 0) " (ninguna entidad verificada todavía)" else "",
                )
                Text("Voz en español: ${voiceState.label()}")
                lastResult?.let { Text("Último evento: $it") }
                if (!hasAccess) {
                    Button(onClick = onOpenNotificationAccess) { Text("Conceder acceso a notificaciones") }
                }
                if (voiceState == AndroidVoiceEngine.State.READY) {
                    Button(onClick = { app.voice.test("Prueba de voz de PAGOBOX") }) { Text("Probar voz") }
                }
            }
        }
    }
}

private fun SystemHealth.label() = when (this) {
    SystemHealth.OPERATIONAL -> "Operativo"
    SystemHealth.DEGRADED -> "Degradado"
    SystemHealth.DISCONNECTED -> "Desconectado"
    SystemHealth.UNSUPERVISED -> "Sin supervisión"
}

private fun AndroidVoiceEngine.State.label() = when (this) {
    AndroidVoiceEngine.State.INITIALIZING -> "iniciando…"
    AndroidVoiceEngine.State.READY -> "disponible"
    AndroidVoiceEngine.State.NO_SPANISH_VOICE -> "no hay voz en español instalada"
    AndroidVoiceEngine.State.UNAVAILABLE -> "motor TTS no disponible"
}
