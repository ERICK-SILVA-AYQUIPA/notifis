package pe.pagobox.backend

import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.config.ProviderCredentials
import pe.pagobox.backend.ingest.Retry
import pe.pagobox.backend.ingest.WebhookIngestion
import pe.pagobox.backend.providers.WebhookRequest
import pe.pagobox.backend.security.Crypto
import pe.pagobox.backend.store.JdbcIntegrationStore
import pe.pagobox.backend.store.ProcessingStatus
import pe.pagobox.backend.store.SignatureStatus
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.VerificationStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebhookIngestionTest {
    private val provider = TestProvider()
    private val store = JdbcIntegrationStore("jdbc:sqlite::memory:")
    private val config = BackendConfig(
        enabledEnvironments = setOf(DeployEnvironment.SANDBOX, DeployEnvironment.PRODUCTION),
        credentials = mapOf(
            (ProviderId.IZIPAY to DeployEnvironment.SANDBOX) to ProviderCredentials(mapOf("SECRET" to "secreto-sandbox")),
            (ProviderId.IZIPAY to DeployEnvironment.PRODUCTION) to ProviderCredentials(mapOf("SECRET" to "secreto-prod")),
        ),
        maxBodyBytes = 1024,
    )
    private val ingestion = WebhookIngestion(config, listOf(provider), store, Retry(attempts = 3, initialDelayMs = 1, sleep = {}))

    private fun request(paymentId: String, delivery: String?, secret: String = "secreto-sandbox", signature: String? = null): WebhookRequest {
        val body = """{"paymentId":"$paymentId"}"""
        val headers = buildMap {
            put("x-test-signature", signature ?: Crypto.hmacSha256Hex(secret, body))
            delivery?.let { put("x-test-delivery", it) }
            put("authorization", "Bearer no-debe-guardarse")
        }
        return WebhookRequest(headers, emptyMap(), body.toByteArray(), Instant.parse("2026-11-13T15:00:00Z"))
    }

    private fun sandboxPayment(id: String, amount: String = "25.00", status: String = "approved") {
        provider.api[id] = TestProvider.ApiPayment(amount, status, live = false)
    }

    @Test
    fun `pago aprobado con firma valida queda PROVIDER_CONFIRMED`() {
        sandboxPayment("P1")
        val r = ingestion.ingest("sandbox", "izipay", request("P1", "d1"))
        assertEquals(200, r.httpStatus)
        val stored = assertNotNull(store.paymentByExternalId(ProviderId.IZIPAY, DeployEnvironment.SANDBOX, "P1"))
        assertEquals(VerificationStatus.PROVIDER_CONFIRMED, stored.event.verificationStatus)
        assertEquals("25.00", stored.event.money!!.normalized.toPlainString())
        val raw = assertNotNull(store.rawByDeliveryKey(ProviderId.IZIPAY, DeployEnvironment.SANDBOX, "d1"))
        assertEquals(ProcessingStatus.PROCESSED, raw.processingStatus)
        assertEquals(SignatureStatus.VALID, raw.signatureStatus)
        assertNull(raw.headers["authorization"], "Nunca se guarda la cabecera Authorization")
        assertTrue(store.auditTrail(50).any { it.action == "PAYMENT_PROVIDER_CONFIRMED" })
    }

    @Test
    fun `firma invalida se rechaza sin crear pago ni guardar el cuerpo`() {
        sandboxPayment("P2")
        val r = ingestion.ingest("sandbox", "izipay", request("P2", "d2", signature = "00"))
        assertEquals(401, r.httpStatus)
        assertNull(store.paymentByExternalId(ProviderId.IZIPAY, DeployEnvironment.SANDBOX, "P2"))
        assertEquals(0, provider.calls, "Sin firma válida no se consulta al proveedor")
        assertTrue(store.auditTrail(50).any { it.action == "SIGNATURE_INVALID" })
    }

    @Test
    fun `la misma entrega repetida no se procesa dos veces`() {
        sandboxPayment("P3")
        assertEquals(200, ingestion.ingest("sandbox", "izipay", request("P3", "d3")).httpStatus)
        val again = ingestion.ingest("sandbox", "izipay", request("P3", "d3"))
        assertEquals(200, again.httpStatus)
        assertEquals("already processed", again.message)
        assertEquals(1, provider.calls)
    }

    @Test
    fun `entregas distintas del mismo pago producen un solo evento`() {
        sandboxPayment("P4")
        val a = ingestion.ingest("sandbox", "izipay", request("P4", "d4a"))
        val b = ingestion.ingest("sandbox", "izipay", request("P4", "d4b"))
        assertEquals(a.eventId, b.eventId)
        assertEquals(1, store.paymentsSince(DeployEnvironment.SANDBOX, Instant.EPOCH, 10).size)
    }

    @Test
    fun `pago pendiente se confirma cuando el proveedor lo aprueba`() {
        sandboxPayment("P5", status = "pending")
        ingestion.ingest("sandbox", "izipay", request("P5", "d5a"))
        assertEquals(VerificationStatus.PENDING_VERIFICATION, store.paymentByExternalId(ProviderId.IZIPAY, DeployEnvironment.SANDBOX, "P5")!!.event.verificationStatus)
        sandboxPayment("P5", status = "approved")
        ingestion.ingest("sandbox", "izipay", request("P5", "d5b"))
        assertEquals(VerificationStatus.PROVIDER_CONFIRMED, store.paymentByExternalId(ProviderId.IZIPAY, DeployEnvironment.SANDBOX, "P5")!!.event.verificationStatus)
    }

    @Test
    fun `proveedor caido se reintenta y luego pide reenvio`() {
        sandboxPayment("P6")
        provider.failuresBeforeSuccess = 2
        assertEquals(200, ingestion.ingest("sandbox", "izipay", request("P6", "d6")).httpStatus)
        assertEquals(3, provider.calls)

        sandboxPayment("P7")
        provider.failuresBeforeSuccess = 5
        val r = ingestion.ingest("sandbox", "izipay", request("P7", "d7"))
        assertEquals(503, r.httpStatus)
        assertEquals(ProcessingStatus.RETRY_PENDING, store.rawByDeliveryKey(ProviderId.IZIPAY, DeployEnvironment.SANDBOX, "d7")!!.processingStatus)
        // El proveedor reenvía la misma entrega: ahora sí se procesa.
        provider.failuresBeforeSuccess = 0
        assertEquals(200, ingestion.ingest("sandbox", "izipay", request("P7", "d7")).httpStatus)
        val raw = store.rawByDeliveryKey(ProviderId.IZIPAY, DeployEnvironment.SANDBOX, "d7")!!
        assertEquals(ProcessingStatus.PROCESSED, raw.processingStatus)
        assertEquals(2, raw.attempts)
    }

    @Test
    fun `sandbox y produccion estan separados`() {
        sandboxPayment("P8")
        // Firma con el secreto de sandbox enviada a la ruta de producción: rechazada.
        assertEquals(401, ingestion.ingest("production", "izipay", request("P8", "d8", secret = "secreto-sandbox")).httpStatus)
        // Firma de producción, pero el proveedor dice que el pago es de prueba (live=false): rechazado.
        assertEquals(409, ingestion.ingest("production", "izipay", request("P8", "d8b", secret = "secreto-prod")).httpStatus)
        assertNull(store.paymentByExternalId(ProviderId.IZIPAY, DeployEnvironment.PRODUCTION, "P8"))
    }

    @Test
    fun `entorno deshabilitado, proveedor desconocido, sin credenciales y cuerpo grande`() {
        val onlySandbox = WebhookIngestion(config.copy(enabledEnvironments = setOf(DeployEnvironment.SANDBOX)), listOf(provider), store)
        assertEquals(404, onlySandbox.ingest("production", "izipay", request("X", "x1", secret = "secreto-prod")).httpStatus)
        assertEquals(404, ingestion.ingest("sandbox", "otro", request("X", "x2")).httpStatus)
        val noCreds = WebhookIngestion(config.copy(credentials = emptyMap()), listOf(provider), store)
        assertEquals(503, noCreds.ingest("sandbox", "izipay", request("X", "x3")).httpStatus)
        val big = WebhookRequest(emptyMap(), emptyMap(), ByteArray(2048), Instant.EPOCH)
        assertEquals(413, ingestion.ingest("sandbox", "izipay", big).httpStatus)
    }

    @Test
    fun `estado del proveedor registra ultima comunicacion, ultimo evento y errores`() {
        sandboxPayment("P9")
        ingestion.ingest("sandbox", "izipay", request("P9", "d9"))
        ingestion.ingest("sandbox", "izipay", request("P9", "d9x", signature = "00"))
        val h = store.providerHealth(ProviderId.IZIPAY, DeployEnvironment.SANDBOX)
        assertNotNull(h.lastCommunicationAt)
        assertNotNull(h.lastEventAt)
        assertTrue(h.lastError!!.contains("Firma inválida"))
    }
}
