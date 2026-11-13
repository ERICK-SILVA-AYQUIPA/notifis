package pe.pagobox.engine.source

/**
 * Señal genérica de dinero en el texto de una notificación: un símbolo de moneda peruano o dólar
 * seguido de un número («S/ 25», «S/.1,250.50», «PEN 20», «US$ 15»).
 *
 * Sirve solo para **registrar** apps que podrían ser financieras y aún no están asociadas a ninguna
 * entidad. No es un formato de ninguna entidad, no crea eventos ni anuncia nada.
 * Un número sin símbolo de moneda no cuenta (evita registrar apps por horas, códigos o teléfonos).
 */
object MonetarySignal {
    private val pattern = Regex(
        """(?<![A-Za-z])(?:S/\.?|PEN|US\$|USD)\s?\d[\d,]*(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE,
    )

    fun containsAmount(text: String?): Boolean = !text.isNullOrBlank() && pattern.containsMatchIn(text)

    fun containsAmount(notification: RawNotification): Boolean = containsAmount(notification.fullText)
}
