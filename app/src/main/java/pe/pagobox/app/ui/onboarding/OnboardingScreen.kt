package pe.pagobox.app.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.diagnostics.CheckStatus
import pe.pagobox.app.ui.components.CheckRow
import pe.pagobox.app.ui.components.SectionCard

/** Configuración inicial: permisos y activación, comprobados en vivo (sección 8). */
@SuppressLint("BatteryLife")
@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    // Se vuelve a comprobar cada vez que el usuario regresa de Ajustes.
    val access = remember(refresh) { container.health.hasNotificationAccess() }
    val battery = remember(refresh) { container.health.isIgnoringBatteryOptimizations() }
    val bluetooth = remember(refresh) { container.health.hasBluetoothPermissions() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Bienvenido a PAGOBOX", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Escucha los pagos que recibe tu negocio. Configura estos permisos para empezar.")

        SectionCard("1. Acceso a notificaciones (obligatorio)") {
            CheckRow("Acceso a notificaciones", if (access) "Concedido" else "Pendiente", if (access) CheckStatus.OK else CheckStatus.ERROR)
            Text(
                "PAGOBOX solo lee las notificaciones de las entidades financieras supervisadas. " +
                    "No lee contraseñas, PIN ni códigos, y no guarda el texto completo.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!access) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text("Abrir ajustes")
                }
            }
        }
        SectionCard("2. Funcionamiento en segundo plano (recomendado)") {
            CheckRow("Sin restricción de batería", if (battery) "Configurado" else "Pendiente", if (battery) CheckStatus.OK else CheckStatus.WARNING)
            if (!battery) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }) { Text("Permitir") }
            }
        }
        SectionCard("3. Bluetooth (si usarás el equipo PAGOBOX)") {
            CheckRow("Permisos Bluetooth", if (bluetooth) "Concedidos" else "Pendiente", if (bluetooth) CheckStatus.OK else CheckStatus.NOT_APPLICABLE)
            if (!bluetooth) {
                OutlinedButton(onClick = {
                    btLauncher.launch(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                    )
                }) { Text("Conceder") }
            }
        }
        Button(enabled = access, onClick = {
            scope.launch {
                container.settings.setOnboardingDone()
                onDone()
            }
        }) { Text("Continuar") }
        if (!access) {
            Text("Para continuar, concede el acceso a notificaciones.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
