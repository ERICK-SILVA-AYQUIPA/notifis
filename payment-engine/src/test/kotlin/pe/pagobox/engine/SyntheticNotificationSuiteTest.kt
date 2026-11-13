package pe.pagobox.engine

import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.Currency
import pe.pagobox.domain.model.SourceChannel
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.deduplication.DeduplicationEngine
import pe.pagobox.engine.deduplication.InMemorySeenFingerprintStore
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

/**
 * Fase 4: suite con notificaciones SINTÉTICAS anonimizadas (formato ficticio «PRUEBA:»).
 * Verifica el comportamiento del motor; no prueba la compatibilidad con ninguna entidad real.
 */
class SyntheticNotificationSuiteTest {
    private val now = Instant.parse("2026-11-12T20:30:00Z")
    private var seq = 0L
    private val pipeline = PaymentPipeline(
        registry = SourcePackageRegistry(listOf(TestFixtures.testAdapter)),
        deduplication = DeduplicationEngine(InMemorySeenFingerprintStore()),
        clock = Clock.fixed(now, ZoneOffset.UTC),
        nextSequence = { ++seq },
    )

    private fun notif(text: String, at: Instant = now, key: String? = null, whenMs: Long? = null) = RawNotification(
        packageName = TestFixtures.TEST_PACKAGE,
        title = "Billetera de prueba",
        text = text,
        postedAt = at,
        notificationKey = key,
        whenMillis = whenMs,
    )

    private fun created(text: String) = assertIs<PipelineResult.Created>(pipeline.process(notif(text))).event

    @Test
    fun `1 pago recibido se detecta como candidato y nunca como verificado`() {
        val e = created("PRUEBA: Ana te envió S/ 20")
        assertEquals(VerificationStatus.INCOME_CANDIDATE, e.verificationStatus)
        assertEquals(SourceChannel.ANDROID_NOTIFICATION, e.sourceChannel)
        assertEquals(AnnouncementStatus.PENDING, e.announcementStatus)
        assertEquals(BigDecimal("20.00"), e.money!!.amount)
    }

    @Test
    fun `2 dinero enviado se rechaza`() {
        assertIs<PipelineResult.Rejected>(pipeline.process(notif("PRUEBA: enviaste S/ 50.00")))
    }

    @Test
    fun `2b regla mal declarada como ingreso pero con texto de egreso se rechaza`() {
        val r = assertIs<PipelineResult.Rejected>(pipeline.process(notif("PRUEBA-MAL: pagaste S/ 50")))
        assertTrue("egreso" in r.reason)
    }

    @Test
    fun `3 solicitud de dinero se rechaza`() {
        assertIs<PipelineResult.Rejected>(pipeline.process(notif("PRUEBA: Luis te solicita S/ 30")))
    }

    @Test
    fun `4 notificacion duplicada se descarta`() {
        val n = notif("PRUEBA: Ana te envió S/ 20", key = "k1", whenMs = now.toEpochMilli())
        assertIs<PipelineResult.Created>(pipeline.process(n))
        assertIs<PipelineResult.Duplicate>(pipeline.process(n))
    }

    @Test
    fun `4b dos pagos reales con el mismo texto no se confunden con duplicados`() {
        val first = notif("PRUEBA: Ana te envió S/ 20", key = "k1", whenMs = now.toEpochMilli() - 60_000)
        val second = notif("PRUEBA: Ana te envió S/ 20", key = "k2", whenMs = now.toEpochMilli())
        assertIs<PipelineResult.Created>(pipeline.process(first))
        assertIs<PipelineResult.Created>(pipeline.process(second))
    }

    @Test
    fun `5 importe decimal y grande`() {
        assertEquals(BigDecimal("35.50"), created("PRUEBA: Ana te envió S/ 35.50").money!!.amount)
        assertEquals(BigDecimal("12345.60"), created("PRUEBA: Ana te envió S/ 12,345.6").money!!.amount)
    }

    @Test
    fun `6 notificacion incompleta queda pendiente de revision y no se anuncia`() {
        val e = created("PRUEBA: recibiste un pago")
        assertEquals(VerificationStatus.PENDING_VERIFICATION, e.verificationStatus)
        assertEquals(AnnouncementStatus.SKIPPED, e.announcementStatus)
        assertEquals(null, e.money)
    }

    @Test
    fun `7 notificacion desconocida de la entidad se registra como formato desconocido`() {
        assertIs<PipelineResult.UnknownFormat>(pipeline.process(notif("¡Gana premios con tu billetera!")))
    }

    @Test
    fun `8 nombre con caracteres especiales se conserva`() {
        assertEquals("José Ñúñez-O'Higgins", created("PRUEBA: José Ñúñez-O'Higgins te envió S/ 10").senderName)
    }

    @Test
    fun `9 distintos formatos de moneda`() {
        assertEquals(Currency.PEN, created("PRUEBA: Ana te envió S/. 10").money!!.currency)
        assertEquals(Currency.PEN, created("PRUEBA: Ana te envió PEN 11.00").money!!.currency)
        assertEquals(Currency.USD, created("PRUEBA: Ana te envió US$ 12").money!!.currency)
        assertEquals(Currency.USD, created("PRUEBA: Ana te envió USD 13.50").money!!.currency)
    }

    @Test
    fun `9b importe ambiguo no se inventa`() {
        val e = created("PRUEBA: Ana te envió S/ 1,25")
        assertEquals(null, e.money)
        assertEquals(VerificationStatus.PENDING_VERIFICATION, e.verificationStatus)
    }

    @Test
    fun `10 notificaciones antiguas o con fecha futura se rechazan`() {
        assertIs<PipelineResult.Rejected>(pipeline.process(notif("PRUEBA: Ana te envió S/ 20", whenMs = now.minusSeconds(3600).toEpochMilli())))
        assertIs<PipelineResult.Rejected>(pipeline.process(notif("PRUEBA: Bea te envió S/ 20", whenMs = now.plusSeconds(3600).toEpochMilli())))
    }

    @Test
    fun `el texto de lineas adicionales tambien se analiza`() {
        val n = RawNotification(
            packageName = TestFixtures.TEST_PACKAGE, title = "Billetera", text = null,
            textLines = listOf("PRUEBA: Ana te envió S/ 15"), postedAt = now,
        )
        assertIs<PipelineResult.Created>(pipeline.process(n))
    }
}
