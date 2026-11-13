package pe.pagobox.engine

import pe.pagobox.engine.deduplication.DeduplicationEngine
import pe.pagobox.engine.deduplication.InMemorySeenFingerprintStore
import pe.pagobox.engine.protocol.DeviceMessages
import pe.pagobox.engine.queue.AnnouncementQueue
import pe.pagobox.engine.source.RawNotification
import pe.pagobox.engine.source.SourcePackageRegistry
import pe.pagobox.engine.voice.PhraseTemplateRenderer
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Prueba de integración del flujo completo en el motor:
 * notificación -> pipeline -> cola de anuncios -> frase -> mensaje al equipo,
 * y contrato con el firmware mediante protocol-fixtures/ (el firmware prueba el mismo archivo).
 */
class EndToEndIntegrationTest {
    private val now = Instant.parse("2026-11-12T20:30:00Z")
    private val fixtures = File("../protocol-fixtures")

    @Test
    fun `de la notificacion al mensaje del equipo sin duplicados`() {
        var seq = 0L
        val pipeline = PaymentPipeline(
            registry = SourcePackageRegistry(listOf(TestFixtures.testAdapter)),
            deduplication = DeduplicationEngine(InMemorySeenFingerprintStore()),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            nextSequence = { ++seq },
        )
        val queue = AnnouncementQueue()
        val raw = RawNotification(
            packageName = TestFixtures.TEST_PACKAGE, title = null,
            text = "PRUEBA: José Ñúñez te envió S/ 1,250.50", postedAt = now,
            notificationKey = "k", whenMillis = now.toEpochMilli(),
        )

        val event = assertIs<PipelineResult.Created>(pipeline.process(raw)).event
        // Android vuelve a publicar la misma notificación: no genera un segundo evento.
        assertIs<PipelineResult.Duplicate>(pipeline.process(raw))

        queue.enqueue(event)
        val next = queue.next(now)!!
        assertEquals(
            "Se detectó un posible ingreso de 1250 soles con 50 céntimos",
            PhraseTemplateRenderer().phraseFor(next),
        )
        assertEquals(File(fixtures, "payment_event.json").readText(), DeviceMessages.paymentEvent(next))

        queue.markAnnounced(next.eventId)
        // Reconexión: el mismo evento no vuelve a anunciarse.
        queue.enqueue(event)
        assertNull(queue.next(now))
    }

    @Test
    fun `mensaje de prueba de entrega coincide con el contrato`() {
        assertEquals(File(fixtures, "test_event.json").readText(), DeviceMessages.testEvent("test_1731443400000"))
    }
}
