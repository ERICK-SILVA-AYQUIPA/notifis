package pe.pagobox.engine.source

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.compatibility.CompatibilityRegistry
import pe.pagobox.engine.compatibility.RuntimeOverride

/**
 * Registro de paquetes Android -> entidad.
 * Combina los paquetes oficiales declarados en código con los asignados en el celular
 * desde el diagnóstico de notificaciones. Solo devuelve adaptador para procesar pagos
 * cuando el estado efectivo de la entidad lo permite.
 */
class SourcePackageRegistry(
    adapters: List<PaymentSourceAdapter>,
    assignedPackages: Map<String, SourceId> = emptyMap(),
    overrides: Map<SourceId, RuntimeOverride> = emptyMap(),
) {
    private val bySource = adapters.associateBy { it.sourceId }
    private val sourceByPackage: Map<String, SourceId>
    val statuses: Map<SourceId, CompatibilityStatus>

    init {
        val map = mutableMapOf<String, SourceId>()
        for (adapter in adapters) {
            for (pkg in adapter.officialPackages) {
                require(pkg !in map) { "Paquete registrado dos veces: $pkg" }
                map[pkg] = adapter.sourceId
            }
        }
        for ((pkg, source) in assignedPackages) {
            if (pkg !in map && source in bySource) map[pkg] = source
        }
        sourceByPackage = map
        val withPackage = map.values.toSet()
        statuses = adapters.associate {
            it.sourceId to CompatibilityRegistry.effectiveStatus(it, it.sourceId in withPackage, overrides[it.sourceId] ?: RuntimeOverride.NONE)
        }
    }

    /** Entidad a la que pertenece el paquete (en cualquier estado), o null. */
    fun sourceFor(packageName: String): SourceId? = sourceByPackage[packageName]

    /** Adaptador para procesar pagos, solo si el estado efectivo lo permite. */
    fun adapterFor(packageName: String): PaymentSourceAdapter? {
        val source = sourceByPackage[packageName] ?: return null
        return bySource[source]?.takeIf { statuses[source]?.processesPayments == true }
    }

    /** Paquetes cuyas notificaciones se anuncian. */
    val supervisedPackages: Set<String>
        get() = sourceByPackage.filterValues { statuses[it]?.processesPayments == true }.keys

    /** Paquetes asociados a una entidad (incluye los que solo recopilan muestras). */
    val knownPackages: Set<String> get() = sourceByPackage.keys

    fun packagesOf(source: SourceId): Set<String> = sourceByPackage.filterValues { it == source }.keys
}
