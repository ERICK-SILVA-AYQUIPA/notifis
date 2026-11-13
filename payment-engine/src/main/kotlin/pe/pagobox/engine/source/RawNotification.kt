package pe.pagobox.engine.source

import java.time.Instant

/**
 * Notificación tal como llega de Android. Vive solo en memoria durante el procesamiento:
 * nunca se persiste en crudo (ver NotificationAnonymizer para muestras).
 */
data class RawNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String? = null,
    val textLines: List<String> = emptyList(),
    val subText: String? = null,
    /** Momento en que Android publicó la notificación. */
    val postedAt: Instant,
    /** Clave única de Android (StatusBarNotification.key), si existe. */
    val notificationKey: String? = null,
    /** Marca de tiempo propia de la notificación (Notification.when), en ms. */
    val whenMillis: Long? = null,
) {
    /** Texto completo disponible, priorizando el texto expandido. */
    val fullText: String
        get() = buildList {
            title?.let(::add)
            add(bigText ?: text ?: "")
            addAll(textLines)
            subText?.let(::add)
        }.filter { it.isNotBlank() }.joinToString(" \n ").trim()
}
