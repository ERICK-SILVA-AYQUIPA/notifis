package pe.pagobox.engine.source.banks

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.source.BankNotificationAdapter
import pe.pagobox.engine.source.NotificationFormat

/**
 * Adaptador de YAPE.
 *
 * Configurado con el paquete oficial de Yape y patrones de notificación para
 * detección y anuncio de cobros recibidos y descarte de egresos.
 */
object YapeAdapter : BankNotificationAdapter(
    sourceId = SourceId.YAPE,
    officialPackages = PACKAGES,
    formats = FORMATS,
    declaredStatus = STATUS,
)

private val PACKAGES: Set<String> = setOf(
    "com.bcp.innovacxion.yapeapp",
)

private val FORMATS: List<NotificationFormat> = listOf(
    NotificationFormat(
        id = "yape-ingreso-te-envio",
        eventType = EventType.INCOMING_TRANSFER,
        pattern = Regex("""(?:(?<remitente>[^\n]+?)\s+te\s+(?:envió|envio|yapeó|yapeo)|¡?Te\s+(?:yapearon|enviaron)!?)\s*(?:un\s+pago\s+de\s+)?(?<monto>(?:S/\.?|PEN)\s*[\d,.]+)""", RegexOption.IGNORE_CASE),
        anonymizedSample = "¡Te yapearon! \n <NOMBRE> te envió S/ 99.99",
        expectedAmount = "99.99",
        provenance = "Notificación estándar de Yape (Android)",
    ),
    NotificationFormat(
        id = "yape-ingreso-confirmacion",
        eventType = EventType.INCOMING_TRANSFER,
        pattern = Regex("""(?:Confirmación:\s*)?Te\s+acaban\s+de\s+yapear\s+(?<monto>(?:S/\.?|PEN)\s*[\d,.]+)(?:\s+de\s+parte\s+de\s+(?<remitente>[^\n]+))?""", RegexOption.IGNORE_CASE),
        anonymizedSample = "Confirmación: Te acaban de yapear S/ 99.99",
        expectedAmount = "99.99",
        provenance = "Notificación de confirmación Yape",
    ),
    NotificationFormat(
        id = "yape-egreso",
        eventType = EventType.OUTGOING_TRANSFER,
        pattern = Regex("""(?:Enviaste|Yapeaste|Pagaste)\s+(?<monto>(?:S/\.?|PEN)\s*[\d,.]+)\s+a\s+(?<remitente>[^\n]+)""", RegexOption.IGNORE_CASE),
        anonymizedSample = "Yapeaste S/ 99.99 a <NOMBRE>",
        expectedAmount = "99.99",
        provenance = "Egreso Yape (descarte)",
    ),
)

private val STATUS = CompatibilityStatus.CONFIGURED

