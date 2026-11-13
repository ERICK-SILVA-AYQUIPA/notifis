package pe.pagobox.engine

import pe.pagobox.engine.source.MonetarySignal
import pe.pagobox.engine.source.RawNotification
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Textos genéricos escritos para la prueba: no son formatos de ninguna entidad. */
class MonetarySignalTest {
    @Test
    fun `reconoce importes con simbolo de moneda`() {
        listOf("Total S/ 25", "S/.1,250.50 abonado", "monto PEN 20.00", "US$ 15", "usd 7.5", "s/9").forEach {
            assertTrue(MonetarySignal.containsAmount(it), it)
        }
    }

    @Test
    fun `ignora numeros sin moneda, horas, telefonos y codigos`() {
        listOf("Reunión a las 15:30", "Llama al 987654321", "Tu código es 123456", "Tienes 3 mensajes", "", null).forEach {
            assertFalse(MonetarySignal.containsAmount(it), it.toString())
        }
    }

    @Test
    fun `busca en todos los campos de la notificacion`() {
        val raw = RawNotification(
            packageName = "app.de.prueba",
            title = "Aviso",
            text = "Sin importe aquí",
            textLines = listOf("línea 1", "Movimiento S/ 40.00"),
            postedAt = Instant.EPOCH,
        )
        assertTrue(MonetarySignal.containsAmount(raw))
    }
}
