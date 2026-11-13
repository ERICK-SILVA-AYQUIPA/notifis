package pe.pagobox.engine.compatibility

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.source.PaymentSourceAdapter

enum class EvidenceKind { LAB, DEVICE, FULL_PROTOCOL }

/** Evidencia documentada de una prueba (sin datos personales). */
data class TestEvidence(
    val kind: EvidenceKind,
    val date: String,
    val deviceModel: String?,
    val androidVersion: String?,
    val bankAppVersion: String?,
    val passed: Boolean,
    /** Ruta o enlace del informe (docs/pruebas/...). */
    val reportRef: String,
)

/** Cambios de estado en tiempo de ejecución, guardados por la app. */
enum class RuntimeOverride { NONE, FORMAT_CHANGED, TEMPORARILY_UNAVAILABLE }

/**
 * Registro individual de compatibilidad por entidad y reglas para avanzar de estado.
 * Una entidad solo puede quedar en VERIFIED_COMPATIBLE con evidencia real del protocolo completo.
 */
object CompatibilityRegistry {
    /** Evidencia documentada por entidad. Vacía hasta realizar pruebas reales. */
    val evidence: Map<SourceId, List<TestEvidence>> = emptyMap()

    /**
     * Comprueba que el estado declarado en código esté respaldado. Lanza error si no:
     * lo ejecutan las pruebas automáticas, así un estado inflado no llega a compilarse en CI.
     */
    fun validate(adapter: PaymentSourceAdapter, evidence: List<TestEvidence> = this.evidence[adapter.sourceId].orEmpty()) {
        val name = adapter.sourceId.displayName
        val passed = evidence.filter { it.passed }
        val realFormats = adapter.formats.filterNot { it.synthetic }
        when (adapter.declaredStatus) {
            CompatibilityStatus.NOT_CONFIGURED, CompatibilityStatus.WAITING_FOR_SAMPLE,
            CompatibilityStatus.TEMPORARILY_UNAVAILABLE -> Unit
            CompatibilityStatus.CONFIGURED, CompatibilityStatus.FORMAT_CHANGED ->
                require(realFormats.isNotEmpty()) { "$name: ${adapter.declaredStatus} requiere formatos reales" }
            CompatibilityStatus.LAB_TESTED ->
                require(realFormats.isNotEmpty() && passed.any { it.kind == EvidenceKind.LAB }) {
                    "$name: LAB_TESTED requiere formatos reales y evidencia de laboratorio"
                }
            CompatibilityStatus.DEVICE_TESTED ->
                require(realFormats.isNotEmpty() && passed.any { it.kind == EvidenceKind.DEVICE && it.deviceModel != null }) {
                    "$name: DEVICE_TESTED requiere evidencia en un celular físico identificado"
                }
            CompatibilityStatus.VERIFIED_COMPATIBLE ->
                require(
                    realFormats.isNotEmpty() &&
                        passed.any { it.kind == EvidenceKind.DEVICE && it.deviceModel != null } &&
                        passed.any { it.kind == EvidenceKind.FULL_PROTOCOL },
                ) { "$name: VERIFIED_COMPATIBLE requiere pruebas reales documentadas del protocolo completo" }
        }
        require(adapter.formats.none { it.synthetic }) { "$name contiene formatos sintéticos" }
    }

    /**
     * Estado efectivo en un celular concreto:
     * - Sin paquete conocido (en código o asignado) -> NOT_CONFIGURED.
     * - Override manual o automático -> FORMAT_CHANGED / TEMPORARILY_UNAVAILABLE.
     * - Si no, el estado declarado.
     */
    fun effectiveStatus(adapter: PaymentSourceAdapter, hasPackage: Boolean, override: RuntimeOverride): CompatibilityStatus {
        if (!hasPackage) return CompatibilityStatus.NOT_CONFIGURED
        return when (override) {
            RuntimeOverride.TEMPORARILY_UNAVAILABLE -> CompatibilityStatus.TEMPORARILY_UNAVAILABLE
            RuntimeOverride.FORMAT_CHANGED ->
                if (adapter.declaredStatus.processesPayments) CompatibilityStatus.FORMAT_CHANGED else adapter.declaredStatus
            RuntimeOverride.NONE -> adapter.declaredStatus
        }
    }

    /** Cantidad de formatos desconocidos seguidos que dispara FORMAT_CHANGED en una entidad configurada. */
    const val UNKNOWN_FORMAT_THRESHOLD = 3
}
