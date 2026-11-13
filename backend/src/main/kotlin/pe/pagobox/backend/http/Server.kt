package pe.pagobox.backend.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.ingest.WebhookIngestion
import pe.pagobox.backend.providers.WebhookRequest
import java.time.Clock

/**
 * Rutas HTTP. Los webhooks entran por `POST /webhooks/{entorno}/{proveedor}`, p. ej.
 * `/webhooks/sandbox/mercado-pago`. La terminación TLS la hace el proxy inverso; si
 * [BackendConfig.requireHttps] está activo, se rechaza lo que no llegó por HTTPS.
 */
fun Application.pagoboxBackend(
    config: BackendConfig,
    ingestion: WebhookIngestion,
    deviceApi: DeviceApi? = null,
    clock: Clock = Clock.systemUTC(),
) {
    routing {
        get("/health") { call.respondText("ok") }

        // Canal B → celular: eventos oficiales del entorno al que pertenece el token.
        get("/api/v1/events") {
            val api = deviceApi
            if (api == null) {
                call.respondText("not found", status = HttpStatusCode.NotFound)
                return@get
            }
            val https = call.request.headers["X-Forwarded-Proto"]?.lowercase() == "https" || call.request.origin.scheme == "https"
            if (config.requireHttps && !https) {
                call.respondText("https required", status = HttpStatusCode.Forbidden)
                return@get
            }
            val env = api.environmentFor(call.request.headers["Authorization"])
            if (env == null) {
                call.respondText("unauthorized", status = HttpStatusCode.Unauthorized)
                return@get
            }
            val after = java.time.Instant.ofEpochMilli(call.request.queryParameters["after"]?.toLongOrNull() ?: 0L)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            call.respondText(api.eventsJson(env, after, limit), ContentType.Application.Json)
        }

        post("/webhooks/{environment}/{provider}") {
            val forwardedProto = call.request.headers["X-Forwarded-Proto"]?.lowercase()
            val https = forwardedProto == "https" || call.request.origin.scheme == "https"
            if (config.requireHttps && !https) {
                call.respondText("https required", status = HttpStatusCode.Forbidden)
                return@post
            }
            val declared = call.request.contentLength()
            if (declared != null && declared > config.maxBodyBytes) {
                call.respondText("payload too large", status = HttpStatusCode.PayloadTooLarge)
                return@post
            }
            val body = call.receiveChannel().readRemaining(config.maxBodyBytes.toLong() + 1).readBytes()
            val request = WebhookRequest(
                headers = call.request.headers.entries().associate { (k, v) -> k.lowercase() to v.joinToString(",") },
                query = call.request.queryParameters.entries().associate { (k, v) -> k to v.first() },
                body = body,
                receivedAt = clock.instant(),
            )
            val result = ingestion.ingest(
                call.parameters["environment"].orEmpty(),
                call.parameters["provider"].orEmpty(),
                request,
            )
            call.respondText(result.message, ContentType.Text.Plain, HttpStatusCode.fromValue(result.httpStatus))
        }
    }
}
