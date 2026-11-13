package pe.pagobox.backend

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.config.ProviderCredentials
import pe.pagobox.backend.http.DeviceApi
import pe.pagobox.backend.http.IntegrationStatus
import pe.pagobox.backend.http.integrationStatus
import pe.pagobox.backend.store.ProviderHealth
import pe.pagobox.backend.http.pagoboxBackend
import pe.pagobox.backend.ingest.WebhookIngestion
import pe.pagobox.backend.providers.WebhookRequest
import pe.pagobox.backend.security.Crypto
import pe.pagobox.backend.store.JdbcIntegrationStore
import pe.pagobox.domain.model.ProviderId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceApiTest {
    private val sandboxToken = "token-de-prueba-sandbox-0123456789abcdef"
    private val prodToken = "token-de-prueba-produccion-0123456789abcdef"
    private val config = BackendConfig(
        enabledEnvironments = setOf(DeployEnvironment.SANDBOX, DeployEnvironment.PRODUCTION),
        credentials = mapOf(
            (ProviderId.IZIPAY to DeployEnvironment.SANDBOX) to ProviderCredentials(mapOf("SECRET" to "s")),
        ),
        deviceTokenHashes = mapOf(
            DeployEnvironment.SANDBOX to setOf(Crypto.sha256Hex(sandboxToken.toByteArray())),
            DeployEnvironment.PRODUCTION to setOf(Crypto.sha256Hex(prodToken.toByteArray())),
        ),
    )
    private val store = JdbcIntegrationStore("jdbc:sqlite::memory:")
    private val provider = TestProvider().apply { api["E1"] = TestProvider.ApiPayment("12.50", "approved", live = false) }
    private val ingestion = WebhookIngestion(config, listOf(provider), store)

    @Test
    fun `el celular lee solo los eventos de su entorno con un token valido`() = testApplication {
        val body = """{"paymentId":"E1"}"""
        ingestion.ingest(
            "sandbox", "izipay",
            WebhookRequest(mapOf("x-test-signature" to Crypto.hmacSha256Hex("s", body), "x-test-delivery" to "e1"), emptyMap(), body.toByteArray(), Instant.now()),
        )
        application { pagoboxBackend(config, ingestion, DeviceApi(config, store)) }

        val noToken = client.get("/api/v1/events") { header("X-Forwarded-Proto", "https") }
        assertEquals(HttpStatusCode.Unauthorized, noToken.status)

        val sandbox = client.get("/api/v1/events?after=0") {
            header("X-Forwarded-Proto", "https"); header("Authorization", "Bearer $sandboxToken")
        }
        assertEquals(HttpStatusCode.OK, sandbox.status)
        val json = Json.parseToJsonElement(sandbox.bodyAsText()).jsonObject
        assertEquals("sandbox", json["environment"]!!.jsonPrimitive.content)
        val events = json["events"]!!.jsonArray
        assertEquals(1, events.size)
        assertEquals("12.50", events[0].jsonObject["amount"]!!.jsonPrimitive.content)
        assertEquals("PROVIDER_CONFIRMED", events[0].jsonObject["verificationStatus"]!!.jsonPrimitive.content)

        val prod = client.get("/api/v1/events?after=0") {
            header("X-Forwarded-Proto", "https"); header("Authorization", "Bearer $prodToken")
        }
        assertEquals(0, Json.parseToJsonElement(prod.bodyAsText()).jsonObject["events"]!!.jsonArray.size)

        val cursor = json["cursor"]!!.jsonPrimitive.content
        val next = client.get("/api/v1/events?after=$cursor") {
            header("X-Forwarded-Proto", "https"); header("Authorization", "Bearer $sandboxToken")
        }
        assertEquals(0, Json.parseToJsonElement(next.bodyAsText()).jsonObject["events"]!!.jsonArray.size)
    }

    @Test
    fun `el panel de integraciones no muestra activo sin un webhook real procesado`() = testApplication {
        application { pagoboxBackend(config, ingestion, DeviceApi(config, store, listOf(provider))) }
        suspend fun panel(token: String) = Json.parseToJsonElement(
            client.get("/api/v1/integrations") {
                header("X-Forwarded-Proto", "https"); header("Authorization", "Bearer $token")
            }.bodyAsText(),
        ).jsonObject
        fun status(panel: kotlinx.serialization.json.JsonObject, id: String) =
            panel["providers"]!!.jsonArray.map { it.jsonObject }.first { it["provider"]!!.jsonPrimitive.content == id }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/integrations") { header("X-Forwarded-Proto", "https") }.status)

        val before = panel(sandboxToken)
        assertEquals("AWAITING_FIRST_EVENT", status(before, "IZIPAY")["status"]!!.jsonPrimitive.content)
        assertEquals("true", status(before, "IZIPAY")["credentialsConfigured"]!!.jsonPrimitive.content)
        assertEquals("/webhooks/sandbox/izipay", status(before, "IZIPAY")["webhookPath"]!!.jsonPrimitive.content)
        assertEquals("NOT_IMPLEMENTED", status(before, "MERCADO_PAGO")["status"]!!.jsonPrimitive.content)
        assertEquals("NOT_CONFIGURED", status(panel(prodToken), "IZIPAY")["status"]!!.jsonPrimitive.content)

        // Una firma inválida no activa el proveedor: queda en error.
        val body = """{"paymentId":"E1"}"""
        ingestion.ingest("sandbox", "izipay", WebhookRequest(mapOf("x-test-signature" to "00", "x-test-delivery" to "bad"), emptyMap(), body.toByteArray(), Instant.now()))
        assertEquals("ERROR", status(panel(sandboxToken), "IZIPAY")["status"]!!.jsonPrimitive.content)

        ingestion.ingest(
            "sandbox", "izipay",
            WebhookRequest(mapOf("x-test-signature" to Crypto.hmacSha256Hex("s", body), "x-test-delivery" to "ok"), emptyMap(), body.toByteArray(), Instant.now().plusSeconds(1)),
        )
        val after = panel(sandboxToken)
        assertEquals("ACTIVE", status(after, "IZIPAY")["status"]!!.jsonPrimitive.content)
        // Nunca se devuelven credenciales.
        val text = after.toString()
        assertEquals(false, "SECRET" in text || "\"s\"" in text)
    }

    @Test
    fun `estado de integracion segun el ultimo evento y el ultimo error`() {
        val t = Instant.parse("2026-11-13T10:00:00Z")
        fun h(event: Instant?, error: Instant?) = ProviderHealth(ProviderId.MERCADO_PAGO, DeployEnvironment.SANDBOX, event ?: error, event, error?.let { "x" }, error)
        assertEquals(IntegrationStatus.NOT_IMPLEMENTED, integrationStatus(false, true, h(t, null)))
        assertEquals(IntegrationStatus.NOT_CONFIGURED, integrationStatus(true, false, h(t, null)))
        assertEquals(IntegrationStatus.AWAITING_FIRST_EVENT, integrationStatus(true, true, h(null, null)))
        assertEquals(IntegrationStatus.ERROR, integrationStatus(true, true, h(null, t)))
        assertEquals(IntegrationStatus.ACTIVE, integrationStatus(true, true, h(t.plusSeconds(1), t)))
        assertEquals(IntegrationStatus.ERROR, integrationStatus(true, true, h(t, t.plusSeconds(1))))
    }
}
