package pe.pagobox.backend.http

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.providers.ProviderAdapter
import pe.pagobox.backend.providers.Providers
import pe.pagobox.backend.security.Crypto
import pe.pagobox.backend.store.ProviderHealth
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.backend.store.IntegrationStore
import pe.pagobox.backend.store.StoredPaymentEvent
import java.time.Instant

/**
 * Lectura de eventos oficiales por el celular PAGOBOX (Canal B → app). Solo lectura: el celular
 * no puede crear ni confirmar pagos. Cada token pertenece a un único entorno.
 */
class DeviceApi(
    private val config: BackendConfig,
    private val store: IntegrationStore,
    private val adapters: List<ProviderAdapter> = Providers.all(),
) {
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

    /**
     * Estado de cada proveedor en el entorno del token, para el panel de integraciones. Nunca devuelve
     * credenciales: solo si están configuradas. «ACTIVE» exige que el backend haya procesado al menos un
     * webhook comprobado (firma o consulta a la API del proveedor) y que no haya un error posterior.
     */
    fun integrationsJson(environment: DeployEnvironment): String {
        val providers = ProviderId.entries.map { id ->
            val adapter = adapters.firstOrNull { it.id == id }
            val configured = adapter != null && config.credentialsFor(id, environment).has(adapter.requiredCredentials)
            val health = store.providerHealth(id, environment)
            JsonObject(
                mapOf(
                    "provider" to JsonPrimitive(id.name),
                    "implemented" to JsonPrimitive(adapter != null),
                    "credentialsConfigured" to JsonPrimitive(configured),
                    "webhookPath" to (adapter?.let { JsonPrimitive("/webhooks/${environment.path}/${id.pathSegment()}") } ?: JsonNull),
                    "lastCommunicationAt" to millis(health.lastCommunicationAt),
                    "lastEventAt" to millis(health.lastEventAt),
                    "lastError" to (health.lastError?.let { JsonPrimitive(it) } ?: JsonNull),
                    "lastErrorAt" to millis(health.lastErrorAt),
                    "status" to JsonPrimitive(integrationStatus(adapter != null, configured, health).name),
                ),
            )
        }
        return JsonObject(
            mapOf("environment" to JsonPrimitive(environment.path), "providers" to JsonArray(providers)),
        ).toString()
    }

    private fun millis(i: Instant?) = i?.let { JsonPrimitive(it.toEpochMilli()) } ?: JsonNull

    private fun ProviderId.pathSegment() = name.lowercase().replace('_', '-')

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

/** Estado mostrado en el panel. Nunca «ACTIVE» sin un webhook real procesado y comprobado. */
enum class IntegrationStatus { NOT_IMPLEMENTED, NOT_CONFIGURED, AWAITING_FIRST_EVENT, ACTIVE, ERROR }

fun integrationStatus(implemented: Boolean, configured: Boolean, health: ProviderHealth): IntegrationStatus {
    if (!implemented) return IntegrationStatus.NOT_IMPLEMENTED
    if (!configured) return IntegrationStatus.NOT_CONFIGURED
    val lastEvent = health.lastEventAt
    val lastError = health.lastErrorAt
    return when {
        lastError != null && (lastEvent == null || lastError.isAfter(lastEvent)) -> IntegrationStatus.ERROR
        lastEvent != null -> IntegrationStatus.ACTIVE
        else -> IntegrationStatus.AWAITING_FIRST_EVENT
    }
}
