package pe.pagobox.engine

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.compatibility.CompatibilityRegistry
import pe.pagobox.engine.parser.AmountParser
import pe.pagobox.engine.source.KnownSources
import pe.pagobox.engine.source.ParseResult
import pe.pagobox.engine.source.RawNotification
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pruebas de los adaptadores reales. Se ejecutan sobre todos los formatos registrados:
 * cuando se agregue un formato real con su muestra anonimizada, queda probado automáticamente.
 */
class BankAdaptersTest {
    private val adapters = KnownSources.all()

    @Test
    fun `hay un adaptador independiente por cada entidad de la especificacion`() {
        val expected = setOf(
            SourceId.YAPE, SourceId.PLIN, SourceId.BCP, SourceId.INTERBANK,
            SourceId.BBVA, SourceId.SCOTIABANK, SourceId.BANCO_NACION,
        )
        assertEquals(expected, adapters.map { it.sourceId }.toSet())
        assertEquals(adapters.size, adapters.map { it::class }.toSet().size, "Cada entidad debe tener su propia clase")
    }

    @Test
    fun `el estado declarado de cada entidad esta respaldado por evidencia`() {
        adapters.forEach { CompatibilityRegistry.validate(it) }
    }

    @Test
    fun `ninguna entidad sin formatos reales se declara configurada`() {
        adapters.filter { it.formats.isEmpty() }.forEach {
            assertTrue(!it.declaredStatus.processesPayments, "${it.sourceId} procesa pagos sin formatos")
        }
    }

    @Test
    fun `cada formato reconoce su propia muestra con el resultado esperado`() {
        for (adapter in adapters) {
            for (format in adapter.formats) {
                val raw = RawNotification(packageName = "x", title = null, text = format.anonymizedSample, postedAt = Instant.EPOCH)
                val parsed = assertIs<ParseResult.Parsed>(adapter.parse(raw), "${adapter.sourceId}/${format.id}").payment
                assertEquals(format.id, parsed.formatId, "La muestra de ${format.id} la reconoce otro formato")
                assertEquals(format.eventType, parsed.eventType)
                val expected = format.expectedAmount?.let { AmountParser.parse(it)?.amount }
                assertEquals(expected, parsed.money?.amount, "Importe de ${format.id}")
            }
        }
    }

    @Test
    fun `los formatos de ingreso no reconocen muestras de egreso de la misma entidad`() {
        for (adapter in adapters) {
            val incoming = adapter.formats.filter { it.eventType == EventType.INCOMING_TRANSFER }
            val others = adapter.formats.filter { it.eventType != EventType.INCOMING_TRANSFER }
            for (o in others) {
                val raw = RawNotification(packageName = "x", title = null, text = o.anonymizedSample, postedAt = Instant.EPOCH)
                val parsed = adapter.parse(raw)
                if (parsed is ParseResult.Parsed && parsed.payment.eventType == EventType.INCOMING_TRANSFER) {
                    fail("${o.id} se clasifica como ingreso con ${parsed.payment.formatId} (${incoming.size} formatos de ingreso)")
                }
            }
        }
    }

    @Test
    fun `entidad sin formatos devuelve formato desconocido para registrar la muestra`() {
        for (adapter in adapters.filter { it.formats.isEmpty() }) {
            val raw = RawNotification(packageName = "x", title = "t", text = "texto", postedAt = Instant.EPOCH)
            assertIs<ParseResult.UnknownFormat>(adapter.parse(raw))
            assertEquals(CompatibilityStatus.WAITING_FOR_SAMPLE, adapter.declaredStatus)
        }
    }
}
