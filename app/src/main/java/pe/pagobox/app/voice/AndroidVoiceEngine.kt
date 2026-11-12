package pe.pagobox.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import pe.pagobox.app.data.local.VoiceProfileEntity
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Voz disponible en el motor TTS. [offline] indica que no necesita Internet. */
data class VoiceOption(val name: String, val locale: String, val offline: Boolean, val quality: Int)

/**
 * Motor de voz local sobre el TTS de Android (sección 2.4).
 * - Solo voces en español; informa si no hay ninguna instalada.
 * - Un anuncio a la vez: [speak] espera a que termine la frase (no se interrumpe).
 * - [synthesizeToFile] genera el audio que se envía al equipo PAGOBOX.
 */
class AndroidVoiceEngine(context: Context) {
    enum class State { INITIALIZING, READY, NO_SPANISH_VOICE, UNAVAILABLE }

    private val _state = MutableStateFlow(State.INITIALIZING)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val mutex = Mutex()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        _state.value = if (status != TextToSpeech.SUCCESS) State.UNAVAILABLE else initSpanish()
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                pending.remove(utteranceId)?.complete(true)
            }

            @Deprecated("Requerido por la API")
            override fun onError(utteranceId: String) {
                pending.remove(utteranceId)?.complete(false)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                pending.remove(utteranceId)?.complete(false)
            }
        })
    }

    private fun initSpanish(): State {
        val spanish = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == "es" }
        _voices.value = spanish
            .map { VoiceOption(it.name, it.locale.toLanguageTag(), !it.isNetworkConnectionRequired, it.quality) }
            .sortedWith(compareByDescending<VoiceOption> { it.offline }.thenBy { it.locale }.thenBy { it.name })
        for (locale in listOf(Locale("es", "PE"), Locale("es", "US"), Locale("es", "MX"), Locale("es", "ES"), Locale("es"))) {
            val result = tts.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                return State.READY
            }
        }
        return State.NO_SPANISH_VOICE
    }

    private fun apply(profile: VoiceProfileEntity) {
        profile.voiceName?.let { name ->
            tts.voices?.firstOrNull { it.name == name }?.let { voice: Voice -> tts.voice = voice }
        }
        tts.setSpeechRate(profile.speechRate)
        tts.setPitch(profile.pitch)
    }

    /** Reproduce en el celular y espera a que termine. Devuelve false si falló. */
    suspend fun speak(text: String, profile: VoiceProfileEntity): Boolean = mutex.withLock {
        if (_state.value != State.READY) return false
        apply(profile)
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>().also { pending[id] = it }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, profile.volume.coerceIn(0, 100) / 100f)
        }
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, params, id) != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return false
        }
        withTimeoutOrNull(SPEAK_TIMEOUT_MS) { done.await() } ?: false.also { pending.remove(id) }
    }

    /** Genera un archivo WAV con la frase para enviarlo al equipo. */
    suspend fun synthesizeToFile(text: String, profile: VoiceProfileEntity, file: File): Boolean = mutex.withLock {
        if (_state.value != State.READY) return false
        apply(profile)
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>().also { pending[id] = it }
        if (tts.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return false
        }
        (withTimeoutOrNull(SPEAK_TIMEOUT_MS) { done.await() } ?: false.also { pending.remove(id) }) &&
            file.length() > 0
    }

    private companion object {
        const val SPEAK_TIMEOUT_MS = 20_000L
    }
}
