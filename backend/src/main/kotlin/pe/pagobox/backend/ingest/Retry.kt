package pe.pagobox.backend.ingest

import pe.pagobox.backend.providers.ProviderUnavailableException

/** Reintentos con espera exponencial para llamadas salientes al proveedor. */
class Retry(
    private val attempts: Int = 3,
    private val initialDelayMs: Long = 300,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    fun <T> run(block: () -> T): T {
        var delay = initialDelayMs
        var last: ProviderUnavailableException? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: ProviderUnavailableException) {
                last = e
                if (attempt < attempts - 1) {
                    sleep(delay)
                    delay *= 2
                }
            }
        }
        throw last!!
    }
}
