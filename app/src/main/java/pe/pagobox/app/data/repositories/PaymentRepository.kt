package pe.pagobox.app.data.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.pagobox.app.data.local.PagoboxDatabase
import pe.pagobox.app.data.local.PaymentEventEntity
import pe.pagobox.app.data.local.SeenFingerprintEntity
import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.engine.deduplication.SeenFingerprintStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Resumen del día para el panel principal: detectados y verificados separados (sección 8). */
data class DailySummary(
    val detectedTotalCents: Long,
    val verifiedTotalCents: Long,
    val eventCount: Int,
    val verifiedCount: Int,
    val lastPayment: PaymentEventEntity?,
)

class PaymentRepository(private val db: PagoboxDatabase) {
    private val dao = db.paymentEvents()

    /** Huellas persistidas en Room para que la deduplicación sobreviva reinicios. */
    val fingerprintStore = object : SeenFingerprintStore {
        override fun lastSeen(fingerprint: String): Instant? =
            db.seenFingerprints().lastSeen(fingerprint)?.let(Instant::ofEpochMilli)

        override fun markSeen(fingerprint: String, at: Instant) =
            db.seenFingerprints().upsert(SeenFingerprintEntity(fingerprint, at.toEpochMilli()))
    }

    suspend fun nextSequence(): Long = dao.maxSequence() + 1

    suspend fun save(event: PaymentEvent, reason: String) {
        dao.insert(event.toEntity(reason, System.currentTimeMillis()))
    }

    fun observeRecent(limit: Int = 200): Flow<List<PaymentEventEntity>> = dao.observeRecent(limit)

    fun observe(eventId: String): Flow<PaymentEventEntity?> = dao.observe(eventId)

    fun observeToday(zone: ZoneId = ZoneId.of("America/Lima")): Flow<DailySummary> {
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.observeSince(start).map { events ->
            val incomes = events.filter {
                it.state in INCOME_STATES && it.amountCents != null && it.currency == "PEN"
            }
            val verified = incomes.filter { it.verificationStatus == "VERIFIED" }
            DailySummary(
                detectedTotalCents = incomes.filter { it.verificationStatus == "DETECTED" }.sumOf { it.amountCents!! },
                verifiedTotalCents = verified.sumOf { it.amountCents!! },
                eventCount = events.size,
                verifiedCount = verified.size,
                lastPayment = incomes.maxByOrNull { it.sequence },
            )
        }
    }

    suspend fun pendingAnnouncements(): List<PaymentEvent> = dao.pendingAnnouncements().map { it.toDomain() }

    suspend fun announcedIds(): List<String> = dao.announcedIds()

    suspend fun updateAnnouncement(eventId: String, status: AnnouncementStatus, state: EventState) =
        dao.updateAnnouncement(eventId, status.name, state.name, System.currentTimeMillis())

    suspend fun applyRetention(days: Int) {
        val before = System.currentTimeMillis() - days * 24L * 3600 * 1000
        dao.deleteOlderThan(before)
        db.announcementHistory().deleteOlderThan(before)
        db.diagnosticLogs().deleteOlderThan(before)
        db.seenFingerprints().deleteOlderThan(System.currentTimeMillis() - 2L * 24 * 3600 * 1000)
    }

    private companion object {
        val INCOME_STATES = setOf("INCOME_CANDIDATE", "VERIFIED", "ANNOUNCED", "DELIVERY_FAILED")
    }
}
