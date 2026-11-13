package pe.pagobox.app.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import pe.pagobox.app.AppContainer
import pe.pagobox.app.data.preferences.AnnouncementOutput
import pe.pagobox.app.device.BleDeviceLink
import pe.pagobox.app.voice.AndroidVoiceEngine
import pe.pagobox.domain.model.SystemHealth

enum class CheckStatus { OK, WARNING, ERROR, NOT_APPLICABLE }

data class HealthCheck(val title: String, val status: CheckStatus, val detail: String)

data class HealthReport(val health: SystemHealth, val summary: String, val checks: List<HealthCheck>)

/**
 * Evalúa el estado real del sistema (secciones 3.1 y 7). Solo informa «Operativo»
 * cuando todos los componentes necesarios están comprobados.
 */
class HealthMonitor(private val context: Context, private val c: AppContainer) {

    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(5_000)
        }
    }

    val report: Flow<HealthReport> = combine(
        c.listenerConnected,
        c.voice.state,
        c.bleLink.state,
        combine(c.devices.devices, c.settings.settings) { d, s -> d to s },
        ticker,
    ) { listener, voice, ble, (devices, settings), _ ->
        evaluate(listener, voice, ble, devices.isNotEmpty(), settings.output)
    }

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun hasBluetoothPermissions(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun isIgnoringBatteryOptimizations(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun evaluate(
        listenerConnected: Boolean,
        voice: AndroidVoiceEngine.State,
        ble: BleDeviceLink.State,
        hasDevice: Boolean,
        output: AnnouncementOutput,
    ): HealthReport {
        val access = hasNotificationAccess()
        val supervised = c.processor.supervisedCount
        val needsDevice = hasDevice && output != AnnouncementOutput.PHONE
        val needsPhoneVoice = output != AnnouncementOutput.DEVICE || !hasDevice

        val checks = listOf(
            HealthCheck(
                "Acceso a notificaciones",
                if (access) CheckStatus.OK else CheckStatus.ERROR,
                if (access) "Concedido" else "No concedido: PAGOBOX no puede leer pagos",
            ),
            HealthCheck(
                "Servicio de lectura",
                if (listenerConnected) CheckStatus.OK else CheckStatus.ERROR,
                if (listenerConnected) "Conectado" else "Desconectado",
            ),
            HealthCheck(
                "Entidades supervisadas",
                if (supervised > 0) CheckStatus.OK else CheckStatus.ERROR,
                if (supervised > 0) "$supervised aplicaciones" else "Ninguna entidad con formatos verificados: aún no se anuncian pagos reales",
            ),
            HealthCheck(
                "Restricciones de batería",
                if (isIgnoringBatteryOptimizations()) CheckStatus.OK else CheckStatus.WARNING,
                if (isIgnoringBatteryOptimizations()) "Sin restricciones" else "Android puede detener PAGOBOX en segundo plano",
            ),
            HealthCheck(
                "Voz en español",
                when {
                    voice == AndroidVoiceEngine.State.READY -> CheckStatus.OK
                    needsPhoneVoice -> CheckStatus.ERROR
                    else -> CheckStatus.WARNING
                },
                when (voice) {
                    AndroidVoiceEngine.State.READY -> "Disponible"
                    AndroidVoiceEngine.State.INITIALIZING -> "Iniciando…"
                    AndroidVoiceEngine.State.NO_SPANISH_VOICE -> "No hay voz en español instalada"
                    AndroidVoiceEngine.State.UNAVAILABLE -> "Motor de voz no disponible"
                },
            ),
            HealthCheck(
                "Permisos Bluetooth",
                when {
                    hasBluetoothPermissions() -> CheckStatus.OK
                    hasDevice -> CheckStatus.ERROR
                    else -> CheckStatus.NOT_APPLICABLE
                },
                if (hasBluetoothPermissions()) "Concedidos" else "No concedidos",
            ),
            HealthCheck(
                "Equipo PAGOBOX",
                when {
                    !hasDevice -> CheckStatus.NOT_APPLICABLE
                    ble == BleDeviceLink.State.READY -> CheckStatus.OK
                    else -> CheckStatus.ERROR
                },
                when {
                    !hasDevice -> "Sin equipo vinculado"
                    ble == BleDeviceLink.State.READY -> "Conectado"
                    ble == BleDeviceLink.State.BLUETOOTH_OFF -> "Bluetooth desactivado"
                    ble == BleDeviceLink.State.BONDING -> "Esperando código de vinculación"
                    else -> "Desconectado; reintentando"
                },
            ),
        )

        val health = when {
            !access || !listenerConnected || supervised == 0 -> SystemHealth.UNSUPERVISED
            needsDevice && ble != BleDeviceLink.State.READY -> SystemHealth.DISCONNECTED
            checks.any { it.status == CheckStatus.ERROR || it.status == CheckStatus.WARNING } -> SystemHealth.DEGRADED
            else -> SystemHealth.OPERATIONAL
        }
        val summary = when (health) {
            SystemHealth.OPERATIONAL -> "Operativo: todos los componentes comprobados"
            SystemHealth.DEGRADED -> "Degradado: " + checks.first { it.status != CheckStatus.OK && it.status != CheckStatus.NOT_APPLICABLE }.title
            SystemHealth.DISCONNECTED -> "Desconectado: el equipo no se está comunicando"
            SystemHealth.UNSUPERVISED -> "Sin supervisión: " + checks.first { it.status == CheckStatus.ERROR }.title
        }
        return HealthReport(health, summary, checks)
    }
}
