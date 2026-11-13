package pe.pagobox.engine.source

import pe.pagobox.engine.source.banks.BancoNacionAdapter
import pe.pagobox.engine.source.banks.BbvaAdapter
import pe.pagobox.engine.source.banks.BcpAdapter
import pe.pagobox.engine.source.banks.InterbankAdapter
import pe.pagobox.engine.source.banks.PlinAdapter
import pe.pagobox.engine.source.banks.ScotiabankAdapter
import pe.pagobox.engine.source.banks.YapeAdapter

/** Adaptadores independientes por entidad (fase 3). */
object KnownSources {
    fun all(): List<PaymentSourceAdapter> = listOf(
        YapeAdapter,
        PlinAdapter,
        BcpAdapter,
        InterbankAdapter,
        BbvaAdapter,
        ScotiabankAdapter,
        BancoNacionAdapter,
    )
}
