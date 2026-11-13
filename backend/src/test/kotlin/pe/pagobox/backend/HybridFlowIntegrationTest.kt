package pe.pagobox.backend

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.config.ProviderCredentials
import pe.pagobox.backend.http.DeviceApi
import pe.pagobox.backend.http.pagoboxBackend
import pe.pagobox.backend.ingest.Retry
import pe.pagobox.backend.ingest.WebhookIngestion
import pe.pagobox.backend.providers.mercadopago.MercadoPagoAdapter
import pe.pagobox.backend.providers.mercadopago.MercadoPagoSignature
import pe.pagobox.backend.security.Crypto
import pe.pagobox.backend.store.JdbcIntegrationStore
import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceChannel
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.correlation.Correlation
import pe.pagobox.engine.correlation.CrossChannelCorrelator
import pe.pagobox.engine.protocol.DeviceMessages
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fase 8 — flujo híbrido completo por HTTP, con datos de prueba:
 * webhook firmado de Mercado Pago (sandbox) → consulta al «API» local → evento confirmado →
 * lectura por el celular → correlación con un posible ingreso Android → mensaje al equipo.
 * El servidor local imita GET /v1/payments/{id} con los campos del SDK oficial; no es Mercado Pago.
 */
class HybridFlowIntegrationTest {
    private val api = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val payments = mutableMapOf<String, String>()

