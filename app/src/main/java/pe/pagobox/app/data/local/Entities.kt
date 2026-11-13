package pe.pagobox.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tablas de la sección 5. No se guarda el texto completo de las notificaciones:
 * solo los datos extraídos y una huella (hash) para deduplicar.
 */
@Entity(
    tableName = "payment_events",
    indices = [Index(value = ["eventId"], unique = true), Index("rawNotificationHash"), Index("announcementStatus")],
)
data class PaymentEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val sequence: Long,
    val sourceId: String,
    val eventType: String,
    /** Importe en céntimos para evitar errores de redondeo; null si la notificación no lo trae. */
    val amountCents: Long?,
    val currency: String?,
    val senderName: String?,
    val eventTimestamp: Long,
    val detectedTimestamp: Long,
    val state: String,
    val verificationStatus: String,
    val announcementStatus: String,
    val rawNotificationHash: String,
    val deviceId: String?,
    val stateReason: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "payment_sources")
data class PaymentSourceEntity(
    @PrimaryKey val sourceId: String,
    /** El comerciante puede desactivar la supervisión de una entidad. */
    val enabled: Boolean,
    /** Frase personalizada para esta entidad; null usa la predeterminada. */
    val customPhrase: String?,
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val bleAddress: String,
    val pairedAt: Long,
    val lastSeenAt: Long?,
    val lastAckedSequence: Long,
    val firmwareVersion: String?,
    val batteryPercent: Int?,
    val volume: Int?,
    val wifiIp: String?,
)

@Entity(tableName = "announcement_history", indices = [Index("eventId")])
data class AnnouncementHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val phrase: String,
    /** PHONE o DEVICE. */
    val output: String,
    /** DELIVERED, PLAYED o FAILED. */
    val result: String,
    val detail: String?,
    val timestamp: Long,
)

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey val id: Int = DEFAULT_ID,
    /** Nombre de la voz del motor TTS; null usa la voz española por defecto. */
    val voiceName: String?,
    val speechRate: Float,
    val pitch: Float,
    /** 0–100, volumen del anuncio en el celular. */
    val volume: Int,
    val announceSender: Boolean,
) {
    companion object {
        const val DEFAULT_ID = 1
    }
}

@Entity(tableName = "phrase_templates")
data class PhraseTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Entidad a la que aplica, o null para todas. */
    val sourceId: String?,
    /** Si aplica a pagos VERIFIED (true) o DETECTED (false). */
    val forVerified: Boolean,
    val template: String,
    val enabled: Boolean,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)

@Entity(tableName = "diagnostic_logs", indices = [Index("timestamp")])
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** INFO, WARN o ERROR. */
    val level: String,
    val component: String,
    val message: String,
)

/** Huellas de notificaciones ya procesadas: evita duplicados incluso tras reiniciar. */
@Entity(tableName = "seen_fingerprints")
data class SeenFingerprintEntity(
    @PrimaryKey val fingerprint: String,
    val seenAt: Long,
)

/**
 * Muestra ANONIMIZADA de una notificación (diagnóstico o formato desconocido).
 * Nunca se guarda el texto original. Se puede borrar desde la app.
 */
@Entity(tableName = "notification_samples", indices = [Index("capturedAt"), Index("packageName")])
data class NotificationSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val packageName: String,
    val appLabel: String?,
    val appVersion: String?,
    /** Entidad asociada al paquete, si existe. */
    val sourceId: String?,
    val category: String?,
    val titleAnon: String?,
    val textAnon: String?,
    val bigTextAnon: String?,
    val linesAnon: String?,
    val subTextAnon: String?,
    /** Solo los nombres de los extras disponibles, nunca sus valores. */
    val extraKeys: String,
    val postedAt: Long,
    val whenMillis: Long?,
    val listenerConnected: Boolean,
    /** DIAGNOSTIC, UNKNOWN_FORMAT o WAITING_FOR_SAMPLE. */
    val kind: String,
    val processingResult: String,
)

/** Paquete de Android asignado a una entidad desde el diagnóstico, con su huella de firma. */
@Entity(tableName = "package_assignments")
data class PackageAssignmentEntity(
    @PrimaryKey val packageName: String,
    val sourceId: String,
    val appLabel: String?,
    val appVersion: String?,
    val installer: String?,
    /** SHA-256 del certificado de firma: si cambia, la app deja de confiar en ese paquete. */
    val signingSha256: String?,
    val assignedAt: Long,
)

/** Estado en tiempo de ejecución de cada entidad (FORMAT_CHANGED / TEMPORARILY_UNAVAILABLE). */
@Entity(tableName = "source_status")
data class SourceStatusEntity(
    @PrimaryKey val sourceId: String,
    /** NONE, FORMAT_CHANGED o TEMPORARILY_UNAVAILABLE. */
    val override: String,
    val consecutiveUnknown: Int,
    val reason: String?,
    val updatedAt: Long,
)

/** Resultado de una prueba física en un celular real (fase 6). */
@Entity(tableName = "physical_test_results")
data class PhysicalTestResultEntity(
    @PrimaryKey val testId: String,
    /** NOT_RUN, PASSED, FAILED o BLOCKED. */
    val result: String,
    val notes: String,
    val deviceModel: String,
    val androidVersion: String,
    val appVersion: String,
    val testedAt: Long,
)

/**
 * App que publicó una notificación con un importe en soles o dólares y que no está asociada a
 * ninguna entidad (Canal A, registro de apps desconocidas). Solo se guardan datos de la app y
 * contadores: **nunca** el texto de la notificación. El usuario puede asignarla o descartarla.
 */
@Entity(tableName = "unknown_financial_apps")
data class UnknownFinancialAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String?,
    val appVersion: String?,
    val installer: String?,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    /** Notificaciones con importe vistas desde esta app. */
    val monetaryCount: Int,
    /** El usuario indicó que no es una app financiera: se sigue contando, pero no se muestra. */
    val dismissed: Boolean,
)
