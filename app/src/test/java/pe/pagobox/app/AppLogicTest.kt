package pe.pagobox.app

import pe.pagobox.app.data.local.PhraseTemplateEntity
import pe.pagobox.app.data.repositories.toDomain
import pe.pagobox.app.data.repositories.toEntity
import pe.pagobox.app.voice.PhraseSelector
import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLogicTest {
    private fun event(source: SourceId = SourceId.YAPE, sender: String? = "Juan", verified: Boolean = false) = PaymentEvent(
        eventId = "evt_000001",
        sequence = 1,
        source = source,
        eventType = EventType.INCOMING_TRANSFER,
        money = Money.pen("35.50"),
        senderName = sender,
        eventTimestamp = Instant.parse("2026-11-12T20:30:00Z"),
        detectedTimestamp = Instant.parse("2026-11-12T20:30:01Z"),
        state = EventState.INCOME_CANDIDATE,
        verificationStatus = if (verified) VerificationStatus.VERIFIED else VerificationStatus.DETECTED,
        announcementStatus = AnnouncementStatus.PENDING,
        rawNotificationHash = "hash",
    )

    private fun template(text: String, source: SourceId? = null, verified: Boolean = false, enabled: Boolean = true) =
        PhraseTemplateEntity(sourceId = source?.name, forVerified = verified, template = text, enabled = enabled)

    @Test
    fun `sin plantillas se usa la frase predeterminada`() {
        assertEquals("Se detectó un posible ingreso de 35 soles con 50 céntimos por Yape", PhraseSelector.select(event(), emptyList(), true))
    }

    @Test
    fun `la plantilla de la entidad tiene prioridad sobre la general`() {
        val templates = listOf(
            template("General {monto_hablado}"),
            template("Yape: [{remitente} ]{monto_hablado}", SourceId.YAPE),
        )
        assertEquals("Yape: Juan 35 soles con 50 céntimos", PhraseSelector.select(event(), templates, true))
        assertEquals("General 35 soles con 50 céntimos", PhraseSelector.select(event(SourceId.PLIN), templates, true))
    }

    @Test
    fun `el remitente se omite si el comerciante lo desactiva`() {
        val templates = listOf(template("[{remitente} te envió ]{monto_hablado}"))
        assertEquals("35 soles con 50 céntimos", PhraseSelector.select(event(), templates, announceSender = false))
    }

    @Test
    fun `plantillas desactivadas o de otro tipo de verificacion no se usan`() {
        val templates = listOf(template("Desactivada", enabled = false), template("Solo verificados {monto_hablado}", verified = true))
        assertEquals("Se detectó un posible ingreso de 35 soles con 50 céntimos por Yape", PhraseSelector.select(event(), templates, true))
        assertEquals("Solo verificados 35 soles con 50 céntimos", PhraseSelector.select(event(verified = true), templates, true))
    }

    @Test
    fun `el evento se guarda y recupera sin perder precision`() {
        val original = event()
        val restored = original.toEntity("motivo", 0).toDomain()
        assertEquals(original, restored)
        assertEquals(3550L, original.toEntity("m", 0).amountCents)
    }
}
