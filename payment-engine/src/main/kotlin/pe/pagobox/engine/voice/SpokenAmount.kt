package pe.pagobox.engine.voice

import pe.pagobox.domain.model.Currency
import pe.pagobox.domain.model.Money
import java.math.RoundingMode

/**
 * Convierte un importe en texto que el TTS pronuncia correctamente en español:
 * 20.00 -> "20 soles", 1.00 -> "1 sol", 35.50 -> "35 soles con 50 céntimos".
 * Los números se dejan en cifras: los motores TTS en español los leen bien.
 */
object SpokenAmount {
    fun of(money: Money): String {
        val value = money.amount.setScale(2, RoundingMode.UNNECESSARY)
        val units = value.toBigInteger()
        val cents = value.subtract(units.toBigDecimal()).movePointRight(2).toInt()
        val (singular, plural, centWord) = when (money.currency) {
            Currency.PEN -> Triple("sol", "soles", "céntimos")
            Currency.USD -> Triple("dólar", "dólares", "centavos")
        }
        val unitWord = if (units == java.math.BigInteger.ONE) singular else plural
        val main = "$units $unitWord"
        return if (cents == 0) main else "$main con $cents $centWord"
    }

    /** Importe numérico para mostrar en pantalla: "1,250.50". */
    fun display(money: Money): String = "%,.2f".format(java.util.Locale.US, money.amount)
}
