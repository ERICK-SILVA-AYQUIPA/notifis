package pe.pagobox.domain.model

import java.time.Instant

/**
 * Evento de pago tal como lo maneja el sistema.
 * Solo contiene los datos necesarios; nunca el texto completo de la notificación
 * (sección 5: privacidad). [rawNotificationHash] permite deduplicar sin guardar el texto.
 */
data class PaymentEvent(
    val eventId: String,
    val sequence: Long,
    val source: SourceId,
    val eventType: EventType,
    val money: Money?,
    val senderName: String?,
    val eventTimestamp: Instant,
    val detectedTimestamp: Instant,
    val state: EventState,
    val verificationStatus: VerificationStatus,
    val announcementStatus: AnnouncementStatus,
    val rawNotificationHash: String,
    val deviceId: String? = null,
)
