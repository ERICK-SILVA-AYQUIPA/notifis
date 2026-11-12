package pe.pagobox.engine.queue

import pe.pagobox.domain.model.PaymentEvent
import java.time.Instant

/**
 * Cola de anuncios (sección 2.6):
 * - Orden de llegada (por número de secuencia), con prioridad configurable.
 * - Nunca entrega dos veces el mismo eventId, aunque se reencole al reconectar.
 * - Silencio temporal: los eventos se conservan y se entregan al terminar el silencio.
 * - Un anuncio en curso no se interrumpe: [next] no entrega otro hasta [markAnnounced]/[markFailed].
 */
class AnnouncementQueue(
    private val priorityOf: (PaymentEvent) -> Int = { 0 },
) {
    private val pending = mutableListOf<PaymentEvent>()
    private val announcedIds = mutableSetOf<String>()
    private var inProgress: PaymentEvent? = null
    private var mutedUntil: Instant? = null

    val size: Int get() = pending.size
    val current: PaymentEvent? get() = inProgress

    /** Devuelve false si el evento ya fue anunciado o ya está en cola. */
    fun enqueue(event: PaymentEvent): Boolean {
        if (event.eventId in announcedIds) return false
        if (pending.any { it.eventId == event.eventId } || inProgress?.eventId == event.eventId) return false
        pending += event
        pending.sortWith(compareByDescending<PaymentEvent> { priorityOf(it) }.thenBy { it.sequence })
        return true
    }

    fun muteUntil(until: Instant?) {
        mutedUntil = until
    }

    fun isMuted(now: Instant): Boolean = mutedUntil?.let { now.isBefore(it) } ?: false

    /** Siguiente evento a anunciar, o null si hay uno en curso, está silenciado o no hay pendientes. */
    fun next(now: Instant): PaymentEvent? {
        if (inProgress != null || isMuted(now) || pending.isEmpty()) return null
        return pending.removeAt(0).also { inProgress = it }
    }

    fun markAnnounced(eventId: String) {
        check(inProgress?.eventId == eventId) { "El evento $eventId no está en reproducción" }
        announcedIds += eventId
        inProgress = null
    }

    /** Falla de entrega: el evento vuelve al frente de la cola para reintentarse. */
    fun markFailed(eventId: String) {
        val event = inProgress
        check(event?.eventId == eventId) { "El evento $eventId no está en reproducción" }
        inProgress = null
        pending.add(0, event!!)
    }

    /** Restaura los ids ya anunciados (desde Room) tras un reinicio. */
    fun restoreAnnounced(ids: Collection<String>) {
        announcedIds += ids
        pending.removeAll { it.eventId in announcedIds }
    }
}
