package pe.pagobox.engine

import pe.pagobox.domain.model.Currency
import pe.pagobox.engine.parser.AmountParser
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountParserTest {
    private fun pen(text: String) = AmountParser.parse(text)!!.also { assertEquals(Currency.PEN, it.currency) }.amount

    @Test
    fun `formatos peruanos en soles`() {
        assertEquals(BigDecimal("20.00"), pen("S/ 20"))
        assertEquals(BigDecimal("20.00"), pen("S/20.00"))
        assertEquals(BigDecimal("35.50"), pen("S/. 35.50"))
        assertEquals(BigDecimal("7.50"), pen("s/ 7.5"))
        assertEquals(BigDecimal("1250.00"), pen("S/ 1,250"))
        assertEquals(BigDecimal("1234567.89"), pen("S/ 1,234,567.89"))
        assertEquals(BigDecimal("15.00"), pen("15"))
        assertEquals(BigDecimal("20.00"), pen("PEN 20.00"))
    }

    @Test
    fun `dolares`() {
        val usd = AmountParser.parse("US$ 15.00")!!
        assertEquals(Currency.USD, usd.currency)
        assertEquals(BigDecimal("15.00"), usd.amount)
    }

    @Test
    fun `textos ambiguos o invalidos se rechazan`() {
        assertNull(AmountParser.parse("S/ 1,25"))
        assertNull(AmountParser.parse("S/ 12.345"))
        assertNull(AmountParser.parse("veinte soles"))
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("S/ -5"))
    }
}
