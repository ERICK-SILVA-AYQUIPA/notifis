package pe.pagobox.backend

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.config.ProviderCredentials
import pe.pagobox.backend.http.pagoboxBackend
import pe.pagobox.backend.ingest.WebhookIngestion
import pe.pagobox.backend.security.Crypto
import pe.pagobox.backend.store.JdbcIntegrationStore
import pe.pagobox.domain.model.ProviderId
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpRoutesTest {
    private val config = BackendConfig(
        credentials = mapOf((ProviderId.IZIPAY to DeployEnvironment.SANDBOX) to ProviderCredentials(mapOf("SECRET" to "s"))),
        requireHttps = true,
    )
    private val provider = TestProvider().apply { api["H1"] = TestProvider.ApiPayment("10.00", "approved", live = false) }
    private val ingestion = WebhookIngestion(config, listOf(provider), JdbcIntegrationStore("jdbc:sqlite::memory:"))

    @Test
    fun `webhook por HTTPS se acepta y por HTTP se rechaza`() = testApplication {
        application { pagoboxBackend(config, ingestion) }
        val body = """{"paymentId":"H1"}"""
        val sig = Crypto.hmacSha256Hex("s", body)

        val plain = client.post("/webhooks/sandbox/izipay") { header("x-test-signature", sig); setBody(body) }
        assertEquals(HttpStatusCode.Forbidden, plain.status)

        val secure = client.post("/webhooks/sandbox/izipay") {
            header("X-Forwarded-Proto", "https"); header("x-test-signature", sig); header("x-test-delivery", "h1"); setBody(body)
        }
        assertEquals(HttpStatusCode.OK, secure.status)
        assertEquals("ok", secure.bodyAsText())
    }
}
