package pe.pagobox.engine.deduplication

import pe.pagobox.engine.source.RawNotification
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/** Almacén de huellas ya vistas. En Android se implementa sobre Room para sobrevivir reinicios. */
interface SeenFingerprintStore {
    fun lastSeen(fingerprint: String): Instant?
    fun markSeen(fingerprint: String, at: Instant)
}

class InMemorySeenFingerprintStore : SeenFingerprintStore {
    private val seen = mutableMapOf<String, Instant>()
    override fun lastSeen(fingerprint: String): Instant? = seen[fingerprint]
    override fun markSeen(fingerprint: String, at: Instant) {
        seen[fingerprint] = at
    }
}

/**
 * Evita anuncios duplicados cuando Android re-publica o actualiza la misma notificación.
 * La huella combina paquete y texto normalizado: dos pagos distintos del mismo monto y
 * remitente suelen diferir en el texto (hora, código), por eso no se usa solo el monto.
 */
class DeduplicationEngine(
    private val store: SeenFingerprintStore,
    private val window: Duration = Duration.ofMinutes(30),
) {
    fun fingerprint(raw: RawNotification): String {
        val normalized = raw.fullText.lowercase().replace(Regex("\\s+"), " ").trim()
        return sha256("${raw.packageName}|$normalized")
    }

    /** Devuelve true si el evento es nuevo (y lo registra); false si es duplicado. */
    fun registerIfNew(raw: RawNotification, now: Instant): Boolean {
        val fp = fingerprint(raw)
        val last = store.lastSeen(fp)
        if (last != null && Duration.between(last, now) <= window) return false
        store.markSeen(fp, now)
        return true
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
