package pe.pagobox.app.payment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import pe.pagobox.app.data.preferences.SettingsRepository
import pe.pagobox.app.data.repositories.DiagnosticsLogger
import java.net.HttpURLConnection
import java.net.URL

/** Evento oficial tal como lo entrega el backend (`GET /api/v1/events`). */
data class OfficialEventDto(
    val eventId: String,
    val provider: String,
    val source: String,
    val externalTransactionId: String,
    val eventType: String,
    val amount: String?,
    val currency: String?,
    val sender: String?,
    val recipient: String?,
    val eventTimestamp: Long,
    val verificationStatus: String,
    val providerStatus: String,
)

/** Estado visible en el panel de integraciones. */
data class OfficialSyncState(
    val configured: Boolean = false,
    val environment: String? = null,
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    val lastEventAt: Long? = null,
)

/**
 * Canal B → celular. Consulta periódicamente el backend propio (nunca al proveedor: las credenciales del
 * proveedor no existen en la app) y entrega los eventos al [PaymentProcessor], que los une con los de Android.
 * Solo funciona con HTTPS y un token de dispositivo. Mientras el proceso de la app esté vivo consulta cada
 * [intervalMs]; no hay notificaciones push (requeriría Firebase), así que la latencia es la del intervalo.
 */
class OfficialEventsSync(
    private val settings: SettingsRepository,
    private val processor: PaymentProcessor,
    private val log: DiagnosticsLogger,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 20_000,
) {
    private val _state = MutableStateFlow(OfficialSyncState())
    val state: StateFlow<OfficialSyncState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            while (true) {
                runCatching { syncOnce() }.onFailure { log.error("Canal B", "Error al sincronizar eventos oficiales", it) }
                delay(intervalMs)
            }
        }
    }

    suspend fun syncOnce() {
        val s = settings.settings.first()
        if (s.backendUrl.isBlank() || s.backendToken.isBlank()) {
            _state.value = OfficialSyncState(configured = false)
            return
        }
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(configured = true, lastAttemptAt = now)
        if (!s.backendUrl.startsWith("https://")) {
            _state.value = _state.value.copy(lastError = "La URL del backend no es HTTPS")
            return
        }
        val cursor = settings.officialCursor()
        val result = runCatching { fetch(s.backendUrl, s.backendToken, cursor) }
        result.onFailure { e ->
            _state.value = _state.value.copy(lastError = e.message ?: e.javaClass.simpleName)
            return
        }
        val (environment, newCursor, events) = result.getOrThrow()
        if (events.isNotEmpty()) {
            processor.ingestOfficial(events, testMode = environment != "production")
            _state.value = _state.value.copy(lastEventAt = now)
        }
        settings.setOfficialCursor(newCursor)
        _state.value = _state.value.copy(environment = environment, lastSuccessAt = now, lastError = null)
    }

    private fun fetch(baseUrl: String, token: String, after: Long): Triple<String, Long, List<OfficialEventDto>> {
        val conn = URL("$baseUrl/api/v1/events?after=$after&limit=100").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            if (code == 401) error("Token rechazado por el backend")
            if (code != 200) error("Backend respondió HTTP $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val arr = json.getJSONArray("events")
            val events = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                OfficialEventDto(
                    eventId = o.getString("eventId"),
                    provider = o.getString("provider"),
                    source = o.getString("source"),
                    externalTransactionId = o.getString("externalTransactionId"),
                    eventType = o.getString("eventType"),
                    amount = o.optStringOrNull("amount"),
                    currency = o.optStringOrNull("currency"),
                    sender = o.optStringOrNull("sender"),
                    recipient = o.optStringOrNull("recipient"),
                    eventTimestamp = o.getLong("eventTimestamp"),
                    verificationStatus = o.getString("verificationStatus"),
                    providerStatus = o.getString("providerStatus"),
                )
            }
            return Triple(json.getString("environment"), json.getLong("cursor"), events)
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
