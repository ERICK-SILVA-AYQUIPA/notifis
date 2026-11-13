package pe.pagobox.app.data.repositories

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import pe.pagobox.app.data.local.PagoboxDatabase
import pe.pagobox.app.data.local.PaymentEventEntity
import pe.pagobox.app.data.local.SeenFingerprintEntity
import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.VerificationStatus
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

    /** Máxima secuencia guardada en el historial (puede bajar si se aplica la retención). */
    suspend fun maxStoredSequence(): Long = dao.maxSequence()

    suspend fun save(event: PaymentEvent, reason: String) {
        dao.insert(event.toEntity(reason, System.currentTimeMillis()))
    }

    fun observeRecent(limit: Int = 200): Flow<List<PaymentEventEntity>> = dao.observeRecent(limit)

    fun observe(eventId: String): Flow<PaymentEventEntity?> = dao.observe(eventId)

    /** Resumen del día en curso; cambia de día automáticamente a medianoche (corrige E6). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(zone: ZoneId = ZoneId.of("America/Lima")): Flow<DailySummary> = flow {
        while (true) {
            emit(LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli())
            delay(60_000)
        }
    }.distinctUntilChanged().flatMapLatest { start -> summarySince(start) }

    private fun summarySince(start: Long): Flow<DailySummary> {
        return dao.observeSince(start).map { events ->
            val incomes = events.filter {
                it.verificationStatus in INCOME_STATES && it.amountCents != null && it.currency == "PEN"
            }
            val verified = incomes.filter { it.verificationStatus == "PROVIDER_CONFIRMED" }
            DailySummary(
                detectedTotalCents = incomes.filter { it.verificationStatus == "INCOME_CANDIDATE" }.sumOf { it.amountCents!! },
                verifiedTotalCents = verified.sumOf { it.amountCents!! },
                eventCount = events.size,
                verifiedCount = verified.size,
                lastPayment = incomes.maxByOrNull { it.sequence },
            )
        }
    }

    suspend fun byProviderExternalId(provider: String, externalId: String): PaymentEvent? =
        dao.byProviderExternalId(provider, externalId)?.toDomain()

    suspend fun androidCandidatesSince(from: Long): List<PaymentEvent> = dao.androidCandidatesSince(from).map { it.toDomain() }

    suspend fun markDuplicate(eventId: String, duplicateOf: String, reason: String) =
        dao.markDuplicate(eventId, duplicateOf, reason, System.currentTimeMillis())

    suspend fun setPossibleMatch(eventId: String, other: String) = dao.setPossibleMatch(eventId, other, System.currentTimeMillis())

    suspend fun updateOfficial(eventId: String, status: VerificationStatus, money: Money?, announcement: AnnouncementStatus, reason: String) =
        dao.updateOfficial(
            eventId, status.name, money?.normalized?.movePointRight(2)?.longValueExact(), money?.currency?.code,
            announcement.name, reason, System.currentTimeMillis(),
        )

    suspend fun pendingAnnouncements(): List<PaymentEvent> = dao.pendingAnnouncements().map { it.toDomain() }

    suspend fun announcedIds(): List<String> = dao.announcedIds()

    suspend fun updateAnnouncement(eventId: String, status: AnnouncementStatus) =
        dao.updateAnnouncement(eventId, status.name, System.currentTimeMillis())

    suspend fun applyRetention(days: Int) {
        val before = System.currentTimeMillis() - days * 24L * 3600 * 1000
        dao.deleteOlderThan(before)
        db.announcementHistory().deleteOlderThan(before)
        db.diagnosticLogs().deleteOlderThan(before)
        db.seenFingerprints().deleteOlderThan(System.currentTimeMillis() - 2L * 24 * 3600 * 1000)
        db.notificationSamples().deleteOlderThan(before)
    }

    private companion object {
        /** Posibles ingresos (Canal A) y pagos confirmados (Canal B). Los duplicados no suman dos veces. */
        val INCOME_STATES = setOf("INCOME_CANDIDATE", "PROVIDER_CONFIRMED")
    }
}