    init {
        api.createContext("/v1/payments/") { ex ->
            val body = payments[ex.requestURI.path.substringAfterLast('/')]
            val bytes = (body ?: """{"message":"not found"}""").toByteArray()
            ex.sendResponseHeaders(if (body != null) 200 else 404, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        api.start()
    }

    @AfterTest
    fun stop() = api.stop(0)

    private val secret = "secreto-de-prueba"
    private val deviceToken = "token-de-prueba-del-celular-0123456789"
    private val config = BackendConfig(
        enabledEnvironments = setOf(DeployEnvironment.SANDBOX),
        credentials = mapOf(
            (ProviderId.MERCADO_PAGO to DeployEnvironment.SANDBOX) to ProviderCredentials(
                mapOf(
                    MercadoPagoAdapter.WEBHOOK_SECRET to secret,
                    MercadoPagoAdapter.ACCESS_TOKEN to "TEST-token-de-prueba",
                    MercadoPagoAdapter.API_BASE_URL to "http://127.0.0.1:${api.address.port}",
                ),
            ),
        ),
        deviceTokenHashes = mapOf(DeployEnvironment.SANDBOX to setOf(Crypto.sha256Hex(deviceToken.toByteArray()))),
    )
    private val store = JdbcIntegrationStore("jdbc:sqlite::memory:")
    private val adapters = listOf(MercadoPagoAdapter())
    private val ingestion = WebhookIngestion(config, adapters, store, Retry(attempts = 2, initialDelayMs = 1, sleep = {}))

    private fun signature(paymentId: String, requestId: String, key: String = secret): String {
        val ts = "1763046000"
        return "ts=$ts,v1=" + Crypto.hmacSha256Hex(key, MercadoPagoSignature.manifest(paymentId, requestId, ts))
    }

    @Test
    fun `del webhook firmado al equipo sin fusionar por casualidad`() = testApplication {
        application { pagoboxBackend(config, ingestion, DeviceApi(config, store, adapters)) }
        payments["2001"] = """{"id":2001,"status":"approved","status_detail":"accredited","transaction_amount":25,
            "currency_id":"PEN","payment_method_id":"yape","payment_type_id":"debit_card",
            "date_approved":"2026-11-13T09:59:58.000-05:00","live_mode":false}"""
        val body = """{"action":"payment.updated","type":"payment","data":{"id":"2001"}}"""
        suspend fun deliver(requestId: String, sig: String) =
            client.post("/webhooks/sandbox/mercado-pago?data.id=2001&type=payment") {
                header("X-Forwarded-Proto", "https"); header("x-request-id", requestId); header("x-signature", sig)
                header("Content-Type", "application/json"); setBody(body)
            }
        suspend fun get(path: String) = client.get(path) {
            header("X-Forwarded-Proto", "https"); header("Authorization", "Bearer $deviceToken")
        }

        // 1. Antes de cualquier webhook el panel no muestra Mercado Pago como activo.
        fun mpStatus(panel: String) = Json.parseToJsonElement(panel).jsonObject["providers"]!!.jsonArray
            .map { it.jsonObject }.first { it["provider"]!!.jsonPrimitive.content == "MERCADO_PAGO" }["status"]!!.jsonPrimitive.content
        assertEquals("AWAITING_FIRST_EVENT", mpStatus(get("/api/v1/integrations").bodyAsText()))

        // 2. Firma falsificada: 401 y ningún evento.
        assertEquals(HttpStatusCode.Unauthorized, deliver("r-x", signature("2001", "r-x", key = "otro-secreto")).status)
        assertEquals(0, events(get("/api/v1/events?after=0").bodyAsText()).size)

        // 3. Firma válida: confirmado tras consultar el pago. La misma entrega repetida no duplica.
        assertEquals(HttpStatusCode.OK, deliver("r-1", signature("2001", "r-1")).status)
        assertEquals(HttpStatusCode.OK, deliver("r-1", signature("2001", "r-1")).status)
        val list = events(get("/api/v1/events?after=0").bodyAsText())
        assertEquals(1, list.size)
        val dto = list.single()
        assertEquals("PROVIDER_CONFIRMED", dto["verificationStatus"]!!.jsonPrimitive.content)
        assertEquals("MERCADO_PAGO", dto["provider"]!!.jsonPrimitive.content)
        assertEquals("ACTIVE", mpStatus(get("/api/v1/integrations").bodyAsText()))

        // 4. El celular arma el evento oficial (como OfficialEventsSync/PaymentProcessor).
        val official = PaymentEvent(
            eventId = dto["eventId"]!!.jsonPrimitive.content,
            sequence = 8,
            sourceChannel = SourceChannel.OFFICIAL_WEBHOOK,
            provider = ProviderId.MERCADO_PAGO,
            source = SourceId.valueOf(dto["source"]!!.jsonPrimitive.content),
            externalTransactionId = dto["externalTransactionId"]!!.jsonPrimitive.content,
            eventType = EventType.valueOf(dto["eventType"]!!.jsonPrimitive.content),
            money = Money.pen(dto["amount"]!!.jsonPrimitive.content),
            senderName = null,
            eventTimestamp = Instant.ofEpochMilli(dto["eventTimestamp"]!!.jsonPrimitive.long),
            detectedTimestamp = Instant.parse("2026-11-13T15:00:05Z"),
            verificationStatus = VerificationStatus.PROVIDER_CONFIRMED,
            announcementStatus = AnnouncementStatus.PENDING,
            rawEventReference = dto["eventId"]!!.jsonPrimitive.content,
            testMode = true,
        )

        // 5. Un posible ingreso Android de 25 soles por Yape un minuto antes, sin remitente en el webhook:
        //    importe + hora no bastan → posible mismo pago, sin fusionar.
        val android = PaymentEvent(
            eventId = "evt_000007", sequence = 7,
            sourceChannel = SourceChannel.ANDROID_NOTIFICATION, sourceApplication = "app.de.prueba",
            source = SourceId.YAPE, eventType = EventType.INCOMING_TRANSFER, money = Money.pen("25.00"),
            senderName = "Cliente de prueba",
            eventTimestamp = Instant.parse("2026-11-13T14:58:58Z"), detectedTimestamp = Instant.parse("2026-11-13T14:58:59Z"),
            verificationStatus = VerificationStatus.INCOME_CANDIDATE, announcementStatus = AnnouncementStatus.ANNOUNCED,
            rawEventReference = "hash-7", testMode = true,
        )
        val match = CrossChannelCorrelator().correlate(official, listOf(android))
        assertIs<Correlation.PossibleMatch>(match)
        assertEquals(listOf("evt_000007"), match.androidEventIds)
        // Un evento de producción nunca se cruza con uno de sandbox.
        assertEquals(Correlation.NoMatch, CrossChannelCorrelator().correlate(official, listOf(android.copy(testMode = false))))

        // 6. Mensaje al equipo: confirmado, canal oficial, marcado como prueba.
        val msg = Json.parseToJsonElement(DeviceMessages.paymentEvent(official)).jsonObject
        assertEquals("PROVIDER_CONFIRMED", msg["verificationStatus"]!!.jsonPrimitive.content)
        assertEquals("OFFICIAL_WEBHOOK", msg["channel"]!!.jsonPrimitive.content)
        assertEquals("true", msg["test"]!!.jsonPrimitive.content)
        assertTrue(msg["test"]!!.jsonPrimitive.isString.not())
        // Y el posible ingreso Android nunca sale como confirmado.
        val androidMsg = Json.parseToJsonElement(DeviceMessages.paymentEvent(android)).jsonObject
        assertEquals("INCOME_CANDIDATE", androidMsg["verificationStatus"]!!.jsonPrimitive.content)
    }

    private fun events(json: String) = Json.parseToJsonElement(json).jsonObject["events"]!!.jsonArray.map { it.jsonObject as JsonObject }
}
