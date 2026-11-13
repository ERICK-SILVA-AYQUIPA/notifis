package pe.pagobox.engine

import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.protocol.DeviceMessages
import pe.pagobox.engine.verification.ProviderConfirmation
import pe.pagobox.engine.verification.VerificationEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerificationAndProtocolTest {
    private val engine = VerificationEngine()

    private fun proof(
        externalId: String = "TEST-1",
        amount: String = "25.00",
        provider: ProviderId = ProviderId.MERCADO_PAGO,
        signature: Boolean = true,
        api: Boolean = true,
    ) = ProviderConfirmation(provider, externalId, Money.pen(amount), "approved", signature, api)

    @Test
    fun `un evento oficial con evidencia valida se confirma`() {
        val confirmed = engine.confirm(TestFixtures.officialEvent("w1"), proof())
        assertEquals(VerificationStatus.PROVIDER_CONFIRMED, confirmed.verificationStatus)
    }

    @Test
    fun `una notificacion Android nunca se confirma, aunque traiga una referencia`() {
        val android = TestFixtures.event("a1", 1, "25.00")
        assertFailsWith<IllegalArgumentException> { engine.confirm(android, proof()) }
        assertFailsWith<IllegalArgumentException> {
            android.copy(verificationStatus = VerificationStatus.PROVIDER_CONFIRMED)
        }
    }

    @Test
    fun `se rechaza la confirmacion si no coincide proveedor, pago, importe o falta evidencia`() {
        val event = TestFixtures.officialEvent("w2")
        assertFailsWith<IllegalArgumentException> { engine.confirm(event, proof(provider = ProviderId.IZIPAY)) }
        assertFailsWith<IllegalArgumentException> { engine.confirm(event, proof(externalId = "OTRO")) }
        assertFailsWith<IllegalArgumentException> { engine.confirm(event, proof(amount = "25.01")) }
        assertFailsWith<IllegalArgumentException> { engine.confirm(event, proof(signature = false, api = false)) }
    }

    @Test
    fun `un evento ya confirmado o descartado no se vuelve a confirmar`() {
        val rejected = TestFixtures.officialEvent("w3").copy(verificationStatus = VerificationStatus.REJECTED)
        assertFailsWith<IllegalStateException> { engine.confirm(rejected, proof()) }
    }

    @Test
    fun `un evento oficial exige proveedor e identificador externo`() {
        assertFailsWith<IllegalArgumentException> { TestFixtures.officialEvent("w4").copy(provider = null) }
        assertFailsWith<IllegalArgumentException> { TestFixtures.officialEvent("w5").copy(externalTransactionId = " ") }
    }

    @Test
    fun `duplicateOf solo en eventos duplicados`() {
        val e = TestFixtures.event("a2", 2)
        assertFailsWith<IllegalArgumentException> { e.copy(duplicateOf = "w1") }
        assertFailsWith<IllegalArgumentException> { e.copy(verificationStatus = VerificationStatus.DUPLICATE) }
        assertEquals("w1", e.copy(verificationStatus = VerificationStatus.DUPLICATE, duplicateOf = "w1").duplicateOf)
    }

    @Test
    fun `mensaje de evento para el dispositivo`() {
        val json = DeviceMessages.paymentEvent(TestFixtures.event("evt_000001", 1, "35", sender = "Juan \"JJ\""))
        assertEquals(
            "{\"type\":\"PAYMENT_EVENT\",\"eventId\":\"evt_000001\",\"sequence\":1,\"source\":\"YAPE\"," +
                "\"eventType\":\"INCOMING_TRANSFER\",\"amount\":35.00,\"currency\":\"PEN\"," +
                "\"sender\":\"Juan \\\"JJ\\\"\",\"timestamp\":\"2026-11-12T15:30:00-05:00\"," +
                "\"verificationStatus\":\"INCOME_CANDIDATE\",\"announcementStatus\":\"PENDING\"," +
                "\"channel\":\"ANDROID_NOTIFICATION\",\"provider\":null,\"test\":false}",
            json,
        )
    }
}
