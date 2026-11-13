package pe.pagobox.backend.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.Currency
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceChannel
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.util.UUID

/**
 * Almacén JDBC. SQL compatible con SQLite (desarrollo y pruebas) y PostgreSQL (producción).
 * Las restricciones UNIQUE son la base de la idempotencia: una entrega y un pago se registran una vez.
 */
class JdbcIntegrationStore(url: String) : IntegrationStore {
    private val connection: Connection = DriverManager.getConnection(url)
    private val lock = Any()

    init {
        synchronized(lock) {
            connection.createStatement().use { st ->
                st.executeUpdate(
                    """CREATE TABLE IF NOT EXISTS raw_webhook_events (
                        id TEXT PRIMARY KEY, provider TEXT NOT NULL, environment TEXT NOT NULL, delivery_key TEXT NOT NULL,
                        received_at BIGINT NOT NULL, headers TEXT NOT NULL, body TEXT, body_sha256 TEXT NOT NULL,
                        signature_status TEXT NOT NULL, processing_status TEXT NOT NULL, detail TEXT, payment_event_id TEXT,
                        attempts INTEGER NOT NULL, updated_at BIGINT NOT NULL,
                        UNIQUE (provider, environment, delivery_key))""",
                )
                st.executeUpdate(
                    """CREATE TABLE IF NOT EXISTS payment_events (
                        event_id TEXT PRIMARY KEY, environment TEXT NOT NULL, provider TEXT NOT NULL, external_id TEXT NOT NULL,
                        source_id TEXT NOT NULL, event_type TEXT NOT NULL, amount_cents BIGINT, currency TEXT, sender TEXT,
                        recipient TEXT, event_ts BIGINT NOT NULL, detected_ts BIGINT NOT NULL, verification_status TEXT NOT NULL,
                        provider_status TEXT NOT NULL, raw_event_id TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
                        UNIQUE (provider, environment, external_id))""",
                )
                st.executeUpdate(
                    """CREATE TABLE IF NOT EXISTS audit_log (
                        id TEXT PRIMARY KEY, at BIGINT NOT NULL, environment TEXT, provider TEXT, action TEXT NOT NULL, detail TEXT NOT NULL)""",
                )
                st.executeUpdate(
                    """CREATE TABLE IF NOT EXISTS provider_status (
                        provider TEXT NOT NULL, environment TEXT NOT NULL, last_communication_at BIGINT, last_event_at BIGINT,
                        last_error TEXT, last_error_at BIGINT, PRIMARY KEY (provider, environment))""",
                )
            }
        }
    }

    private fun <T> tx(block: (Connection) -> T): T = synchronized(lock) { block(connection) }

    override fun insertRaw(event: RawWebhookEvent): Boolean = tx { c ->
        c.prepareStatement(
            """INSERT INTO raw_webhook_events (id, provider, environment, delivery_key, received_at, headers, body, body_sha256,
               signature_status, processing_status, detail, payment_event_id, attempts, updated_at)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING""",
        ).use { ps ->
            ps.setString(1, event.id); ps.setString(2, event.provider.name); ps.setString(3, event.environment.name)
            ps.setString(4, event.deliveryKey); ps.setLong(5, event.receivedAt.toEpochMilli())
            ps.setString(6, JsonObject(event.headers.mapValues { JsonPrimitive(it.value) }).toString())
            ps.setNullableString(7, event.body); ps.setString(8, event.bodySha256)
            ps.setString(9, event.signatureStatus.name); ps.setString(10, event.processingStatus.name)
            ps.setNullableString(11, event.detail); ps.setNullableString(12, event.paymentEventId)
            ps.setInt(13, event.attempts); ps.setLong(14, event.receivedAt.toEpochMilli())
            ps.executeUpdate() == 1
        }
    }

