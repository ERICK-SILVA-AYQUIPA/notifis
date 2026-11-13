package pe.pagobox.backend.ingest

import pe.pagobox.backend.config.BackendConfig
import pe.pagobox.backend.config.DeployEnvironment
import pe.pagobox.backend.providers.Extraction
import pe.pagobox.backend.providers.ProviderAdapter
import pe.pagobox.backend.providers.ProviderPayment
import pe.pagobox.backend.providers.ProviderPaymentStatus
import pe.pagobox.backend.providers.ProviderRejectedException
import pe.pagobox.backend.providers.ProviderUnavailableException
import pe.pagobox.backend.providers.SignatureCheck
import pe.pagobox.backend.providers.WebhookRequest
import pe.pagobox.backend.security.Crypto
import pe.pagobox.backend.store.AuditEntry
import pe.pagobox.backend.store.IntegrationStore
import pe.pagobox.backend.store.ProcessingStatus
import pe.pagobox.backend.store.RawWebhookEvent
import pe.pagobox.backend.store.SignatureStatus
import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.SourceChannel
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.verification.ProviderConfirmation
import pe.pagobox.engine.verification.VerificationEngine
import java.time.Clock
import java.util.UUID

/** Respuesta HTTP al proveedor. El cuerpo nunca revela detalles internos. */
data class IngestResult(val httpStatus: Int, val message: String, val eventId: String? = null)

/**
 * Recepción de webhooks del Canal B. Orden de controles:
 * 1. entorno habilitado y proveedor conocido;
 * 2. credenciales presentes (si no, el proveedor está «sin configurar»);
 * 3. tamaño del cuerpo;
 * 4. firma criptográfica (inválida → 401, se registra solo la huella del cuerpo);
 * 5. idempotencia por entrega (protección contra repeticiones);
 * 6. obtención del pago (API del proveedor, con reintentos);
 * 7. separación sandbox / producción (el proveedor debe declarar el mismo modo);
 * 8. verificación en el motor ([VerificationEngine]) e idempotencia por id del pago.
 * Todo queda en el evento original y en la auditoría.
 */
