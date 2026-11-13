package pe.pagobox.engine.privacy

import pe.pagobox.engine.classifier.PaymentClassifier

/**
 * Anonimiza textos de notificaciones para el diagnóstico y el registro de formatos desconocidos.
 *
 * Reglas (privacidad primero; se prefiere ocultar de más):
 * 1. Si el texto parece contener un código de seguridad (OTP, PIN, clave, token), se omite completo.
 * 2. Correos electrónicos -> <correo>.
 * 3. Importes: se conserva el formato y se reemplaza cada dígito por 9 ("S/ 1,250.50" -> "S/ 9,999.99"),
 *    para poder escribir reglas sin revelar montos reales.
 * 4. Cualquier otro dígito (teléfonos, cuentas, operaciones, horas) -> #.
 * 5. Palabras con mayúscula inicial o en mayúsculas que no estén en la lista de términos
 *    comunes -> <NOMBRE>. Así se ocultan remitentes y titulares.
 */
object NotificationAnonymizer {
    const val SECURITY_REDACTED = "[OMITIDO: posible código de seguridad]"

    private val securityTerms = listOf(
        "otp", "codigo de verificacion", "codigo de seguridad", "codigo de validacion", "tu codigo", "clave",
        "contrasena", "password", "pin", "token", "no compartas", "no lo compartas", "digital key", "clave dinamica",
    )
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val amount = Regex("""(?i)(S/\.?|PEN|US\$|USD|\$)\s?\d[\d,]*(\.\d{1,2})?""")
    private val digit = Regex("""\d""")
    private val word = Regex("""\p{L}[\p{L}'’.-]*""")

    /** Términos que pueden aparecer con mayúscula y no son datos personales. */
    private val allowed: Set<String> = setOf(
        // entidades y productos
        "yape", "plin", "bcp", "interbank", "bbva", "scotiabank", "banco", "nacion", "de", "la", "del", "credito",
        "peru", "continental", "agora", "tunki", "bim", "visa", "mastercard", "app", "s", "pen", "usd", "us",
        // palabras frecuentes al inicio de frase o en títulos
        "te", "tu", "tus", "has", "hemos", "recibiste", "recibio", "recibido", "recibida", "enviaste", "envio", "envia",
        "yapeo", "yapearon", "yapeaste", "pago", "pagos", "pagaste", "transferencia", "transferencias", "transferiste",
        "deposito", "abono", "cargo", "retiro", "compra", "consumo", "operacion", "operaciones", "exitosa", "exitoso",
        "nuevo", "nueva", "ingreso", "egreso", "solicitud", "solicita", "cobro", "monto", "importe", "soles", "dolares",
        "cuenta", "tarjeta", "ahorros", "corriente", "aviso", "alerta", "notificacion", "mensaje", "hola", "listo",
        "confirmado", "confirmada", "realizado", "realizada", "fecha", "hora", "por", "en", "a", "el", "los", "las",
        "un", "una", "desde", "para", "con", "y", "o", "hoy", "ayer", "ver", "detalle", "detalles", "movimiento",
        "movimientos", "saldo", "disponible", "celular", "numero", "nro", "codigo", "constancia", "pendiente",
        "rechazada", "rechazado", "fallida", "fallido", "dinero", "recibir", "enviar", "pagar", "cobrar", "negocio",
        "comercio", "qr", "id", "ok", "info", "importante", "atencion",
    )

    fun anonymize(text: String?): String? {
        if (text.isNullOrBlank()) return text
        val normalized = PaymentClassifier.normalize(text)
        if (securityTerms.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(normalized) }) return SECURITY_REDACTED

        val placeholders = mutableListOf<String>()
        fun protect(s: String): String {
            placeholders += s
            // Marcador con caracteres de uso privado: no son letras ni dígitos, así no los alteran los pasos siguientes.
            return "\uE000" + (0xE001 + placeholders.size - 1).toChar() + "\uE000"
        }

        var out = email.replace(text) { protect("<correo>") }
        out = amount.replace(out) { m -> protect(m.value.replace(digit, "9")) }
        out = digit.replace(out, "#")
        out = word.replace(out) { m ->
            val w = m.value
            val key = PaymentClassifier.normalize(w.trimEnd('.', '-'))
            val capitalized = w.first().isUpperCase()
            if (capitalized && key !in allowed) "<NOMBRE>" else w
        }
        out = out.replace(Regex("<NOMBRE>(\\s+<NOMBRE>)+"), "<NOMBRE>")
        return Regex("\uE000(.)\uE000").replace(out) { placeholders[it.groupValues[1][0].code - 0xE001] }
    }

    /** true si el texto parece contener un código de seguridad (no debe mostrarse ni guardarse). */
    fun looksSensitive(text: String?): Boolean = anonymize(text) == SECURITY_REDACTED
}
