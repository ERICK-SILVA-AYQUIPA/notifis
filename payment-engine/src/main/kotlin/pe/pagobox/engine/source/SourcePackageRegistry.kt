package pe.pagobox.engine.source

import pe.pagobox.domain.model.CompatibilityStatus

/**
 * Registro de paquetes Android -> adaptador. Las notificaciones de paquetes no
 * registrados se descartan. Los adaptadores NOT_IMPLEMENTED no se usan.
 */
class SourcePackageRegistry(adapters: List<PaymentSourceAdapter>) {
    private val byPackage: Map<String, PaymentSourceAdapter>

    init {
        val map = mutableMapOf<String, PaymentSourceAdapter>()
        for (adapter in adapters) {
            if (adapter.compatibility == CompatibilityStatus.NOT_IMPLEMENTED) continue
            for (pkg in adapter.packageNames) {
                require(pkg !in map) { "Paquete registrado dos veces: $pkg" }
                map[pkg] = adapter
            }
        }
        byPackage = map
    }

    val supervisedPackages: Set<String> get() = byPackage.keys

    fun adapterFor(packageName: String): PaymentSourceAdapter? = byPackage[packageName]
}
