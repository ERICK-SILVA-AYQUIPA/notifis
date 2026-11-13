package pe.pagobox.domain.model

import java.time.Instant

/**
 * Evento de pago normalizado: lo producen ambos canales y lo consume el motor central.
 *
 * Privacidad: nunca contiene el texto completo de una notificación ni el cuerpo de un webhook.
 * [rawEventReference] apunta al original sin copiarlo:
 * - Canal A: huella SHA-256 de la notificación (permite deduplicar sin guardar el texto).
 * - Canal B: identificador del registro original guardado en el backend (auditoría).
 */
data class PaymentEvent(
    val eventId: String,
    /** Orden de entrega al equipo (monotónico en el celular). */
    val sequence: Long,
    val sourceChannel: SourceChannel,
    /** Proveedor oficial (solo Canal B). */
    val provider: ProviderId? = null,
    /** Paquete Android que publicó la notificación (solo Canal A). */
    val sourceApplication: String? = null,
    /** Billetera o banco del pago (Yape, Plin, BCP…), si se conoce. */
    val source: SourceId,
    /** Identificador del pago en el proveedor (solo Canal B; obligatorio allí). */
    val externalTransactionId: String? = null,
    val eventType: EventType,
    /** Importe y moneda. */
    val money: Money?,
    /** Remitente, solo si la fuente lo informa. */
    val senderName: String?,
    /** Destinatario (cuenta/comercio), solo si la fuente lo informa. */
    val recipient: String? = null,
    /** Momento del pago según la fuente. */
    val eventTimestamp: Instant,
    /** Momento en que PAGOBOX lo registró. */
    val detectedTimestamp: Instant,
    val verificationStatus: VerificationStatus,
    val announcementStatus: AnnouncementStatus,
    val rawEventReference: String,
    /** Si es DUPLICATE: evento que representa el mismo pago. */
    val duplicateOf: String? = null,
    val deviceId: String? = null,
) {
    init {
        require(sourceChannel == SourceChannel.OFFICIAL_WEBHOOK || verificationStatus != VerificationStatus.PROVIDER_CONFIRMED) {
            "Una notificación Android no puede estar confirmada por un proveedor"
        }
        if (sourceChannel == SourceChannel.OFFICIAL_WEBHOOK) {
            require(provider != null) { "Un evento oficial requiere proveedor" }
            require(!externalTransactionId.isNullOrBlank()) { "Un evento oficial requiere el identificador del proveedor" }
        }
        require((verificationStatus == VerificationStatus.DUPLICATE) == (duplicateOf != null)) {
            "duplicateOf se informa solo (y siempre) en eventos DUPLICATE"
        }
    }

    val isProviderConfirmed: Boolean get() = verificationStatus == VerificationStatus.PROVIDER_CONFIRMED
}
