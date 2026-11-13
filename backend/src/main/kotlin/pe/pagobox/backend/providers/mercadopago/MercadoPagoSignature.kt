package pe.pagobox.backend.providers.mercadopago

import pe.pagobox.backend.providers.SignatureCheck
import pe.pagobox.backend.security.Crypto
import java.time.Instant

/**
 * Validación de la cabecera `x-signature` de Mercado Pago, igual que el validador oficial de sus SDK
 * (`mercadopago/sdk-nodejs` src/utils/webhook, `mercadopago/sdk-python` mercadopago/webhook/validator.py):
 *
 * - Cabecera: `ts=<segundos>,v1=<hex>` (claves desconocidas se ignoran).
 * - Manifiesto: `id:<data.id>;request-id:<x-request-id>;ts:<ts>;` omitiendo cada par sin valor.
 *   `data.id` se usa tal como llega en la query (sin cambiar mayúsculas).
 * - Firma: HMAC-SHA256(clave secreta del webhook, manifiesto) en hexadecimal; comparación en tiempo constante.
 * - Tolerancia de tiempo opcional contra repeticiones.
 */
object MercadoPagoSignature {
    fun manifest(dataId: String?, requestId: String?, ts: String): String = buildString {
        dataId?.takeIf { it.isNotBlank() }?.let { append("id:").append(it).append(';') }
        requestId?.takeIf { it.isNotBlank() }?.let { append("request-id:").append(it).append(';') }
        append("ts:").append(ts).append(';')
    }

    fun verify(
        xSignature: String?,
        xRequestId: String?,
        dataId: String?,
        secret: String,
        now: Instant,
        toleranceSeconds: Long?,
    ): SignatureCheck {
        if (xSignature.isNullOrBlank()) return SignatureCheck.Invalid("Falta la cabecera x-signature")
        var ts: String? = null
        var v1: String? = null
        for (part in xSignature.split(',')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = part.substring(0, eq).trim().lowercase()
            val value = part.substring(eq + 1).trim()
            when (key) {
                "ts" -> ts = value
                "v1" -> v1 = value
            }
        }
        if (ts == null || !ts.all { it.isDigit() }) return SignatureCheck.Invalid("x-signature sin ts válido")
        if (v1.isNullOrBlank()) return SignatureCheck.Invalid("x-signature sin v1")
        val expected = Crypto.hmacSha256Hex(secret, manifest(dataId?.trim(), xRequestId?.trim(), ts))
        if (!Crypto.constantTimeEquals(expected, v1.lowercase())) return SignatureCheck.Invalid("La firma no coincide")
        if (toleranceSeconds != null) {
            val drift = kotlin.math.abs(now.epochSecond - ts.toLong())
            if (drift > toleranceSeconds) return SignatureCheck.Invalid("Marca de tiempo fuera de tolerancia (${drift}s)")
        }
        return SignatureCheck.Valid
    }
}
