package pe.pagobox.app.ui.devices

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.data.local.DeviceEntity
import pe.pagobox.app.device.BleDeviceLink
import pe.pagobox.app.device.FoundDevice
import pe.pagobox.app.ui.components.LabeledValue
import pe.pagobox.app.ui.components.Pill
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.ui.components.formatDateTime
import pe.pagobox.app.ui.theme.StatusError
import pe.pagobox.app.ui.theme.StatusOk
import pe.pagobox.app.ui.theme.StatusWarn

private val BLE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

@Composable
fun DeviceManagementScreen(container: AppContainer) {
    val devices by container.devices.devices.collectAsStateWithLifecycle(initialValue = emptyList())
    val linkState by container.bleLink.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(container.health.hasBluetoothPermissions()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        hasPermission = res.values.all { it }
    }
    var found by remember { mutableStateOf<List<FoundDevice>>(emptyList()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var wifiDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { scanJob?.cancel() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hasPermission) {
            SectionCard("Permiso de Bluetooth") {
                Text("PAGOBOX necesita Bluetooth para comunicarse con el equipo.")
                Button(onClick = { launcher.launch(BLE_PERMISSIONS) }) { Text("Conceder permiso") }
            }
            return@Column
        }
        if (!container.bleLink.isBluetoothEnabled) {
            SectionCard("Bluetooth desactivado") {
                Text("Activa el Bluetooth del celular para usar el equipo PAGOBOX.", color = MaterialTheme.colorScheme.error)
            }
        }

        val device = devices.firstOrNull()
        if (device != null) {
            PairedDeviceCard(
                device = device,
                state = linkState,
                onVolume = { v -> scope.launch { message = if (container.devices.setVolume(v)) null else "No se pudo cambiar el volumen" } },
                onTest = { scope.launch { message = if (container.devices.playTest()) "Prueba enviada al equipo" else "El equipo no está conectado" } },
                onWifi = { wifiDialog = true },
                onReconnect = { container.bleLink.connect(device.bleAddress) },
                onForget = { scope.launch { container.devices.forget(device) } },
            )
        } else {
            SectionCard("Vincular equipo") {
                Text(
                    "Enciende el equipo PAGOBOX y acércalo al celular. Al vincular, el equipo mostrará un código " +
                        "de 6 dígitos que debes confirmar en el celular.",
                )
                if (scanJob == null) {
                    Button(onClick = {
                        found = emptyList()
                        scanJob = scope.launch { container.bleLink.scan().collect { found = it } }
                    }) { Text("Buscar equipos") }
                } else {
                    OutlinedButton(onClick = { scanJob?.cancel(); scanJob = null }) { Text("Detener búsqueda") }
                    if (found.isEmpty()) Text("Buscando…")
                }
                found.forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name)
                            Text("${f.address} · señal ${f.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            scanJob?.cancel(); scanJob = null
                            scope.launch { container.devices.pair(f) }
                        }) { Text("Vincular") }
                    }
                }
            }
        }
        message?.let { Text(it) }
    }

    if (wifiDialog) {
        WifiDialog(
            onDismiss = { wifiDialog = false },
            onSave = { ssid, pass ->
                wifiDialog = false
                scope.launch {
                    message = if (container.devices.configureWifi(ssid, pass)) {
                        "Datos de Wi-Fi enviados por el enlace cifrado"
                    } else {
                        "No se pudo enviar: el equipo no está conectado"
                    }
                }
            },
        )
    }
}

@Composable
private fun PairedDeviceCard(
    device: DeviceEntity,
    state: BleDeviceLink.State,
    onVolume: (Int) -> Unit,
    onTest: () -> Unit,
    onWifi: () -> Unit,
    onReconnect: () -> Unit,
    onForget: () -> Unit,
) {
    var volume by remember(device.volume) { mutableFloatStateOf((device.volume ?: 70).toFloat()) }
    SectionCard(device.name) {
        when (state) {
            BleDeviceLink.State.READY -> Pill("Conectado", StatusOk)
            BleDeviceLink.State.CONNECTING -> Pill("Conectando…", StatusWarn)
            BleDeviceLink.State.BONDING -> Pill("Confirma el código del equipo", StatusWarn)
            BleDeviceLink.State.DISCONNECTED -> Pill("Desconectado", StatusError)
            BleDeviceLink.State.BLUETOOTH_OFF -> Pill("Bluetooth desactivado", StatusError)
        }
        LabeledValue("Batería", device.batteryPercent?.let { "$it%" } ?: "Sin dato")
        LabeledValue("Wi-Fi (audio)", device.wifiIp ?: "No configurado: el equipo usará su tono de aviso")
        LabeledValue("Firmware", device.firmwareVersion ?: "Sin dato")
        LabeledValue("Última comunicación", device.lastSeenAt?.let(::formatDateTime) ?: "Nunca")
        LabeledValue("Último evento confirmado", "#${device.lastAckedSequence}")
        Text("Volumen del equipo: ${volume.toInt()}%")
        Slider(volume, { volume = it }, valueRange = 0f..100f, onValueChangeFinished = { onVolume(volume.toInt()) }, enabled = state == BleDeviceLink.State.READY)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTest, enabled = state == BleDeviceLink.State.READY) { Text("Probar") }
            OutlinedButton(onClick = onWifi, enabled = state == BleDeviceLink.State.READY) { Text("Wi-Fi") }
            if (state != BleDeviceLink.State.READY) OutlinedButton(onClick = onReconnect) { Text("Reconectar") }
        }
        TextButton(onClick = onForget) { Text("Desvincular equipo", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun WifiDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi del equipo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("El equipo usa el Wi-Fi local para recibir la voz. No necesita Internet.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(ssid, { ssid = it }, label = { Text("Nombre de la red") }, singleLine = true)
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(enabled = ssid.isNotBlank(), onClick = { onSave(ssid, pass) }) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
