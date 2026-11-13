package pe.pagobox.engine

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.compatibility.RuntimeOverride
import pe.pagobox.engine.deduplication.DeduplicationEngine
import pe.pagobox.engine.deduplication.InMemorySeenFingerprintStore
import pe.pagobox.engine.source.KnownSources
import pe.pagobox.engine.source.RawNotification
import pe.pagobox.engine.source.SourcePackageRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentPipelineTest {
    private val now = Instant.parse("2026-11-12T20:30:00Z")

    private fun pipeline(registry: SourcePackageRegistry): PaymentPipeline {
        var seq = 0L
        return PaymentPipeline(
            registry = registry,
            deduplication = DeduplicationEngine(InMemorySeenFingerprintStore()),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            nextSequence = { ++seq },
        )
    }

    private fun notif(pkg: String, text: String) = RawNotification(packageName = pkg, title = null, text = text, postedAt = now)

    @Test
    fun `ninguna entidad real procesa pagos sin formatos reales`() {
        val registry = SourcePackageRegistry(KnownSources.all())
        assertTrue(registry.supervisedPackages.isEmpty())
        registry.statuses.values.forEach { assertEquals(CompatibilityStatus.NOT_CONFIGURED, it) }
    }

    @Test
    fun `paquete asignado a una entidad sin formatos solo recopila muestras`() {
        val registry = SourcePackageRegistry(KnownSources.all(), assignedPackages = mapOf("app.asignada" to SourceId.YAPE))
        assertEquals(CompatibilityStatus.WAITING_FOR_SAMPLE, registry.statuses[SourceId.YAPE])
        assertNull(registry.adapterFor("app.asignada"))
        val result = pipeline(registry).process(notif("app.asignada", "cualquier texto"))
        assertIs<PipelineResult.NotProcessed>(result)
    }

    @Test
    fun `entidad desactivada temporalmente no procesa`() {
        val registry = SourcePackageRegistry(
            listOf(TestFixtures.testAdapter),
            overrides = mapOf(SourceId.OTHER to RuntimeOverride.TEMPORARILY_UNAVAILABLE),
        )
        assertIs<PipelineResult.NotProcessed>(pipeline(registry).process(notif(TestFixtures.TEST_PACKAGE, "PRUEBA: Ana te envió S/ 20")))
    }

    @Test
    fun `formato cambiado sigue procesando lo reconocido`() {
        val registry = SourcePackageRegistry(
            listOf(TestFixtures.testAdapter),
            overrides = mapOf(SourceId.OTHER to RuntimeOverride.FORMAT_CHANGED),
        )
        assertEquals(CompatibilityStatus.FORMAT_CHANGED, registry.statuses[SourceId.OTHER])
        assertIs<PipelineResult.Created>(pipeline(registry).process(notif(TestFixtures.TEST_PACKAGE, "PRUEBA: Ana te envió S/ 20")))
    }

    @Test
    fun `paquetes no asociados se ignoran`() {
        val registry = SourcePackageRegistry(listOf(TestFixtures.testAdapter))
        assertIs<PipelineResult.Ignored>(pipeline(registry).process(notif("com.otra.app", "PRUEBA: Ana te envió S/ 20")))
    }
}
