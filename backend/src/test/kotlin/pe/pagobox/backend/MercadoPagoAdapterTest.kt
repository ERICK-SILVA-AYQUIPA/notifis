package pe.pagobox.backend

import com.sun.net.httpserver.HttpServer
import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.config.ProviderCredentials
import pe.pagobox.backend.ingest.Retry
import pe.pagobox.backend.ingest.WebhookIngestion
import pe.pagobox.backend.providers.Extraction
import pe.pagobox.backend.providers.SignatureCheck
import pe.pagobox.backend.providers.WebhookRequest
import pe.pagobox.backend.providers.mercadopago.MercadoPagoAdapter
import pe.pagobox.backend.providers.mercadopago.MercadoPagoSignature
import pe.pagobox.backend.security.Crypto
import pe.pagobox.backend.store.JdbcIntegrationStore
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MercadoPagoAdapterTest {
    /**
     * Vector tomado de las pruebas del SDK oficial (mercadopago/sdk-nodejs, src/utils/webhook/webhook.spec.ts):
     * secreto, x-request-id, data.id y ts. El hash esperado se calculó aparte con Node.js y con Python
     * (HMAC-SHA256), ambos dieron el mismo valor.
     */
    @Test
    fun `firma compatible con el validador oficial del SDK`() {
        val secret = "your_secret_key_here"
        val requestId = "2066ca19-c6f1-498a-be75-1923005edd06"
        val dataId = "ord01jq4s4ky8hwq6na5pxb65b3d3"
        val ts = "1742505638683"
        assertEquals("id:$dataId;request-id:$requestId;ts:$ts;", MercadoPagoSignature.manifest(dataId, requestId, ts))
        val header = "ts=$ts,v1=633f91233312dd391ec75fa0bea539cfc2d6c4918873305b84a96cc1c58db71c"
        assertEquals(SignatureCheck.Valid, MercadoPagoSignature.verify(header, requestId, dataId, secret, Instant.now(), null))
        // data.id se usa tal cual: en mayúsculas el manifiesto cambia y la firma ya no coincide.
        assertIs<SignatureCheck.Invalid>(MercadoPagoSignature.verify(header, requestId, dataId.uppercase(), secret, Instant.now(), null))
        assertIs<SignatureCheck.Invalid>(MercadoPagoSignature.verify(header, requestId, dataId, "otro", Instant.now(), null))
        assertIs<SignatureCheck.Invalid>(MercadoPagoSignature.verify(null, requestId, dataId, secret, Instant.now(), null))
        assertIs<SignatureCheck.Invalid>(MercadoPagoSignature.verify("v1=abc", requestId, dataId, secret, Instant.now(), null))
    }

    @Test
    fun `pares sin valor se omiten del manifiesto y la tolerancia de tiempo se aplica`() {
        assertEquals("ts:10;", MercadoPagoSignature.manifest(null, null, "10"))
        assertEquals("id:5;ts:10;", MercadoPagoSignature.manifest("5", " ", "10"))
        val sig = Crypto.hmacSha256Hex("k", "id:5;ts:1000;")
        val header = "ts=1000,v1=$sig"
        assertEquals(SignatureCheck.Valid, MercadoPagoSignature.verify(header, null, "5", "k", Instant.ofEpochSecond(1200), 300))
        assertIs<SignatureCheck.Invalid>(MercadoPagoSignature.verify(header, null, "5", "k", Instant.ofEpochSecond(2000), 300))
    }

    @Test
    fun `solo se procesan notificaciones de pagos con data id en la query`() {
        val mp = MercadoPagoAdapter()
        fun req(query: Map<String, String>, body: String) = WebhookRequest(emptyMap(), query, body.toByteArray(), Instant.EPOCH)
        assertEquals(Extraction.Payment("123"), mp.extract(req(mapOf("type" to "payment", "data.id" to "123"), """{"data":{"id":"123"}}""")))
        assertIs<Extraction.Ignored>(mp.extract(req(mapOf("type" to "merchant_order", "data.id" to "1"), "{}")))
        assertIs<Extraction.Malformed>(mp.extract(req(mapOf("type" to "payment"), """{"data":{"id":"123"}}""")))
        assertIs<Extraction.Malformed>(mp.extract(req(mapOf("type" to "payment", "data.id" to "123"), """{"data":{"id":"999"}}""")))
    }

    // --- Flujo completo contra un servidor local que responde como GET /v1/payments/{id} ---
    // Las respuestas son DATOS DE PRUEBA con los campos que define el SDK oficial (PaymentResponse);
    // no son respuestas reales de Mercado Pago.

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val payments = mutableMapOf<String, Pair<Int, String>>()
    private var lastAuthorization: String? = null

    init {
        server.createContext("/v1/payments/") { ex ->
            lastAuthorization = ex.requestHeaders.getFirst("Authorization")
            val id = ex.requestURI.path.substringAfterLast('/')
            val (code, body) = payments[id] ?: (404 to """{"message":"not found"}""")
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private val secret = "secreto-de-prueba"
    private val store = JdbcIntegrationStore("jdbc:sqlite::memory:")
    private val config = BackendConfig(
        enabledEnvironments = setOf(DeployEnvironment.SANDBOX),
        credentials = mapOf(
            (ProviderId.MERCADO_PAGO to DeployEnvironment.SANDBOX) to ProviderCredentials(
                mapOf(
                    MercadoPagoAdapter.WEBHOOK_SECRET to secret,
                    MercadoPagoAdapter.ACCESS_TOKEN to "TEST-token-de-prueba",
                    MercadoPagoAdapter.API_BASE_URL to "http://127.0.0.1:${server.address.port}",
                ),
            ),
        ),
    )
    private val ingestion = WebhookIngestion(config, listOf(MercadoPagoAdapter()), store, Retry(attempts = 2, initialDelayMs = 1, sleep = {}))

    private fun webhook(paymentId: String, requestId: String): WebhookRequest {
        val ts = "1763046000"
        val sig = Crypto.hmacSha256Hex(secret, MercadoPagoSignature.manifest(paymentId, requestId, ts))
        return WebhookRequest(
            headers = mapOf("x-signature" to "ts=$ts,v1=$sig", "x-request-id" to requestId, "content-type" to "application/json"),
            query = mapOf("data.id" to paymentId, "type" to "payment"),
            body = """{"action":"payment.updated","type":"payment","data":{"id":"$paymentId"}}""".toByteArray(),
            receivedAt = Instant.parse("2026-11-13T15:00:00Z"),
        )
    }

    private fun payment(id: String, status: String, amount: String = "25", method: String = "yape", live: Boolean = false) =
        """{"id":$id,"status":"$status","status_detail":"accredited","transaction_amount":$amount,"currency_id":"PEN",
           "payment_method_id":"$method","payment_type_id":"debit_card","date_approved":"2026-11-13T09:59:58.000-05:00","live_mode":$live}"""

    @Test
    fun `yape aprobado consultado en la API queda confirmado`() {
        payments["1001"] = 200 to payment("1001", "approved")
        assertEquals(200, ingestion.ingest("sandbox", "mercado-pago", webhook("1001", "r-1")).httpStatus)
        val e = store.paymentByExternalId(ProviderId.MERCADO_PAGO, DeployEnvironment.SANDBOX, "1001")!!.event
        assertEquals(VerificationStatus.PROVIDER_CONFIRMED, e.verificationStatus)
        assertEquals(SourceId.YAPE, e.source)
        assertEquals("25.00", e.money!!.normalized.toPlainString())
        assertEquals(Instant.parse("2026-11-13T14:59:58Z"), e.eventTimestamp)
        assertEquals("Bearer TEST-token-de-prueba", lastAuthorization)
    }

    @Test
    fun `la API manda sobre el cuerpo del webhook`() {
        // El cuerpo dice "payment" pero la API dice rechazado: no se confirma.
        payments["1002"] = 200 to payment("1002", "rejected")
        ingestion.ingest("sandbox", "mercado-pago", webhook("1002", "r-2"))
        assertEquals(VerificationStatus.REJECTED, store.paymentByExternalId(ProviderId.MERCADO_PAGO, DeployEnvironment.SANDBOX, "1002")!!.event.verificationStatus)
    }

    @Test
    fun `pago de produccion recibido en sandbox se rechaza`() {
        payments["1003"] = 200 to payment("1003", "approved", live = true)
        assertEquals(409, ingestion.ingest("sandbox", "mercado-pago", webhook("1003", "r-3")).httpStatus)
        assertNull(store.paymentByExternalId(ProviderId.MERCADO_PAGO, DeployEnvironment.SANDBOX, "1003"))
    }

    @Test
    fun `errores de la API, 5xx se reintenta y 404 no`() {
        payments["1004"] = 500 to "{}"
        assertEquals(503, ingestion.ingest("sandbox", "mercado-pago", webhook("1004", "r-4")).httpStatus)
        assertEquals(200, ingestion.ingest("sandbox", "mercado-pago", webhook("1005", "r-5")).httpStatus)
        assertNull(store.paymentByExternalId(ProviderId.MERCADO_PAGO, DeployEnvironment.SANDBOX, "1005"))
    }

    @Test
    fun `importe sin moneda conocida queda pendiente, sin inventar`() {
        payments["1006"] = 200 to payment("1006", "approved").replace("\"PEN\"", "\"BRL\"")
        ingestion.ingest("sandbox", "mercado-pago", webhook("1006", "r-6"))
        val e = store.paymentByExternalId(ProviderId.MERCADO_PAGO, DeployEnvironment.SANDBOX, "1006")!!.event
        assertEquals(VerificationStatus.PENDING_VERIFICATION, e.verificationStatus)
        assertNull(e.money)
    }
}
