package pe.pagobox.engine.source

import pe.pagobox.domain.model.EventType

/**
 * Formato de notificación de una entidad. Solo se agrega a partir de una muestra REAL
 * recopilada con el diagnóstico de notificaciones y anonimizada.
 *
 * El patrón puede usar los grupos con nombre `monto` y `remitente`.
 * Cada formato se prueba automáticamente contra su propia muestra (BankAdaptersTest).
 */
data class NotificationFormat(
    val id: String,
    val eventType: EventType,
    val pattern: Regex,
    /** Muestra anonimizada que originó el formato (título + texto, como lo arma RawNotification.fullText). */
    val anonymizedSample: String,
    /** Resultado esperado al procesar la muestra. */
    val expectedAmount: String?,
    /** Origen de la muestra: fecha, modelo, versión de la app (texto libre, obligatorio). */
    val provenance: String,
    /** true solo en pruebas: formato ficticio que nunca debe estar en un adaptador real. */
    val synthetic: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Formato sin id" }
        require(anonymizedSample.isNotBlank()) { "El formato $id no tiene muestra: no se inventan formatos" }
        require(provenance.isNotBlank()) { "El formato $id no documenta su origen" }
    }
}
