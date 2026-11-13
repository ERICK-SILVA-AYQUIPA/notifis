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
}
