package pe.pagobox.engine

import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.protocol.DeviceMessages
import pe.pagobox.engine.verification.OfficialConfirmation
import pe.pagobox.engine.verification.VerificationEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerificationAndProtocolTest {
    @Test
    fun `solo una confirmacion oficial verifica`() {
        val engine = VerificationEngine()
        val verified = engine.verify(TestFixtures.event("e1", 1), OfficialConfirmation("api", "REF-1"))
        assertEquals(EventState.VERIFIED, verified.state)
        assertEquals(VerificationStatus.VERIFIED, verified.verificationStatus)

        val received = TestFixtures.event("e2", 2).copy(state = EventState.RECEIVED)
        assertFailsWith<IllegalStateException> { engine.verify(received, OfficialConfirmation("api", "REF-2")) }
        assertFailsWith<IllegalArgumentException> {
            engine.verify(TestFixtures.event("e3", 3), OfficialConfirmation("api", " "))
        }
    }

    @Test
    fun `mensaje de evento para el dispositivo`() {
        val json = DeviceMessages.paymentEvent(TestFixtures.event("evt_000001", 1, "35", sender = "Juan \"JJ\""))
        assertEquals(
            "{\"type\":\"PAYMENT_EVENT\",\"eventId\":\"evt_000001\",\"sequence\":1,\"source\":\"YAPE\"," +
                "\"eventType\":\"INCOMING_TRANSFER\",\"amount\":35.00,\"currency\":\"PEN\"," +
                "\"sender\":\"Juan \\\"JJ\\\"\",\"timestamp\":\"2026-11-12T15:30:00-05:00\"," +
                "\"verificationStatus\":\"DETECTED\",\"announcementStatus\":\"PENDING\"}",
            json,
        )
    }
}
