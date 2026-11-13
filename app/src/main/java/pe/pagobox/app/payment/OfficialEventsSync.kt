package pe.pagobox.app.payment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pe.pagobox.app.data.preferences.SettingsRepository
import pe.pagobox.app.data.repositories.DiagnosticsLogger
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.engine.integrations.BackendIntegrationStatus
import pe.pagobox.engine.integrations.ProviderLiveStatus
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

    private val _integrations = MutableStateFlow<List<ProviderLiveStatus>>(emptyList())
    /** Estado por proveedor según el backend; vacío si no hay backend o no respondió. */
    val integrations: StateFlow<List<ProviderLiveStatus>> = _integrations.asStateFlow()

    fun start() {
        scope.launch {
            while (true) {
                runCatching { syncOnce() }.onFailure { log.error("Canal B", "Error al sincronizar eventos oficiales", it) }
                delay(intervalMs)
            }
        }
    }

    suspend fun syncOnce() = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        if (s.backendUrl.isBlank() || s.backendToken.isBlank()) {
            _state.value = OfficialSyncState(configured = false)
            _integrations.value = emptyList()
            return@withContext
        }
        val now = System.currentTimeMillis()
        _state.value = _state.value.copy(configured = true, lastAttemptAt = now)
        if (!s.backendUrl.startsWith("https://")) {
            _state.value = _state.value.copy(lastError = "La URL del backend no es HTTPS")
            return@withContext
        }
        val cursor = settings.officialCursor()
        val result = runCatching { fetch(s.backendUrl, s.backendToken, cursor) }
        result.onFailure { e ->
            _state.value = _state.value.copy(lastError = e.message ?: e.javaClass.simpleName)
            _integrations.value = emptyList() // sin respuesta no se muestra nada como activo
            return@withContext
        }
        val (environment, newCursor, events) = result.getOrThrow()
        if (events.isNotEmpty()) {
            processor.ingestOfficial(events, testMode = environment != "production")
            _state.value = _state.value.copy(lastEventAt = now)
        }
        settings.setOfficialCursor(newCursor)
        _state.value = _state.value.copy(environment = environment, lastSuccessAt = now, lastError = null)
        _integrations.value = runCatching { fetchIntegrations(s.backendUrl, s.backendToken) }
            .onFailure { log.warn("Canal B", "No se pudo leer el estado de integraciones: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private fun fetchIntegrations(baseUrl: String, token: String): List<ProviderLiveStatus> {
        val conn = URL("$baseUrl/api/v1/integrations").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (conn.responseCode != 200) error("Backend respondió HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val environment = json.getString("environment")
            val arr = json.getJSONArray("providers")
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val provider = runCatching { ProviderId.valueOf(o.getString("provider")) }.getOrNull() ?: return@mapNotNull null
                val status = runCatching { BackendIntegrationStatus.valueOf(o.getString("status")) }.getOrNull() ?: return@mapNotNull null
                ProviderLiveStatus(
                    provider = provider,
                    environment = environment,
                    status = status,
                    credentialsConfigured = o.optBoolean("credentialsConfigured", false),
                    webhookPath = o.optStringOrNull("webhookPath"),
                    lastCommunicationAt = o.optLongOrNull("lastCommunicationAt"),
                    lastEventAt = o.optLongOrNull("lastEventAt"),
                    lastError = o.optStringOrNull("lastError"),
                )
            }
        } finally {
            conn.disconnect()
        }
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

    private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
