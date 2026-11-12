package pe.pagobox.app.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pe.pagobox.app.data.local.DeviceDao
import pe.pagobox.app.data.local.DeviceEntity
import pe.pagobox.app.data.repositories.DiagnosticsLogger
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.engine.protocol.DeviceMessages
import java.io.File

/** Resultado de entregar un anuncio al equipo. Un ACK no confirma que el dinero ingresó. */
sealed interface DeliveryResult {
    data object Played : DeliveryResult
    data class Failed(val reason: String) : DeliveryResult
}

/**
 * Administra el equipo vinculado: reconexión, estado (batería, volumen, Wi-Fi) y
 * entrega de eventos con ACK y audio.
 */
class DeviceManager(
    val link: BleDeviceLink,
    private val dao: DeviceDao,
    private val uploader: DeviceAudioUploader,
    private val log: DiagnosticsLogger,
    private val scope: CoroutineScope,
) {
    val devices: Flow<List<DeviceEntity>> = dao.observeAll()

    /** Token de audio vigente (solo en memoria: se renueva en cada conexión). */
    @Volatile private var audioToken: String? = null

    fun start() {
        scope.launch {
            link.incoming.filterIsInstance<DeviceProtocol.Incoming.Status>().collect { s ->
                audioToken = s.audioToken
                val current = dao.current() ?: return@collect
                dao.upsert(
                    current.copy(
                        deviceId = current.deviceId,
                        lastSeenAt = System.currentTimeMillis(),
                        firmwareVersion = s.firmware,
                        batteryPercent = s.battery,
                        volume = s.volume,
                        wifiIp = s.wifiIp,
                        lastAckedSequence = maxOf(current.lastAckedSequence, s.lastSequence),
                    ),
                )
                if (s.battery != null && s.battery <= LOW_BATTERY && !s.charging) {
                    log.warn("Equipo", "Batería baja: ${s.battery}%")
                }
                s.error?.let { log.error("Equipo", "El equipo informa: $it") }
            }
        }
        scope.launch {
            link.state.collect { st ->
                if (st == BleDeviceLink.State.READY) link.send(DeviceProtocol.statusRequest())
            }
        }
        scope.launch { dao.current()?.let { link.connect(it.bleAddress) } }
    }

    suspend fun pair(found: FoundDevice) {
        dao.upsert(
            DeviceEntity(
                deviceId = found.address,
                name = found.name,
                bleAddress = found.address,
                pairedAt = System.currentTimeMillis(),
                lastSeenAt = null,
                lastAckedSequence = 0,
                firmwareVersion = null,
                batteryPercent = null,
                volume = null,
                wifiIp = null,
            ),
        )
        link.connect(found.address)
        log.info("Equipo", "Equipo ${found.name} vinculado")
    }

    suspend fun forget(device: DeviceEntity) {
        link.disconnect()
        dao.delete(device.deviceId)
        log.info("Equipo", "Equipo ${device.name} desvinculado")
    }

    suspend fun hasDevice(): Boolean = dao.current() != null

    val isReady: Boolean get() = link.state.value == BleDeviceLink.State.READY

    suspend fun setVolume(volume: Int) = link.send(DeviceProtocol.setVolume(volume))
    suspend fun configureWifi(ssid: String, password: String) = link.send(DeviceProtocol.wifiConfig(ssid, password))
    suspend fun playTest() = link.send(DeviceProtocol.testAnnouncement())

    /**
     * Entrega un evento: mensaje por BLE -> ACK RECEIVED -> audio por Wi-Fi (si hay)
     * -> ACK PLAYED. [synthesize] genera el WAV de la frase.
     */
    suspend fun deliver(event: PaymentEvent, synthesize: suspend (File) -> Boolean, cacheDir: File): DeliveryResult {
        if (!isReady) return DeliveryResult.Failed("Equipo no conectado")
        val received = scope.waitForAck(event.eventId, "RECEIVED", ACK_TIMEOUT_MS)
        val played = scope.waitForAck(event.eventId, "PLAYED", PLAY_TIMEOUT_MS)
        if (!link.send(DeviceMessages.paymentEvent(event))) {
            received.cancel(); played.cancel()
            return DeliveryResult.Failed("No se pudo enviar por BLE")
        }
        if (received.await() == null) {
            played.cancel()
            return DeliveryResult.Failed("El equipo no confirmó la recepción (sin ACK)")
        }

        val device = dao.current()
        val token = audioToken
        if (device?.wifiIp != null && token != null) {
            val wav = File(cacheDir, "anuncio_${event.eventId}.wav")
            try {
                if (synthesize(wav)) {
                    uploader.upload(device.wifiIp, token, event.eventId, wav).onFailure {
                        log.warn("Equipo", "No se pudo enviar el audio por Wi-Fi: ${it.message}")
                    }
                }
            } finally {
                wav.delete()
            }
        }
        // Sin audio por Wi-Fi el equipo reproduce su tono de aviso y muestra el pago en pantalla.
        return if (played.await() != null) DeliveryResult.Played else DeliveryResult.Failed("El equipo no confirmó la reproducción")
    }

    private fun CoroutineScope.waitForAck(eventId: String, stage: String, timeout: Long) =
        // UNDISPATCHED: se suscribe antes de enviar, para no perder un ACK rápido.
        async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(timeout) {
                link.incoming.filterIsInstance<DeviceProtocol.Incoming.Ack>()
                    .first { it.eventId == eventId && it.stage == stage }
            }
        }

    private companion object {
        const val ACK_TIMEOUT_MS = 5_000L
        const val PLAY_TIMEOUT_MS = 30_000L
        const val LOW_BATTERY = 15
    }
}