class WebhookIngestion(
    private val config: BackendConfig,
    private val providers: List<ProviderAdapter>,
    private val store: IntegrationStore,
    private val retry: Retry = Retry(),
    private val clock: Clock = Clock.systemUTC(),
    private val verification: VerificationEngine = VerificationEngine(),
) {
    private val byPath = providers.associateBy { it.id.name.lowercase().replace('_', '-') }

    fun ingest(environmentPath: String, providerPath: String, request: WebhookRequest): IngestResult {
        val environment = DeployEnvironment.fromPath(environmentPath)
        if (environment == null || environment !in config.enabledEnvironments) return IngestResult(404, "not found")
        val adapter = byPath[providerPath] ?: return IngestResult(404, "not found")
        val credentials = config.credentialsFor(adapter.id, environment)
        if (!credentials.has(adapter.requiredCredentials)) {
            audit(environment, adapter, "WEBHOOK_REJECTED", "Proveedor sin credenciales configuradas en ${environment.path}")
            return IngestResult(503, "provider not configured")
        }
        if (request.body.size > config.maxBodyBytes) {
            audit(environment, adapter, "WEBHOOK_REJECTED", "Cuerpo de ${request.body.size} bytes supera el límite")
            return IngestResult(413, "payload too large")
        }

        val bodyHash = Crypto.sha256Hex(request.body)
        val deliveryKey = adapter.deliveryId(request) ?: "sha256:$bodyHash"
        val headers = request.headers.filterKeys { it in STORED_HEADERS }

        val signature = adapter.verifySignature(request, credentials, config.signatureToleranceSeconds)
        if (signature is SignatureCheck.Invalid) {
            store.insertRaw(
                RawWebhookEvent(
                    id = newId(), provider = adapter.id, environment = environment, deliveryKey = "invalid:${newId()}",
                    receivedAt = request.receivedAt, headers = headers, body = null, bodySha256 = bodyHash,
                    signatureStatus = SignatureStatus.INVALID, processingStatus = ProcessingStatus.REJECTED_SIGNATURE,
                    detail = signature.reason,
                ),
            )
            store.recordCommunication(adapter.id, environment, request.receivedAt, null, "Firma inválida: ${signature.reason}")
            audit(environment, adapter, "SIGNATURE_INVALID", signature.reason)
            return IngestResult(401, "invalid signature")
        }
        val signatureStatus = if (signature is SignatureCheck.Valid) SignatureStatus.VALID else SignatureStatus.NOT_SIGNED

        // Protección contra repeticiones: una entrega ya procesada no se vuelve a procesar.
        val existing = store.rawByDeliveryKey(adapter.id, environment, deliveryKey)
        val raw: RawWebhookEvent
        if (existing != null) {
            when (existing.processingStatus) {
                ProcessingStatus.PROCESSED, ProcessingStatus.IGNORED, ProcessingStatus.FAILED -> {
                    audit(environment, adapter, "DELIVERY_REPEATED", "Entrega $deliveryKey ya atendida (${existing.processingStatus})")
                    return IngestResult(200, "already processed", existing.paymentEventId)
                }
                else -> {
                    store.updateRaw(existing.id, ProcessingStatus.RECEIVED, "Reintento del proveedor", null, incrementAttempts = true)
                    raw = existing
                }
            }
        } else {
            raw = RawWebhookEvent(
                id = newId(), provider = adapter.id, environment = environment, deliveryKey = deliveryKey,
                receivedAt = request.receivedAt, headers = headers, body = request.bodyText, bodySha256 = bodyHash,
                signatureStatus = signatureStatus, processingStatus = ProcessingStatus.RECEIVED, detail = null,
            )
            if (!store.insertRaw(raw)) return IngestResult(200, "already received")
        }

        val extraction = adapter.extract(request)
        val payment = when (extraction) {
            is Extraction.Ignored -> return finish(raw, environment, adapter, ProcessingStatus.IGNORED, extraction.reason, 200, "ignored")
            is Extraction.Malformed -> return finish(raw, environment, adapter, ProcessingStatus.FAILED, extraction.reason, 400, "malformed")
            is Extraction.Payment -> extraction
        }

        val resolved: ProviderPayment = try {
            retry.run { adapter.resolve(payment, request, credentials, environment) }
        } catch (e: ProviderUnavailableException) {
            store.updateRaw(raw.id, ProcessingStatus.RETRY_PENDING, e.message, null)
            store.recordCommunication(adapter.id, environment, clock.instant(), null, "API del proveedor: ${e.message}")
            audit(environment, adapter, "PROVIDER_UNAVAILABLE", "Pago ${payment.resourceId}: ${e.message}")
            // 5xx: el proveedor reenviará la notificación más tarde.
            return IngestResult(503, "retry later")
        } catch (e: ProviderRejectedException) {
            // Definitivo (credencial inválida, pago ajeno): 200 para que el proveedor no reenvíe sin fin.
            return finish(raw, environment, adapter, ProcessingStatus.FAILED, e.message ?: "rechazado", 200, "not processed")
        }

        if (signatureStatus == SignatureStatus.NOT_SIGNED && !resolved.fetchedFromApi) {
            return finish(raw, environment, adapter, ProcessingStatus.FAILED, "Aviso sin firma y sin consulta al proveedor", 400, "unverifiable")
        }
        val expectedLive = environment == DeployEnvironment.PRODUCTION
        if (resolved.liveMode != null && resolved.liveMode != expectedLive) {
            return finish(raw, environment, adapter, ProcessingStatus.FAILED, "El proveedor declara live_mode=${resolved.liveMode} en ${environment.path}", 409, "environment mismatch")
        }

        val eventId = savePayment(environment, adapter, resolved, signatureStatus == SignatureStatus.VALID, raw.id)
        store.updateRaw(raw.id, ProcessingStatus.PROCESSED, "Pago ${resolved.externalTransactionId}: ${resolved.providerStatus}", eventId)
        store.recordCommunication(adapter.id, environment, clock.instant(), clock.instant(), null)
        return IngestResult(200, "ok", eventId)
    }

    private fun savePayment(
        environment: DeployEnvironment,
        adapter: ProviderAdapter,
        p: ProviderPayment,
        signatureVerified: Boolean,
        rawId: String,
    ): String {
        val previous = store.paymentByExternalId(adapter.id, environment, p.externalTransactionId)
        if (previous != null && previous.event.verificationStatus in FINAL) {
            audit(environment, adapter, "PAYMENT_UNCHANGED", "${p.externalTransactionId} ya estaba ${previous.event.verificationStatus}")
            return previous.event.eventId
        }
        val now = clock.instant()
        val base = previous?.event ?: PaymentEvent(
            eventId = "wh_" + UUID.randomUUID().toString().replace("-", ""),
            sequence = 0,
            sourceChannel = SourceChannel.OFFICIAL_WEBHOOK,
            provider = adapter.id,
            source = p.source,
            externalTransactionId = p.externalTransactionId,
            eventType = EventType.INCOMING_TRANSFER,
            money = p.money,
            senderName = p.sender,
            recipient = p.recipient,
            eventTimestamp = p.occurredAt ?: now,
            detectedTimestamp = now,
            verificationStatus = VerificationStatus.DETECTED,
            announcementStatus = AnnouncementStatus.PENDING,
            rawEventReference = rawId,
        )
        val event = base.copy(money = p.money ?: base.money, rawEventReference = rawId)
        val updated = when (p.status) {
            ProviderPaymentStatus.APPROVED -> {
                val money = p.money
                if (money == null) {
                    event.copy(verificationStatus = VerificationStatus.PENDING_VERIFICATION)
                } else {
                    verification.confirm(
                        event,
                        ProviderConfirmation(adapter.id, p.externalTransactionId, money, p.providerStatus, signatureVerified, p.fetchedFromApi),
                    )
                }
            }
            ProviderPaymentStatus.PENDING -> event.copy(verificationStatus = VerificationStatus.PENDING_VERIFICATION)
            ProviderPaymentStatus.REJECTED -> event.copy(verificationStatus = VerificationStatus.REJECTED)
        }
        store.savePayment(environment, updated, p.providerStatus, rawId)
        audit(
            environment, adapter, "PAYMENT_${updated.verificationStatus}",
            "${updated.eventId} pago ${p.externalTransactionId} (${p.providerStatus}; firma=${signatureVerified}; api=${p.fetchedFromApi})",
        )
        return updated.eventId
    }

    private fun finish(
        raw: RawWebhookEvent,
        environment: DeployEnvironment,
        adapter: ProviderAdapter,
        status: ProcessingStatus,
        detail: String,
        http: Int,
        message: String,
    ): IngestResult {
        store.updateRaw(raw.id, status, detail, null)
        store.recordCommunication(adapter.id, environment, clock.instant(), null, if (status == ProcessingStatus.FAILED) detail else null)
        audit(environment, adapter, "WEBHOOK_$status", detail)
        return IngestResult(http, message)
    }

    private fun audit(environment: DeployEnvironment?, adapter: ProviderAdapter?, action: String, detail: String) =
        store.audit(AuditEntry(clock.instant(), environment, adapter?.id, action, detail))

    private fun newId() = UUID.randomUUID().toString()

    companion object {
        /** Cabeceras que se conservan del original (nunca Authorization, cookies ni IP). */
        val STORED_HEADERS = setOf("content-type", "user-agent", "x-request-id", "x-signature")
        private val FINAL = setOf(VerificationStatus.PROVIDER_CONFIRMED, VerificationStatus.REJECTED)
    }
}
