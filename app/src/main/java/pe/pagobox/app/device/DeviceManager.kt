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

/** Resultado de la prueba de extremo a extremo (sin pago real: el equipo muestra «PRUEBA»). */
data class EndToEndResult(
    val testId: String,
    val receivedMs: Long?,
    val playedMs: Long?,
    val audioUploaded: Boolean,
    val error: String?,
) {
    val passed: Boolean get() = error == null && receivedMs != null && playedMs != null
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
            link.incoming.filterIsInstance<DeviceProtocol.Incoming.Ack>().collect { ack ->
                val current = dao.current() ?: return@collect
                if (ack.sequence > current.lastAckedSequence) {
                    dao.upsert(current.copy(lastAckedSequence = ack.sequence, lastSeenAt = System.currentTimeMillis()))
                }
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

    /** Última secuencia que el equipo confirmó; el teléfono nunca crea eventos por debajo de ella. */
    suspend fun lastAckedSequence(): Long = dao.current()?.lastAckedSequence ?: 0L

    val isReady: Boolean get() = link.state.value == BleDeviceLink.State.READY

    suspend fun setVolume(volume: Int) = link.send(DeviceProtocol.setVolume(volume))
    suspend fun configureWifi(ssid: String, password: String) = link.send(DeviceProtocol.wifiConfig(ssid, password))
    suspend fun playTest() = link.send(DeviceProtocol.testAnnouncement())

    /** Actualización de firmware por Wi-Fi local. Requiere que el equipo tenga IP y token vigentes. */
    suspend fun updateFirmware(image: java.io.InputStream, length: Long): Result<Unit> {
        val ip = dao.current()?.wifiIp ?: return Result.failure(IllegalStateException("El equipo no está conectado al Wi-Fi"))
        val token = audioToken ?: return Result.failure(IllegalStateException("El equipo no está conectado por Bluetooth"))
        if (length <= 0) return Result.failure(IllegalArgumentException("Archivo vacío"))
        log.info("Equipo", "Enviando actualización de firmware ($length bytes)")
        return uploader.uploadFirmware(ip, token, image, length).onSuccess {
            log.info("Equipo", "Firmware enviado; el equipo se reiniciará")
        }.onFailure { log.error("Equipo", "Falló la actualización de firmware", it) }
    }

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

    /** Pide la autoprueba de componentes. Null si el equipo no responde. */
    suspend fun selfTest(): DeviceProtocol.Incoming.SelfTestResult? {
        if (!isReady) return null
        val result = scope.async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(SELF_TEST_TIMEOUT_MS) {
                link.incoming.filterIsInstance<DeviceProtocol.Incoming.SelfTestResult>().first()
            }
        }
        if (!link.send(DeviceProtocol.selfTest())) {
            result.cancel()
            return null
        }
        return result.await().also {
            log.info("Equipo", if (it != null) "Autoprueba: pantalla=${it.display} audio=${it.audio} wifi=${it.wifi} reinicio=${it.resetReason}" else "Autoprueba sin respuesta")
        }
    }

    /**
     * Prueba de extremo a extremo con un TEST_EVENT: BLE -> ACK RECEIVED -> audio por Wi-Fi -> ACK PLAYED.
     * No usa la secuencia de pagos ni crea eventos en el historial.
     */
    suspend fun endToEndTest(synthesize: suspend (File) -> Boolean, cacheDir: File): EndToEndResult {
        val testId = "test_%010d".format(System.currentTimeMillis() % 10_000_000_000L)
        if (!isReady) return EndToEndResult(testId, null, null, false, "Equipo no conectado")
        val start = System.currentTimeMillis()
        val received = scope.waitForAck(testId, "RECEIVED", ACK_TIMEOUT_MS)
        val played = scope.waitForAck(testId, "PLAYED", PLAY_TIMEOUT_MS)
        if (!link.send(DeviceMessages.testEvent(testId))) {
            received.cancel(); played.cancel()
            return EndToEndResult(testId, null, null, false, "No se pudo enviar por BLE")
        }
        if (received.await() == null) {
            played.cancel()
            return EndToEndResult(testId, null, null, false, "Sin ACK RECEIVED en ${ACK_TIMEOUT_MS / 1000} s")
        }
        val receivedMs = System.currentTimeMillis() - start
        var uploaded = false
        val device = dao.current()
        val token = audioToken
        if (device?.wifiIp != null && token != null) {
            val wav = File(cacheDir, "prueba_$testId.wav")
            try {
                if (synthesize(wav)) uploaded = uploader.upload(device.wifiIp, token, testId, wav).isSuccess
            } finally {
                wav.delete()
            }
        }
        val playedAck = played.await()
        val result = EndToEndResult(
            testId = testId,
            receivedMs = receivedMs,
            playedMs = playedAck?.let { System.currentTimeMillis() - start },
            audioUploaded = uploaded,
            error = if (playedAck == null) "Sin ACK PLAYED en ${PLAY_TIMEOUT_MS / 1000} s" else null,
        )
        log.info("Equipo", "Prueba extremo a extremo $testId: recibido=${result.receivedMs} ms, reproducido=${result.playedMs} ms, audio Wi-Fi=$uploaded")
        return result
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
        const val SELF_TEST_TIMEOUT_MS = 10_000L
    }
}
