package pe.pagobox.backend.http

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.security.Crypto
import pe.pagobox.backend.store.IntegrationStore
import pe.pagobox.backend.store.StoredPaymentEvent
import java.time.Instant

/**
 * Lectura de eventos oficiales por el celular PAGOBOX (Canal B → app). Solo lectura: el celular
 * no puede crear ni confirmar pagos. Cada token pertenece a un único entorno.
 */
class DeviceApi(private val config: BackendConfig, private val store: IntegrationStore) {
    fun environmentFor(authorization: String?): DeployEnvironment? {
        val token = authorization?.removePrefix("Bearer ")?.trim()?.takeIf { it.length >= 32 } ?: return null
        val hash = Crypto.sha256Hex(token.toByteArray())
        return config.deviceTokenHashes.entries.firstOrNull { (env, hashes) ->
            env in config.enabledEnvironments && hashes.any { Crypto.constantTimeEquals(it, hash) }
        }?.key
    }

    fun eventsJson(environment: DeployEnvironment, after: Instant, limit: Int): String {
        val events = store.paymentsSince(environment, after, limit.coerceIn(1, 200))
        val cursor = events.maxOfOrNull { it.updatedAt } ?: after
        return JsonObject(
            mapOf(
                "environment" to JsonPrimitive(environment.path),
                "cursor" to JsonPrimitive(cursor.toEpochMilli()),
                "events" to JsonArray(events.map { it.toJson() }),
            ),
        ).toString()
    }

    private fun StoredPaymentEvent.toJson(): JsonObject {
        val e = event
        fun s(v: String?) = v?.let { JsonPrimitive(it) } ?: JsonNull
        return JsonObject(
            mapOf(
                "eventId" to JsonPrimitive(e.eventId),
                "provider" to JsonPrimitive(e.provider!!.name),
                "source" to JsonPrimitive(e.source.name),
                "externalTransactionId" to JsonPrimitive(e.externalTransactionId!!),
                "eventType" to JsonPrimitive(e.eventType.name),
                "amount" to s(e.money?.normalized?.toPlainString()),
                "currency" to s(e.money?.currency?.code),
                "sender" to s(e.senderName),
                "recipient" to s(e.recipient),
                "eventTimestamp" to JsonPrimitive(e.eventTimestamp.toEpochMilli()),
                "verificationStatus" to JsonPrimitive(e.verificationStatus.name),
                "providerStatus" to JsonPrimitive(providerStatus),
                "updatedAt" to JsonPrimitive(updatedAt.toEpochMilli()),
            ),
        )
    }
}
