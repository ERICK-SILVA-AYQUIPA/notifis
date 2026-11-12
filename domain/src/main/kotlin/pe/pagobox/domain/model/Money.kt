package pe.pagobox.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

enum class Currency(val code: String) { PEN("PEN"), USD("USD") }

/** Importe monetario exacto (nunca Double) con dos decimales. */
data class Money(val amount: BigDecimal, val currency: Currency) {
    init {
        require(amount.signum() >= 0) { "El importe no puede ser negativo" }
    }

    /** Importe normalizado a 2 decimales, útil para comparar y deduplicar. */
    val normalized: BigDecimal get() = amount.setScale(2, RoundingMode.UNNECESSARY)

    companion object {
        fun pen(value: String) = Money(BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY), Currency.PEN)
    }
}
