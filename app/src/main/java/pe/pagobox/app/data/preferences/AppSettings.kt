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
    }
}
