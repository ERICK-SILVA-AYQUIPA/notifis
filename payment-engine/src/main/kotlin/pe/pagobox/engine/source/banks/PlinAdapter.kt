package pe.pagobox.engine.source.banks

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.source.BankNotificationAdapter
import pe.pagobox.engine.source.NotificationFormat

/**
 * Adaptador de PLIN.
 *
 * Configurado con los paquetes bancarios que soportan Plin (Interbank, BBVA, Scotiabank)
 * y patrones de notificación para ingresos y egresos.
 */
object PlinAdapter : BankNotificationAdapter(
    sourceId = SourceId.PLIN,
    officialPackages = PACKAGES,
    formats = FORMATS,
    declaredStatus = STATUS,
)

private val PACKAGES: Set<String> = setOf(
    "pe.interbank.mobile.android",
    "com.bbva.nxt_peru",
    "pe.com.scotiabank.peru.android",
)

private val FORMATS: List<NotificationFormat> = listOf(
    NotificationFormat(
        id = "plin-ingreso-te-plinio",
        eventType = EventType.INCOMING_TRANSFER,
        pattern = Regex("""^(?:(?<remitente>[^\n]+?)\s+te\s+(?:plinió|plinio|plineó|plineo|envió|envio)|¡?Te\s+(?:plinaron|pliniaron|plinearon)!?)\s*(?:S/\.?|PEN)?\s*(?<monto>(?:S/\.?|PEN)\s*[\d,.]+)""", RegexOption.IGNORE_CASE),
        anonymizedSample = "<NOMBRE> te plinió S/ 99.99",
        expectedAmount = "99.99",
        provenance = "Notificación estándar de Plin (Android)",
    ),
    NotificationFormat(
        id = "plin-egreso",
        eventType = EventType.OUTGOING_TRANSFER,
        pattern = Regex("""^(?:Enviaste|Pliniaste|Plineaste)\s+(?<monto>(?:S/\.?|PEN)\s*[\d,.]+)\s+a\s+(?<remitente>[^\n]+)""", RegexOption.IGNORE_CASE),
        anonymizedSample = "Pliniaste S/ 99.99 a <NOMBRE>",
        expectedAmount = "99.99",
        provenance = "Egreso Plin (descarte)",
    ),
)

private val STATUS = CompatibilityStatus.CONFIGURED

