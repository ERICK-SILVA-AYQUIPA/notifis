package pe.pagobox.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PaymentEventEntity::class,
        PaymentSourceEntity::class,
        DeviceEntity::class,
        AnnouncementHistoryEntity::class,
        VoiceProfileEntity::class,
        PhraseTemplateEntity::class,
        AppSettingEntity::class,
        DiagnosticLogEntity::class,
        SeenFingerprintEntity::class,
        NotificationSampleEntity::class,
        PackageAssignmentEntity::class,
        SourceStatusEntity::class,
        PhysicalTestResultEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PagoboxDatabase : RoomDatabase() {
    abstract fun paymentEvents(): PaymentEventDao
    abstract fun paymentSources(): PaymentSourceDao
    abstract fun devices(): DeviceDao
    abstract fun announcementHistory(): AnnouncementHistoryDao
    abstract fun voiceProfiles(): VoiceProfileDao
    abstract fun phraseTemplates(): PhraseTemplateDao
    abstract fun appSettings(): AppSettingDao
    abstract fun diagnosticLogs(): DiagnosticLogDao
    abstract fun seenFingerprints(): SeenFingerprintDao
    abstract fun notificationSamples(): NotificationSampleDao
    abstract fun packageAssignments(): PackageAssignmentDao
    abstract fun sourceStatus(): SourceStatusDao
    abstract fun physicalTests(): PhysicalTestDao

    companion object {
        /** Las migraciones futuras se agregan aquí; nunca se usa destructive migration. */
        /** v1 -> v2: diagnóstico de notificaciones, asignación de paquetes, estado por entidad y pruebas físicas. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notification_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`capturedAt` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT, `appVersion` TEXT, " +
                        "`sourceId` TEXT, `category` TEXT, `titleAnon` TEXT, `textAnon` TEXT, `bigTextAnon` TEXT, " +
                        "`linesAnon` TEXT, `subTextAnon` TEXT, `extraKeys` TEXT NOT NULL, `postedAt` INTEGER NOT NULL, " +
                        "`whenMillis` INTEGER, `listenerConnected` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                        "`processingResult` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_samples_capturedAt` ON `notification_samples` (`capturedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_samples_packageName` ON `notification_samples` (`packageName`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `package_assignments` (`packageName` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                        "`appLabel` TEXT, `appVersion` TEXT, `installer` TEXT, `signingSha256` TEXT, " +
                        "`assignedAt` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `source_status` (`sourceId` TEXT NOT NULL, `override` TEXT NOT NULL, " +
                        "`consecutiveUnknown` INTEGER NOT NULL, `reason` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `physical_test_results` (`testId` TEXT NOT NULL, `result` TEXT NOT NULL, " +
                        "`notes` TEXT NOT NULL, `deviceModel` TEXT NOT NULL, `androidVersion` TEXT NOT NULL, " +
                        "`appVersion` TEXT NOT NULL, `testedAt` INTEGER NOT NULL, PRIMARY KEY(`testId`))",
                )
            }
        }

        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(MIGRATION_1_2)

        fun create(context: Context): PagoboxDatabase =
            Room.databaseBuilder(context, PagoboxDatabase::class.java, "pagobox.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
