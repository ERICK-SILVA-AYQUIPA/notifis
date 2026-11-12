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
    ],
    version = 1,
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

    companion object {
        /** Las migraciones futuras se agregan aquí; nunca se usa destructive migration. */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        fun create(context: Context): PagoboxDatabase =
            Room.databaseBuilder(context, PagoboxDatabase::class.java, "pagobox.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
