package pe.pagobox.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: PaymentEventEntity): Long

    @Query("SELECT * FROM payment_events WHERE eventId = :eventId")
    suspend fun byEventId(eventId: String): PaymentEventEntity?

    @Query("SELECT * FROM payment_events WHERE eventId = :eventId")
    fun observe(eventId: String): Flow<PaymentEventEntity?>

    @Query("SELECT * FROM payment_events ORDER BY sequence DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PaymentEventEntity>>

    @Query("SELECT * FROM payment_events WHERE detectedTimestamp >= :from ORDER BY sequence DESC")
    fun observeSince(from: Long): Flow<List<PaymentEventEntity>>

    @Query("SELECT * FROM payment_events WHERE announcementStatus IN ('PENDING','DELIVERED') ORDER BY sequence ASC")
    suspend fun pendingAnnouncements(): List<PaymentEventEntity>

    @Query("SELECT eventId FROM payment_events WHERE announcementStatus = 'ANNOUNCED'")
    suspend fun announcedIds(): List<String>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM payment_events")
    suspend fun maxSequence(): Long

    @Query(
        "UPDATE payment_events SET announcementStatus = :status, state = :state, updatedAt = :now " +
            "WHERE eventId = :eventId",
    )
    suspend fun updateAnnouncement(eventId: String, status: String, state: String, now: Long)

    @Query(
        "UPDATE payment_events SET verificationStatus = 'VERIFIED', state = 'VERIFIED', updatedAt = :now " +
            "WHERE eventId = :eventId",
    )
    suspend fun markVerified(eventId: String, now: Long)

    @Query("DELETE FROM payment_events WHERE detectedTimestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface PaymentSourceDao {
    @Query("SELECT * FROM payment_sources")
    fun observeAll(): Flow<List<PaymentSourceEntity>>

    @Query("SELECT * FROM payment_sources")
    suspend fun all(): List<PaymentSourceEntity>

    @Upsert
    suspend fun upsert(source: PaymentSourceEntity)
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY pairedAt DESC")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices ORDER BY pairedAt DESC LIMIT 1")
    suspend fun current(): DeviceEntity?

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)
}

@Dao
interface AnnouncementHistoryDao {
    @Insert
    suspend fun insert(entry: AnnouncementHistoryEntity)

    @Query("SELECT * FROM announcement_history WHERE eventId = :eventId ORDER BY timestamp ASC")
    fun observeFor(eventId: String): Flow<List<AnnouncementHistoryEntity>>

    @Query("DELETE FROM announcement_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profiles WHERE id = 1")
    fun observe(): Flow<VoiceProfileEntity?>

    @Query("SELECT * FROM voice_profiles WHERE id = 1")
    suspend fun get(): VoiceProfileEntity?

    @Upsert
    suspend fun upsert(profile: VoiceProfileEntity)
}

@Dao
interface PhraseTemplateDao {
    @Query("SELECT * FROM phrase_templates ORDER BY id ASC")
    fun observeAll(): Flow<List<PhraseTemplateEntity>>

    @Query("SELECT * FROM phrase_templates WHERE enabled = 1 ORDER BY id DESC")
    suspend fun enabled(): List<PhraseTemplateEntity>

    @Upsert
    suspend fun upsert(template: PhraseTemplateEntity)

    @Query("DELETE FROM phrase_templates WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingEntity>>

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun upsert(setting: AppSettingEntity)
}

@Dao
interface DiagnosticLogDao {
    @Insert
    suspend fun insert(log: DiagnosticLogEntity)

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DiagnosticLogEntity>>

    @Query("DELETE FROM diagnostic_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface SeenFingerprintDao {
    @Query("SELECT seenAt FROM seen_fingerprints WHERE fingerprint = :fingerprint")
    fun lastSeen(fingerprint: String): Long?

    @Upsert
    fun upsert(entity: SeenFingerprintEntity)

    @Query("DELETE FROM seen_fingerprints WHERE seenAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface NotificationSampleDao {
    @Insert
    suspend fun insert(sample: NotificationSampleEntity): Long

    @Query("SELECT * FROM notification_samples ORDER BY capturedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationSampleEntity>>

    @Query("SELECT * FROM notification_samples ORDER BY capturedAt DESC")
    suspend fun all(): List<NotificationSampleEntity>

    @Query("SELECT sourceId AS sourceId, COUNT(*) AS count FROM notification_samples WHERE sourceId IS NOT NULL GROUP BY sourceId")
    fun observeCountsBySource(): Flow<List<SourceCount>>

    @Query("DELETE FROM notification_samples WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notification_samples")
    suspend fun deleteAll()

    @Query("DELETE FROM notification_samples WHERE capturedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

data class SourceCount(val sourceId: String, val count: Int)

@Dao
interface PackageAssignmentDao {
    @Query("SELECT * FROM package_assignments")
    fun observeAll(): Flow<List<PackageAssignmentEntity>>

    @Upsert
    suspend fun upsert(assignment: PackageAssignmentEntity)

    @Query("DELETE FROM package_assignments WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface SourceStatusDao {
    @Query("SELECT * FROM source_status")
    fun observeAll(): Flow<List<SourceStatusEntity>>

    @Query("SELECT * FROM source_status WHERE sourceId = :sourceId")
    suspend fun get(sourceId: String): SourceStatusEntity?

    @Upsert
    suspend fun upsert(status: SourceStatusEntity)
}

@Dao
interface PhysicalTestDao {
    @Query("SELECT * FROM physical_test_results")
    fun observeAll(): Flow<List<PhysicalTestResultEntity>>

    @Query("SELECT * FROM physical_test_results")
    suspend fun all(): List<PhysicalTestResultEntity>

    @Upsert
    suspend fun upsert(result: PhysicalTestResultEntity)

    @Query("DELETE FROM physical_test_results")
    suspend fun deleteAll()
}

@Dao
interface UnknownFinancialAppDao {
    @Query("SELECT * FROM unknown_financial_apps WHERE dismissed = 0 ORDER BY lastSeenAt DESC")
    fun observeActive(): Flow<List<UnknownFinancialAppEntity>>

    @Query("SELECT * FROM unknown_financial_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): UnknownFinancialAppEntity?

    @Upsert
    suspend fun upsert(app: UnknownFinancialAppEntity)

    @Query("UPDATE unknown_financial_apps SET dismissed = :dismissed WHERE packageName = :packageName")
    suspend fun setDismissed(packageName: String, dismissed: Boolean)

    @Query("DELETE FROM unknown_financial_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