    override fun rawByDeliveryKey(provider: ProviderId, environment: DeployEnvironment, deliveryKey: String): RawWebhookEvent? = tx { c ->
        c.prepareStatement("SELECT * FROM raw_webhook_events WHERE provider = ? AND environment = ? AND delivery_key = ?").use { ps ->
            ps.setString(1, provider.name); ps.setString(2, environment.name); ps.setString(3, deliveryKey)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toRaw() else null }
        }
    }

    override fun updateRaw(id: String, status: ProcessingStatus, detail: String?, paymentEventId: String?, incrementAttempts: Boolean) {
        tx { c ->
            c.prepareStatement(
                """UPDATE raw_webhook_events SET processing_status = ?, detail = ?, payment_event_id = COALESCE(?, payment_event_id),
                   attempts = attempts + ?, updated_at = ? WHERE id = ?""",
            ).use { ps ->
                ps.setString(1, status.name); ps.setNullableString(2, detail); ps.setNullableString(3, paymentEventId)
                ps.setInt(4, if (incrementAttempts) 1 else 0); ps.setLong(5, System.currentTimeMillis()); ps.setString(6, id)
                ps.executeUpdate()
            }
        }
    }

    override fun paymentByExternalId(provider: ProviderId, environment: DeployEnvironment, externalId: String): StoredPaymentEvent? = tx { c ->
        c.prepareStatement("SELECT * FROM payment_events WHERE provider = ? AND environment = ? AND external_id = ?").use { ps ->
            ps.setString(1, provider.name); ps.setString(2, environment.name); ps.setString(3, externalId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toPayment() else null }
        }
    }

    override fun savePayment(environment: DeployEnvironment, event: PaymentEvent, providerStatus: String, rawEventId: String) {
        val now = System.currentTimeMillis()
        tx { c ->
            c.prepareStatement(
                """INSERT INTO payment_events (event_id, environment, provider, external_id, source_id, event_type, amount_cents, currency,
                   sender, recipient, event_ts, detected_ts, verification_status, provider_status, raw_event_id, created_at, updated_at)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                   ON CONFLICT (provider, environment, external_id) DO UPDATE SET
                     verification_status = excluded.verification_status, provider_status = excluded.provider_status,
                     amount_cents = excluded.amount_cents, currency = excluded.currency, raw_event_id = excluded.raw_event_id,
                     updated_at = excluded.updated_at""",
            ).use { ps ->
                ps.setString(1, event.eventId); ps.setString(2, environment.name); ps.setString(3, event.provider!!.name)
                ps.setString(4, event.externalTransactionId!!); ps.setString(5, event.source.name); ps.setString(6, event.eventType.name)
                val cents = event.money?.normalized?.movePointRight(2)?.longValueExact()
                if (cents == null) ps.setNull(7, Types.BIGINT) else ps.setLong(7, cents)
                ps.setNullableString(8, event.money?.currency?.code); ps.setNullableString(9, event.senderName)
                ps.setNullableString(10, event.recipient); ps.setLong(11, event.eventTimestamp.toEpochMilli())
                ps.setLong(12, event.detectedTimestamp.toEpochMilli()); ps.setString(13, event.verificationStatus.name)
                ps.setString(14, providerStatus); ps.setString(15, rawEventId); ps.setLong(16, now); ps.setLong(17, now)
                ps.executeUpdate()
            }
        }
    }

    override fun paymentsSince(environment: DeployEnvironment, since: Instant, limit: Int): List<StoredPaymentEvent> = tx { c ->
        c.prepareStatement("SELECT * FROM payment_events WHERE environment = ? AND updated_at > ? ORDER BY updated_at ASC LIMIT ?").use { ps ->
            ps.setString(1, environment.name); ps.setLong(2, since.toEpochMilli()); ps.setInt(3, limit)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toPayment()) } }
        }
    }

    override fun audit(entry: AuditEntry) {
        tx { c ->
            c.prepareStatement("INSERT INTO audit_log (id, at, environment, provider, action, detail) VALUES (?,?,?,?,?,?)").use { ps ->
                ps.setString(1, UUID.randomUUID().toString()); ps.setLong(2, entry.at.toEpochMilli())
                ps.setNullableString(3, entry.environment?.name); ps.setNullableString(4, entry.provider?.name)
                ps.setString(5, entry.action); ps.setString(6, entry.detail)
                ps.executeUpdate()
            }
        }
    }

    override fun auditTrail(limit: Int): List<AuditEntry> = tx { c ->
        c.prepareStatement("SELECT * FROM audit_log ORDER BY at DESC LIMIT ?").use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AuditEntry(
                                at = Instant.ofEpochMilli(rs.getLong("at")),
                                environment = rs.getString("environment")?.let { DeployEnvironment.valueOf(it) },
                                provider = rs.getString("provider")?.let { ProviderId.valueOf(it) },
                                action = rs.getString("action"),
                                detail = rs.getString("detail"),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun recordCommunication(provider: ProviderId, environment: DeployEnvironment, at: Instant, eventAt: Instant?, error: String?) {
        tx { c ->
            c.prepareStatement(
                """INSERT INTO provider_status (provider, environment, last_communication_at, last_event_at, last_error, last_error_at)
                   VALUES (?,?,?,?,?,?)
                   ON CONFLICT (provider, environment) DO UPDATE SET
                     last_communication_at = excluded.last_communication_at,
                     last_event_at = COALESCE(excluded.last_event_at, provider_status.last_event_at),
                     last_error = COALESCE(excluded.last_error, provider_status.last_error),
                     last_error_at = COALESCE(excluded.last_error_at, provider_status.last_error_at)""",
            ).use { ps ->
                ps.setString(1, provider.name); ps.setString(2, environment.name); ps.setLong(3, at.toEpochMilli())
                if (eventAt == null) ps.setNull(4, Types.BIGINT) else ps.setLong(4, eventAt.toEpochMilli())
                ps.setNullableString(5, error)
                if (error == null) ps.setNull(6, Types.BIGINT) else ps.setLong(6, at.toEpochMilli())
                ps.executeUpdate()
            }
        }
    }

    override fun providerHealth(provider: ProviderId, environment: DeployEnvironment): ProviderHealth = tx { c ->
        c.prepareStatement("SELECT * FROM provider_status WHERE provider = ? AND environment = ?").use { ps ->
            ps.setString(1, provider.name); ps.setString(2, environment.name)
            ps.executeQuery().use { rs ->
                if (!rs.next()) {
                    ProviderHealth(provider, environment, null, null, null, null)
                } else {
                    ProviderHealth(
                        provider, environment,
                        rs.getNullableLong("last_communication_at")?.let(Instant::ofEpochMilli),
                        rs.getNullableLong("last_event_at")?.let(Instant::ofEpochMilli),
                        rs.getString("last_error"),
                        rs.getNullableLong("last_error_at")?.let(Instant::ofEpochMilli),
                    )
                }
            }
        }
    }

    private fun ResultSet.toRaw() = RawWebhookEvent(
        id = getString("id"),
        provider = ProviderId.valueOf(getString("provider")),
        environment = DeployEnvironment.valueOf(getString("environment")),
        deliveryKey = getString("delivery_key"),
        receivedAt = Instant.ofEpochMilli(getLong("received_at")),
        headers = Json.parseToJsonElement(getString("headers")).jsonObject.mapValues { it.value.jsonPrimitive.content },
        body = getString("body"),
        bodySha256 = getString("body_sha256"),
        signatureStatus = SignatureStatus.valueOf(getString("signature_status")),
        processingStatus = ProcessingStatus.valueOf(getString("processing_status")),
        detail = getString("detail"),
        paymentEventId = getString("payment_event_id"),
        attempts = getInt("attempts"),
    )

    private fun ResultSet.toPayment(): StoredPaymentEvent {
        val cents = getNullableLong("amount_cents")
        val currency = getString("currency")
        val event = PaymentEvent(
            eventId = getString("event_id"),
            sequence = 0,
            sourceChannel = SourceChannel.OFFICIAL_WEBHOOK,
            provider = ProviderId.valueOf(getString("provider")),
            source = SourceId.valueOf(getString("source_id")),
            externalTransactionId = getString("external_id"),
            eventType = EventType.valueOf(getString("event_type")),
            money = if (cents != null && currency != null) Money(BigDecimal.valueOf(cents, 2), Currency.valueOf(currency)) else null,
            senderName = getString("sender"),
            recipient = getString("recipient"),
            eventTimestamp = Instant.ofEpochMilli(getLong("event_ts")),
            detectedTimestamp = Instant.ofEpochMilli(getLong("detected_ts")),
            verificationStatus = VerificationStatus.valueOf(getString("verification_status")),
            announcementStatus = AnnouncementStatus.PENDING,
            rawEventReference = getString("raw_event_id"),
        )
        return StoredPaymentEvent(DeployEnvironment.valueOf(getString("environment")), event, getString("provider_status"))
    }

    private fun ResultSet.getNullableLong(column: String): Long? = getLong(column).let { if (wasNull()) null else it }

    private fun PreparedStatement.setNullableString(index: Int, value: String?) {
        if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)
    }
}
