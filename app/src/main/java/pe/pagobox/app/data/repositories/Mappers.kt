package pe.pagobox.app.data.repositories

import pe.pagobox.app.data.local.PaymentEventEntity
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
import java.time.Instant

fun PaymentEvent.toEntity(reason: String, now: Long): PaymentEventEntity = PaymentEventEntity(
    eventId = eventId,
    sequence = sequence,
    sourceChannel = sourceChannel.name,
    provider = provider?.name,
    sourceApplication = sourceApplication,
    sourceId = source.name,
    externalTransactionId = externalTransactionId,
    eventType = eventType.name,
    amountCents = money?.normalized?.movePointRight(2)?.longValueExact(),
    currency = money?.currency?.code,
    senderName = senderName,
    recipient = recipient,
    eventTimestamp = eventTimestamp.toEpochMilli(),
    detectedTimestamp = detectedTimestamp.toEpochMilli(),
    verificationStatus = verificationStatus.name,
    announcementStatus = announcementStatus.name,
    rawEventReference = rawEventReference,
    duplicateOf = duplicateOf,
    deviceId = deviceId,
    stateReason = reason,
    createdAt = now,
    updatedAt = now,
)

fun PaymentEventEntity.toDomain(): PaymentEvent = PaymentEvent(
    eventId = eventId,
    sequence = sequence,
    sourceChannel = SourceChannel.valueOf(sourceChannel),
    provider = provider?.let { ProviderId.valueOf(it) },
    sourceApplication = sourceApplication,
    source = SourceId.valueOf(sourceId),
    externalTransactionId = externalTransactionId,
    eventType = EventType.valueOf(eventType),
    money = if (amountCents != null && currency != null) {
        Money(BigDecimal.valueOf(amountCents, 2), Currency.valueOf(currency))
    } else {
        null
    },
    senderName = senderName,
    recipient = recipient,
    eventTimestamp = Instant.ofEpochMilli(eventTimestamp),
    detectedTimestamp = Instant.ofEpochMilli(detectedTimestamp),
    verificationStatus = VerificationStatus.valueOf(verificationStatus),
    announcementStatus = AnnouncementStatus.valueOf(announcementStatus),
    rawEventReference = rawEventReference,
    duplicateOf = duplicateOf,
    deviceId = deviceId,
)
