package pe.pagobox.engine.parser

import pe.pagobox.domain.model.Currency
import pe.pagobox.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Interpreta importes en formatos usados en Perú:
 * "S/ 20", "S/. 1,250.50", "S/35.00", "s/ 7.5", "PEN 20.00", "US$ 15.00", "USD 15", "$ 10".
 * Separador de miles: coma. Separador decimal: punto.
 * Devuelve null si el texto no es un importe inequívoco.
 */
object AmountParser {
    private val pattern = Regex(
        """^\s*(S/\.?|PEN|US\$|USD|\$)?\s*(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): Money? {
        val match = pattern.matchEntire(text) ?: return null
        val symbol = match.groupValues[1].uppercase()
        val integerPart = match.groupValues[2].replace(",", "")
        val decimalPart = match.groupValues[3]
        val currency = when (symbol) {
            "US$", "USD", "$" -> Currency.USD
            else -> Currency.PEN
        }
        val value = BigDecimal(if (decimalPart.isEmpty()) integerPart else "$integerPart.$decimalPart")
            .setScale(2, RoundingMode.UNNECESSARY)
        return Money(value, currency)
    }
}
