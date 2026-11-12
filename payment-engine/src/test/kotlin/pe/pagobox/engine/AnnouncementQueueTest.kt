package pe.pagobox.engine

import pe.pagobox.engine.queue.AnnouncementQueue
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnouncementQueueTest {
    private val now = Instant.parse("2026-11-12T20:30:00Z")

    @Test
    fun `orden de llegada y sin interrumpir el anuncio en curso`() {
        val q = AnnouncementQueue()
        q.enqueue(TestFixtures.event("e2", 2))
        q.enqueue(TestFixtures.event("e1", 1))
        assertEquals("e1", q.next(now)?.eventId)
        assertNull(q.next(now), "No debe entregar otro mientras hay uno en curso")
        q.markAnnounced("e1")
        assertEquals("e2", q.next(now)?.eventId)
    }

    @Test
    fun `un evento anunciado nunca se repite al reconectar`() {
        val q = AnnouncementQueue()
        q.enqueue(TestFixtures.event("e1", 1))
        q.next(now)
        q.markAnnounced("e1")
        assertFalse(q.enqueue(TestFixtures.event("e1", 1)))
        assertEquals(0, q.size)
    }

    @Test
    fun `fallo de entrega reintenta el mismo evento primero`() {
        val q = AnnouncementQueue()
        q.enqueue(TestFixtures.event("e1", 1))
        q.enqueue(TestFixtures.event("e2", 2))
        q.next(now)
        q.markFailed("e1")
        assertEquals("e1", q.next(now)?.eventId)
    }

    @Test
    fun `silencio temporal conserva los eventos`() {
        val q = AnnouncementQueue()
        q.enqueue(TestFixtures.event("e1", 1))
        q.muteUntil(now.plusSeconds(60))
        assertTrue(q.isMuted(now))
        assertNull(q.next(now))
        assertEquals("e1", q.next(now.plusSeconds(61))?.eventId)
    }

    @Test
    fun `prioridad configurable`() {
        val q = AnnouncementQueue(priorityOf = { if (it.eventId == "vip") 10 else 0 })
        q.enqueue(TestFixtures.event("e1", 1))
        q.enqueue(TestFixtures.event("vip", 2))
        assertEquals("vip", q.next(now)?.eventId)
    }

    @Test
    fun `restaurar anunciados tras reinicio`() {
        val q = AnnouncementQueue()
        q.enqueue(TestFixtures.event("e1", 1))
        q.restoreAnnounced(listOf("e1"))
        assertEquals(0, q.size)
    }
}
