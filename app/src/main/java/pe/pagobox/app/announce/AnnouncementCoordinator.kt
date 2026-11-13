package pe.pagobox.app.announce

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.pagobox.app.data.local.AnnouncementHistoryDao
import pe.pagobox.app.data.local.AnnouncementHistoryEntity
import pe.pagobox.app.data.local.PhraseTemplateDao
import pe.pagobox.app.data.local.VoiceProfileDao
import pe.pagobox.app.data.local.VoiceProfileEntity
import pe.pagobox.app.data.preferences.AnnouncementOutput
import pe.pagobox.app.data.preferences.SettingsRepository
import pe.pagobox.app.data.repositories.DiagnosticsLogger
import pe.pagobox.app.data.repositories.PaymentRepository
import pe.pagobox.app.device.DeliveryResult
import pe.pagobox.app.device.DeviceManager
import pe.pagobox.app.system.ShortWakeLock
import pe.pagobox.app.voice.AndroidVoiceEngine
import pe.pagobox.app.voice.PhraseSelector
import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.engine.queue.AnnouncementQueue
import java.time.Instant

/**
 * Motor de anuncios (sección 2.6). La base de datos es la fuente de verdad: al iniciar se
 * restauran los pendientes y los ya anunciados, así un evento nunca se reproduce dos veces
 * tras un reinicio o reconexión. Se reproduce un anuncio a la vez, en orden de llegada.
 */
class AnnouncementCoordinator(
    private val context: Context,
    private val payments: PaymentRepository,
    private val settings: SettingsRepository,
    private val voiceDao: VoiceProfileDao,
    private val templateDao: PhraseTemplateDao,
    private val historyDao: AnnouncementHistoryDao,
    private val voice: AndroidVoiceEngine,
    private val devices: DeviceManager,
    private val wakeLock: ShortWakeLock,
    private val log: DiagnosticsLogger,
    private val scope: CoroutineScope,
) {
    private val queue = AnnouncementQueue()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun start() {
        scope.launch {
            queue.restoreAnnounced(payments.announcedIds())
            loop()
        }
        // Al reconectar el equipo se reintentan los pendientes.
        scope.launch { devices.link.state.collect { notifyNewIncome() } }
    }

    /** Audio de la prueba de extremo a extremo: deja claro que no es un pago. */
    suspend fun synthesizeTest(file: java.io.File): Boolean {
        val profile = voiceDao.get() ?: DEFAULT_VOICE
        return voice.synthesizeToFile("Prueba de PAGOBOX. Esto no es un pago.", profile, file)
    }

    fun notifyNewIncome() {
        wake.trySend(Unit)
    }

    private suspend fun loop() {
        while (true) {
            payments.pendingAnnouncements().forEach { queue.enqueue(it) }
            val current = settings.settings.first()
            val now = Instant.now()
            val event = queue.next(now)
            if (event == null) {
                val wait = current.mutedUntil?.takeIf { it.isAfter(now) }?.let { it.toEpochMilli() - now.toEpochMilli() }
                if (wait != null) {
                    kotlinx.coroutines.withTimeoutOrNull(wait) { wake.receive() }
                } else {
                    wake.receive()
                }
                continue
            }
            queue.muteUntil(current.mutedUntil)
            val ok = wakeLock.hold("announce", ANNOUNCE_WAKE_MS) { announce(event, current.output, current.phoneFallback) }
            if (ok) {
                queue.markAnnounced(event.eventId)
            } else {
                queue.markFailed(event.eventId)
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun announce(event: PaymentEvent, output: AnnouncementOutput, phoneFallback: Boolean): Boolean {
        if (Instant.now().toEpochMilli() - event.detectedTimestamp.toEpochMilli() > MAX_PENDING_AGE_MS) {
            // Un aviso muy atrasado confundiría al comerciante: se registra como no entregado.
            payments.updateAnnouncement(event.eventId, AnnouncementStatus.FAILED, EventState.DELIVERY_FAILED)
            log.warn("Anuncios", "${event.eventId} no se anunció a tiempo; queda en el historial como no entregado")
            return true
        }
        val profile = voiceDao.get() ?: DEFAULT_VOICE
        val phrase = PhraseSelector.select(event, templateDao.enabled(), profile.announceSender)
        if (phrase == null) {
            log.error("Anuncios", "No se pudo generar la frase de ${event.eventId}")
            payments.updateAnnouncement(event.eventId, AnnouncementStatus.SKIPPED, EventState.PENDING_REVIEW)
            return true
        }

        val wantDevice = output != AnnouncementOutput.PHONE && devices.hasDevice()
        var wantPhone = output != AnnouncementOutput.DEVICE || !devices.hasDevice()
        var played = false

        if (wantDevice) {
            when (val r = devices.deliver(event, { file -> voice.synthesizeToFile(phrase, profile, file) }, context.cacheDir)) {
                DeliveryResult.Played -> {
                    played = true
                    history(event.eventId, phrase, "DEVICE", "PLAYED", null)
                }
                is DeliveryResult.Failed -> {
                    history(event.eventId, phrase, "DEVICE", "FAILED", r.reason)
                    _lastError.value = "Equipo: ${r.reason}"
                    log.warn("Anuncios", "Entrega fallida de ${event.eventId}: ${r.reason}")
                    if (phoneFallback) wantPhone = true
                }
            }
        }

        if (wantPhone) {
            if (voice.speak(phrase, profile)) {
                played = true
                history(event.eventId, phrase, "PHONE", "PLAYED", null)
            } else {
                history(event.eventId, phrase, "PHONE", "FAILED", "Motor de voz no disponible")
                _lastError.value = "Voz del celular no disponible"
            }
        }

        return if (played) {
            payments.updateAnnouncement(event.eventId, AnnouncementStatus.ANNOUNCED, EventState.ANNOUNCED)
            _lastError.value = null
            true
        } else {
            // Sigue pendiente en la base de datos para reintentar.
            payments.updateAnnouncement(event.eventId, AnnouncementStatus.PENDING, EventState.DELIVERY_FAILED)
            false
        }
    }

    private suspend fun history(eventId: String, phrase: String, output: String, result: String, detail: String?) =
        historyDao.insert(AnnouncementHistoryEntity(eventId = eventId, phrase = phrase, output = output, result = result, detail = detail, timestamp = System.currentTimeMillis()))

    companion object {
        private const val RETRY_DELAY_MS = 15_000L
        /** Síntesis + entrega al equipo con ACK + voz en el celular; tope de seguridad. */
        private const val ANNOUNCE_WAKE_MS = 120_000L
        private const val MAX_PENDING_AGE_MS = 30L * 60 * 1000
        val DEFAULT_VOICE = VoiceProfileEntity(voiceName = null, speechRate = 1.0f, pitch = 1.0f, volume = 100, announceSender = true)
    }
}
