package pe.pagobox.engine

import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.deduplication.DeduplicationEngine
import pe.pagobox.engine.deduplication.InMemorySeenFingerprintStore
import pe.pagobox.engine.source.KnownSources
import pe.pagobox.engine.source.RawNotification
import pe.pagobox.engine.source.SourcePackageRegistry
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PaymentPipelineTest {
    private val now = Instant.parse("2026-11-12T20:30:00Z")
    private var seq = 0L
    private val pipeline = PaymentPipeline(
        registry = SourcePackageRegistry(listOf(TestFixtures.testAdapter)),
        deduplication = DeduplicationEngine(InMemorySeenFingerprintStore()),
        clock = Clock.fixed(now, ZoneOffset.UTC),
        nextSequence = { ++seq },
    )

    private fun notif(text: String, pkg: String = TestFixtures.TEST_PACKAGE, at: Instant = now) =
        RawNotification(packageName = pkg, title = "Billetera de prueba", text = text, postedAt = at)

    @Test
    fun `ingreso reconocido queda como candidato, nunca verificado`() {
        val result = pipeline.process(notif("PRUEBA: José Ñúñez te envió S/ 1,250.50"))
        val created = assertIs<PipelineResult.Created>(result)
        assertEquals(EventState.INCOME_CANDIDATE, created.event.state)
        assertEquals(VerificationStatus.DETECTED, created.event.verificationStatus)
        assertEquals(AnnouncementStatus.PENDING, created.event.announcementStatus)
        assertEquals(BigDecimal("1250.50"), created.event.money!!.amount)
        assertEquals("José Ñúñez", created.event.senderName)
        assertEquals("evt_000001", created.event.eventId)
    }

    @Test
    fun `la misma notificacion repetida es duplicado`() {
        assertIs<PipelineResult.Created>(pipeline.process(notif("PRUEBA: Ana te envió S/ 20")))
        assertIs<PipelineResult.Duplicate>(pipeline.process(notif("PRUEBA: Ana te envió S/ 20")))
    }

    @Test
    fun `egresos, solicitudes, publicidad y formatos desconocidos se rechazan`() {
        assertIs<PipelineResult.Rejected>(pipeline.process(notif("PRUEBA: enviaste S/ 50")))
        assertIs<PipelineResult.Rejected>(pipeline.process(notif("PRUEBA: Luis te solicita S/ 30")))
        assertIs<PipelineResult.Rejected>(pipeline.process(notif("¡Gana premios con tu billetera!")))
    }

    @Test
    fun `notificacion antigua se rechaza`() {
        val old = notif("PRUEBA: Ana te envió S/ 20", at = now.minusSeconds(3600))
        assertIs<PipelineResult.Rejected>(pipeline.process(old))
    }

    @Test
    fun `ingreso sin importe queda pendiente de revision y no se anuncia`() {
        val created = assertIs<PipelineResult.Created>(pipeline.process(notif("PRUEBA: recibiste un pago")))
        assertEquals(EventState.PENDING_REVIEW, created.event.state)
        assertEquals(AnnouncementStatus.SKIPPED, created.event.announcementStatus)
    }

    @Test
    fun `paquetes no supervisados se ignoran`() {
        assertIs<PipelineResult.Ignored>(pipeline.process(notif("PRUEBA: Ana te envió S/ 20", pkg = "com.otra.app")))
    }

    @Test
    fun `ninguna entidad real se declara compatible sin reglas verificadas`() {
        assertTrue(KnownSources.all().all { it.compatibility == CompatibilityStatus.NOT_IMPLEMENTED })
        assertTrue(SourcePackageRegistry(KnownSources.all()).supervisedPackages.isEmpty())
    }
}
