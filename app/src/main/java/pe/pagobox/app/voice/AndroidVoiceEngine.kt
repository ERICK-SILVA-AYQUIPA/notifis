package pe.pagobox.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.pagobox.engine.queue.AnnouncementQueue
import pe.pagobox.engine.voice.PhraseTemplateRenderer
import java.time.Instant
import java.util.Locale

/**
 * Motor de voz local con el TTS de Android. Informa si hay voz en español instalada
 * (requisito 2.4: no depender de Internet). Reproduce un anuncio a la vez (QUEUE_ADD
 * y la cola del motor garantizan que no se interrumpa una frase).
 */
class AndroidVoiceEngine(context: Context) {
    enum class State { INITIALIZING, READY, NO_SPANISH_VOICE, UNAVAILABLE }

    private val _state = MutableStateFlow(State.INITIALIZING)
    val state: StateFlow<State> = _state.asStateFlow()

    private var queue: AnnouncementQueue? = null
    private var phrases: PhraseTemplateRenderer? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        _state.value = if (status != TextToSpeech.SUCCESS) State.UNAVAILABLE else selectSpanish()
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finish(utteranceId, ok = true)
            @Deprecated("Requerido por la API")
            override fun onError(utteranceId: String) = finish(utteranceId, ok = false)
            override fun onError(utteranceId: String, errorCode: Int) = finish(utteranceId, ok = false)
        })
    }

    private fun selectSpanish(): State {
        for (locale in listOf(Locale("es", "PE"), Locale("es", "419"), Locale("es", "ES"), Locale("es"))) {
            val result = tts.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                return State.READY
            }
        }
        return State.NO_SPANISH_VOICE
    }

    @Synchronized
    fun drain(queue: AnnouncementQueue, phrases: PhraseTemplateRenderer) {
        this.queue = queue
        this.phrases = phrases
        if (_state.value != State.READY) return
        val event = queue.next(Instant.now()) ?: return
        val text = phrases.phraseFor(event)
        if (text == null) {
            queue.markFailed(event.eventId)
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, event.eventId)
    }

    /** Prueba de voz desde la aplicación (no crea eventos ni historial). */
    fun test(text: String) {
        if (_state.value == State.READY) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "test")
    }

    private fun finish(utteranceId: String, ok: Boolean) {
        if (utteranceId == "test") return
        val q = queue ?: return
        val p = phrases ?: return
        synchronized(this) {
            if (ok) q.markAnnounced(utteranceId) else q.markFailed(utteranceId)
        }
        if (ok) drain(q, p)
    }
}
