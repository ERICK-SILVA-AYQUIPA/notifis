package pe.pagobox.app.system

import android.content.Context
import android.os.PowerManager

/**
 * Mantiene la CPU despierta (sin encender la pantalla) mientras se procesa una notificación o se
 * entrega un anuncio. Con la pantalla apagada, Android puede suspender la CPU en cuanto termina el
 * callback del lector; sin esto, el trabajo en corrutinas podría quedar a medias hasta que el
 * celular despierte. Siempre con tiempo máximo: nunca queda retenido si algo falla.
 *
 * Efecto real sujeto a pruebas por fabricante (ver docs/guia-pruebas-fisicas.md).
 */
class ShortWakeLock(context: Context) {
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    suspend fun <T> hold(tag: String, timeoutMs: Long, block: suspend () -> T): T {
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pagobox:$tag").apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
        try {
            return block()
        } finally {
            if (lock.isHeld) lock.release()
        }
    }
}
