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
        UnknownFinancialAppEntity::class,
    ],
    version = 3,
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
    abstract fun unknownFinancialApps(): UnknownFinancialAppDao

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

        /**
         * v2 -> v3 (arquitectura híbrida):
         * - Registro permanente de apps con importes no asociadas a ninguna entidad (Canal A).
         * - Modelo normalizado de eventos: canal, proveedor, app de origen, id externo, destinatario,
         *   referencia al original y duplicado de; verificación separada del anuncio.
         *   Los estados antiguos se convierten sin inventar confirmaciones: ningún evento previo
         *   pasa a PROVIDER_CONFIRMED (antes no existía ningún proveedor).
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `unknown_financial_apps` (`packageName` TEXT NOT NULL, `appLabel` TEXT, " +
                        "`appVersion` TEXT, `installer` TEXT, `firstSeenAt` INTEGER NOT NULL, `lastSeenAt` INTEGER NOT NULL, " +
                        "`monetaryCount` INTEGER NOT NULL, `dismissed` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
                )
                db.execSQL("DROP INDEX IF EXISTS `index_payment_events_eventId`")
                db.execSQL("DROP INDEX IF EXISTS `index_payment_events_rawNotificationHash`")
                db.execSQL("DROP INDEX IF EXISTS `index_payment_events_announcementStatus`")
                db.execSQL("ALTER TABLE `payment_events` RENAME TO `payment_events_v2`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payment_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`eventId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `sourceChannel` TEXT NOT NULL, `provider` TEXT, " +
                        "`sourceApplication` TEXT, `sourceId` TEXT NOT NULL, `externalTransactionId` TEXT, `eventType` TEXT NOT NULL, " +
                        "`amountCents` INTEGER, `currency` TEXT, `senderName` TEXT, `recipient` TEXT, `eventTimestamp` INTEGER NOT NULL, " +
                        "`detectedTimestamp` INTEGER NOT NULL, `verificationStatus` TEXT NOT NULL, `announcementStatus` TEXT NOT NULL, " +
                        "`rawEventReference` TEXT NOT NULL, `duplicateOf` TEXT, `possibleMatchOf` TEXT, `testMode` INTEGER NOT NULL, " +
                        "`deviceId` TEXT, `stateReason` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_events_eventId` ON `payment_events` (`eventId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_events_rawEventReference` ON `payment_events` (`rawEventReference`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_events_announcementStatus` ON `payment_events` (`announcementStatus`)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_events_provider_externalTransactionId` " +
                        "ON `payment_events` (`provider`, `externalTransactionId`)",
                )
                db.execSQL(
                    "INSERT INTO `payment_events` (`id`, `eventId`, `sequence`, `sourceChannel`, `provider`, `sourceApplication`, " +
                        "`sourceId`, `externalTransactionId`, `eventType`, `amountCents`, `currency`, `senderName`, `recipient`, " +
                        "`eventTimestamp`, `detectedTimestamp`, `verificationStatus`, `announcementStatus`, `rawEventReference`, " +
                        "`duplicateOf`, `possibleMatchOf`, `testMode`, `deviceId`, `stateReason`, `createdAt`, `updatedAt`) " +
                        "SELECT `id`, `eventId`, `sequence`, 'ANDROID_NOTIFICATION', NULL, NULL, `sourceId`, NULL, `eventType`, " +
                        "`amountCents`, `currency`, `senderName`, NULL, `eventTimestamp`, `detectedTimestamp`, " +
                        "CASE `state` " +
                        "WHEN 'RECEIVED' THEN 'DETECTED' WHEN 'PARSED' THEN 'DETECTED' " +
                        "WHEN 'PENDING_REVIEW' THEN 'PENDING_VERIFICATION' WHEN 'VERIFIED' THEN 'PENDING_VERIFICATION' " +
                        "WHEN 'REJECTED' THEN 'REJECTED' WHEN 'DUPLICATE' THEN 'REJECTED' " +
                        "ELSE 'INCOME_CANDIDATE' END, " +
                        "`announcementStatus`, `rawNotificationHash`, NULL, NULL, 0, `deviceId`, `stateReason`, `createdAt`, `updatedAt` " +
                        "FROM `payment_events_v2`",
                )
                db.execSQL("DROP TABLE `payment_events_v2`")
            }
        }

        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(MIGRATION_1_2, MIGRATION_2_3)

        fun create(context: Context): PagoboxDatabase =
            Room.databaseBuilder(context, PagoboxDatabase::class.java, "pagobox.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
