package pe.pagobox.engine

import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.voice.PhraseTemplateRenderer
import pe.pagobox.engine.voice.SpokenAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceTest {
    private val renderer = PhraseTemplateRenderer()

    @Test
    fun `importes hablados en soles`() {
        assertEquals("20 soles", SpokenAmount.of(Money.pen("20")))
        assertEquals("1 sol", SpokenAmount.of(Money.pen("1")))
        assertEquals("35 soles con 50 céntimos", SpokenAmount.of(Money.pen("35.50")))
        assertEquals("0 soles con 5 céntimos", SpokenAmount.of(Money.pen("0.05")))
        assertEquals("1,250.50", SpokenAmount.display(Money.pen("1250.50")))
    }

    @Test
    fun `frases predeterminadas por entidad`() {
        assertEquals("¡Te yapearon 20 soles!", renderer.phraseFor(TestFixtures.event("a", 1, "20", SourceId.YAPE)))
        assertEquals("¡Recibiste 35 soles por Plin!", renderer.phraseFor(TestFixtures.event("b", 2, "35", SourceId.PLIN)))
        assertEquals(
            "¡Te hicieron una transferencia de 100 soles!",
            renderer.phraseFor(TestFixtures.event("c", 3, "100", SourceId.BCP)),
        )
        val verified = TestFixtures.event("d", 4, "50").copy(verificationStatus = VerificationStatus.VERIFIED)
        assertEquals("¡Pago verificado por 50 soles!", renderer.phraseFor(verified))
    }

    @Test
    fun `parte opcional se elimina si falta el remitente`() {
        val template = "¡Atención! [{remitente} te envió ]{monto_hablado} mediante {banco}"
        val withSender = TestFixtures.event("a", 1, "35", SourceId.YAPE, sender = "Juan")
        val withoutSender = TestFixtures.event("b", 2, "35", SourceId.YAPE, sender = null)
        assertEquals("¡Atención! Juan te envió 35 soles mediante Yape", renderer.render(template, withSender))
        assertEquals("¡Atención! 35 soles mediante Yape", renderer.render(template, withoutSender))
    }

    @Test
    fun `variable obligatoria ausente invalida la plantilla y se usa la predeterminada`() {
        val event = TestFixtures.event("a", 1, "20", SourceId.YAPE, sender = null)
        assertNull(renderer.render("{remitente} te yapeó {monto_hablado}", event))
        val phrase = renderer.phraseFor(event, mapOf(SourceId.YAPE to "{remitente} te yapeó {monto_hablado}"))
        assertEquals("¡Te yapearon 20 soles!", phrase)
    }

    @Test
    fun `hora en zona horaria de Lima`() {
        assertEquals("15:30", renderer.render("{hora}", TestFixtures.event("a", 1)))
    }
}
