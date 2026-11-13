package pe.pagobox.engine

import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.correlation.Correlation
import pe.pagobox.engine.correlation.CrossChannelCorrelator
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrossChannelCorrelatorTest {
    private val correlator = CrossChannelCorrelator()
    private val t = Instant.parse("2026-11-12T20:30:00Z")

    private fun android(id: String, amount: String = "25.00", sender: String? = "José Núñez", at: Instant = t, source: SourceId = SourceId.YAPE) =
        TestFixtures.event(id, 1, amount, source, sender, at)

    private fun official(sender: String? = "JOSE NUNEZ", amount: String = "25.00") =
        TestFixtures.officialEvent("w1", amount = amount).copy(senderName = sender, eventTimestamp = t.plusSeconds(40))

    @Test
    fun `fusiona solo con importe, entidad, hora, remitente y candidato unico`() {
        val r = correlator.correlate(official(), listOf(android("a1")))
        assertIs<Correlation.SamePayment>(r)
        assertEquals("a1", r.androidEventId)
    }

    @Test
    fun `importe y hora iguales sin remitente no bastan para fusionar`() {
        val r = correlator.correlate(official(sender = null), listOf(android("a1")))
        assertIs<Correlation.PossibleMatch>(r)
        assertEquals(listOf("a1"), r.androidEventIds)
        assertIs<Correlation.PossibleMatch>(correlator.correlate(official(), listOf(android("a1", sender = null))))
    }

    @Test
    fun `dos pagos iguales en la ventana son ambiguos`() {
        val r = correlator.correlate(official(), listOf(android("a1"), android("a2", at = t.plusSeconds(30))))
        assertIs<Correlation.PossibleMatch>(r)
        assertEquals(2, r.androidEventIds.size)
    }

    @Test
    fun `no coincide con otro importe, otra entidad, remitente distinto o fuera de la ventana`() {
        assertEquals(Correlation.NoMatch, correlator.correlate(official(), listOf(android("a1", amount = "25.01"))))
        assertEquals(Correlation.NoMatch, correlator.correlate(official(), listOf(android("a1", source = SourceId.PLIN))))
        assertEquals(Correlation.NoMatch, correlator.correlate(official(), listOf(android("a1", sender = "Ana Pérez"))))
        assertEquals(Correlation.NoMatch, correlator.correlate(official(), listOf(android("a1", at = t.minusSeconds(600)))))
    }

    @Test
    fun `solo se comparan posibles ingresos Android contra eventos oficiales`() {
        val rejected = android("a1").copy(verificationStatus = VerificationStatus.REJECTED)
        assertEquals(Correlation.NoMatch, correlator.correlate(official(), listOf(rejected)))
        assertEquals(Correlation.NoMatch, correlator.correlate(android("x"), listOf(android("a1"))))
        val test = official().copy(testMode = true)
        assertEquals(Correlation.NoMatch, correlator.correlate(test, listOf(android("a1"))), "Un pago de prueba nunca absorbe uno real")
    }
}
