package pe.pagobox.app.data.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.pagobox.app.data.local.AppSettingDao
import pe.pagobox.app.data.local.AppSettingEntity
import java.time.Instant

/** Dónde se reproducen los anuncios. */
enum class AnnouncementOutput(val label: String) {
    PHONE("Solo en el celular"),
    DEVICE("Solo en el equipo PAGOBOX"),
    BOTH("Celular y equipo"),
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val businessName: String = "",
    val businessAddress: String = "",
    val businessPhone: String = "",
    val output: AnnouncementOutput = AnnouncementOutput.PHONE,
    /** Si el equipo no responde, anunciar en el celular para no perder el aviso. */
    val phoneFallback: Boolean = true,
    val mutedUntil: Instant? = null,
    /** Días que se conserva el historial (privacidad, sección 5). */
    val retentionDays: Int = 90,
    /** Fin del modo de diagnóstico de notificaciones (captura de todas las apps), o null si está apagado. */
    val diagnosticUntil: Instant? = null,
    /** URL HTTPS del backend de integraciones oficiales (Canal B); vacío = no configurado. */
    val backendUrl: String = "",
    /** Token de este celular para leer eventos oficiales (lo entrega quien administra el backend). */
    val backendToken: String = "",
)

/** Configuración general persistida en la tabla AppSettings (clave-valor). */
class SettingsRepository(private val dao: AppSettingDao) {
    val settings: Flow<AppSettings> = dao.observeAll().map { rows ->
        val m = rows.associate { it.key to it.value }
        AppSettings(
            onboardingDone = m[ONBOARDING] == "true",
            businessName = m[BUSINESS_NAME].orEmpty(),
            businessAddress = m[BUSINESS_ADDRESS].orEmpty(),
            businessPhone = m[BUSINESS_PHONE].orEmpty(),
            output = m[OUTPUT]?.let { runCatching { AnnouncementOutput.valueOf(it) }.getOrNull() }
                ?: AnnouncementOutput.PHONE,
            phoneFallback = m[PHONE_FALLBACK]?.toBooleanStrictOrNull() ?: true,
            mutedUntil = m[MUTED_UNTIL]?.toLongOrNull()?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            retentionDays = m[RETENTION_DAYS]?.toIntOrNull() ?: 90,
            diagnosticUntil = m[DIAGNOSTIC_UNTIL]?.toLongOrNull()?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            backendUrl = m[BACKEND_URL].orEmpty(),
            backendToken = m[BACKEND_TOKEN].orEmpty(),
        )
    }

    suspend fun setOnboardingDone() = put(ONBOARDING, "true")
    suspend fun setBusiness(name: String, address: String, phone: String) {
        put(BUSINESS_NAME, name.trim())
        put(BUSINESS_ADDRESS, address.trim())
        put(BUSINESS_PHONE, phone.trim())
    }
    suspend fun setOutput(output: AnnouncementOutput) = put(OUTPUT, output.name)
    suspend fun setPhoneFallback(enabled: Boolean) = put(PHONE_FALLBACK, enabled.toString())
    suspend fun setMutedUntil(until: Instant?) = put(MUTED_UNTIL, (until?.toEpochMilli() ?: 0L).toString())
    suspend fun setRetentionDays(days: Int) = put(RETENTION_DAYS, days.coerceIn(7, 365).toString())

    suspend fun setDiagnosticUntil(until: Instant?) = put(DIAGNOSTIC_UNTIL, (until?.toEpochMilli() ?: 0L).toString())

    /** Solo se acepta HTTPS: el token nunca viaja en claro. */
    suspend fun setBackend(url: String, token: String) {
        val clean = url.trim().trimEnd('/')
        require(clean.isEmpty() || clean.startsWith("https://")) { "La URL del backend debe empezar con https://" }
        put(BACKEND_URL, clean)
        put(BACKEND_TOKEN, token.trim())
        put(OFFICIAL_CURSOR, "0")
    }

    suspend fun officialCursor(): Long = dao.get(OFFICIAL_CURSOR)?.toLongOrNull() ?: 0L
    suspend fun setOfficialCursor(cursor: Long) = put(OFFICIAL_CURSOR, cursor.toString())

    /**
     * Secuencia de eventos monotónica y persistente: nunca vuelve a empezar aunque se borre el
     * historial, y nunca queda por debajo de [minimum] (lo guardado y lo confirmado por el equipo).
     * Corrige el error E1 de la auditoría. Se consulta con [peekNextSequence] y solo se fija con
     * [commitSequence] cuando de verdad se crea un evento (el procesador trabaja en serie).
     */
    suspend fun peekNextSequence(minimum: Long): Long {
        val current = dao.get(SEQUENCE)?.toLongOrNull() ?: 0L
        return maxOf(current, minimum) + 1
    }

    suspend fun commitSequence(sequence: Long) {
        val current = dao.get(SEQUENCE)?.toLongOrNull() ?: 0L
        if (sequence > current) put(SEQUENCE, sequence.toString())
    }

    private suspend fun put(key: String, value: String) =
        dao.upsert(AppSettingEntity(key, value, System.currentTimeMillis()))

    private companion object {
        const val ONBOARDING = "onboarding_done"
        const val BUSINESS_NAME = "business_name"
        const val BUSINESS_ADDRESS = "business_address"
        const val BUSINESS_PHONE = "business_phone"
        const val OUTPUT = "announcement_output"
        const val PHONE_FALLBACK = "phone_fallback"
        const val MUTED_UNTIL = "muted_until"
        const val RETENTION_DAYS = "retention_days"
        const val DIAGNOSTIC_UNTIL = "diagnostic_until"
        const val SEQUENCE = "event_sequence"
        const val BACKEND_URL = "backend_url"
        const val BACKEND_TOKEN = "backend_token"
        const val OFFICIAL_CURSOR = "official_cursor"
    }
}
