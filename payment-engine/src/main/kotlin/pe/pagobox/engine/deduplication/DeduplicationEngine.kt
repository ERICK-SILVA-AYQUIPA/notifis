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
 * Evita anuncios duplicados cuando Android vuelve a publicar o actualiza la misma notificación.
 *
 * La huella combina paquete, clave de Android, marca de tiempo propia de la notificación
 * (`when`) y texto. Así:
 * - una actualización de la misma notificación (misma clave, mismo `when`, mismo texto) es duplicado;
 * - dos pagos reales con el mismo texto (mismo remitente y monto) tienen distinto `when`
 *   y NO se descartan.
 * Si la app de origen no informa `when`, se usa solo el texto dentro de la ventana.
 */
class DeduplicationEngine(
    private val store: SeenFingerprintStore,
    private val window: Duration = Duration.ofMinutes(30),
) {
    fun fingerprint(raw: RawNotification): String {
        val normalized = raw.fullText.lowercase().replace(Regex("\\s+"), " ").trim()
        val identity = listOfNotNull(raw.notificationKey, raw.whenMillis?.takeIf { it > 0 }?.toString()).joinToString("|")
        return sha256("${raw.packageName}|$identity|$normalized")
    }

    /** Devuelve true si el evento es nuevo (y lo registra); false si es duplicado. */
    fun registerIfNew(raw: RawNotification, now: Instant): Boolean {
        val fp = fingerprint(raw)
        val last = store.lastSeen(fp)
        val hasOwnTimestamp = raw.whenMillis != null && raw.whenMillis > 0
        // Con `when` propio, la huella identifica un evento único: se descarta siempre que se repita.
        if (last != null && (hasOwnTimestamp || Duration.between(last, now) <= window)) return false
        store.markSeen(fp, now)
        return true
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
