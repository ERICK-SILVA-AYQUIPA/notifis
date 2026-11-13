package pe.pagobox.engine.source.banks

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.source.BankNotificationAdapter
import pe.pagobox.engine.source.NotificationFormat

/**
 * Adaptador de SCOTIABANK.
 *
 * Estado: sin muestras reales. No se declaran paquetes ni formatos supuestos.
 * Para incorporar un formato:
 *  1. Capturar la notificación con «Diagnóstico de notificaciones» en un celular real.
 *  2. Exportar la muestra anonimizada y agregarla a [FORMATS] con su origen.
 *  3. Agregar el paquete oficial confirmado a [PACKAGES] (o asignarlo desde la app).
 *  4. Subir [STATUS] solo con la evidencia que exige docs/compatibilidad-bancaria.md.
 */
object ScotiabankAdapter : BankNotificationAdapter(
    sourceId = SourceId.SCOTIABANK,
    officialPackages = PACKAGES,
    formats = FORMATS,
    declaredStatus = STATUS,
)

private val PACKAGES: Set<String> = emptySet()

private val FORMATS: List<NotificationFormat> = emptyList()

private val STATUS = CompatibilityStatus.WAITING_FOR_SAMPLE
