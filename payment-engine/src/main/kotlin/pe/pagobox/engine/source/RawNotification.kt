package pe.pagobox.engine.source

import java.time.Instant

/**
 * Notificación tal como llega de Android, ya extraída del StatusBarNotification.
 * Vive solo en memoria durante el procesamiento: no se persiste.
 */
data class RawNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String? = null,
    val postedAt: Instant,
) {
    /** Texto completo disponible, priorizando el texto expandido. */
    val fullText: String
        get() = listOfNotNull(title, bigText ?: text).joinToString(" \n ").trim()
}
