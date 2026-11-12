package pe.pagobox.app.data.repositories

import pe.pagobox.app.data.local.PaymentEventEntity
import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.Currency
import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import java.math.BigDecimal
import java.time.Instant

fun PaymentEvent.toEntity(reason: String, now: Long): PaymentEventEntity = PaymentEventEntity(
    eventId = eventId,
    sequence = sequence,
    sourceId = source.name,
    eventType = eventType.name,
    amountCents = money?.normalized?.movePointRight(2)?.longValueExact(),
    currency = money?.currency?.code,
    senderName = senderName,
    eventTimestamp = eventTimestamp.toEpochMilli(),
    detectedTimestamp = detectedTimestamp.toEpochMilli(),
    state = state.name,
    verificationStatus = verificationStatus.name,
    announcementStatus = announcementStatus.name,
    rawNotificationHash = rawNotificationHash,
    deviceId = deviceId,
    stateReason = reason,
    createdAt = now,
    updatedAt = now,
)

fun PaymentEventEntity.toDomain(): PaymentEvent = PaymentEvent(
    eventId = eventId,
    sequence = sequence,
    source = SourceId.valueOf(sourceId),
    eventType = EventType.valueOf(eventType),
    money = if (amountCents != null && currency != null) {
        Money(BigDecimal.valueOf(amountCents, 2), Currency.valueOf(currency))
    } else {
        null
    },
    senderName = senderName,
    eventTimestamp = Instant.ofEpochMilli(eventTimestamp),
    detectedTimestamp = Instant.ofEpochMilli(detectedTimestamp),
    state = EventState.valueOf(state),
    verificationStatus = VerificationStatus.valueOf(verificationStatus),
    announcementStatus = AnnouncementStatus.valueOf(announcementStatus),
    rawNotificationHash = rawNotificationHash,
    deviceId = deviceId,
)
