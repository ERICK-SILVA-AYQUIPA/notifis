package pe.pagobox.app.payment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.pagobox.app.data.local.PackageAssignmentDao
import pe.pagobox.app.data.local.PackageAssignmentEntity
import pe.pagobox.app.data.local.SourceStatusDao
import pe.pagobox.app.data.local.SourceStatusEntity
import pe.pagobox.app.data.preferences.SettingsRepository
import pe.pagobox.app.data.repositories.DiagnosticsLogger
import pe.pagobox.app.data.repositories.PaymentRepository
import pe.pagobox.app.diagnostics.CapturedNotification
import pe.pagobox.app.diagnostics.NotificationDiagnostics
import pe.pagobox.app.diagnostics.PackageInspector
import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.PaymentPipeline
import pe.pagobox.engine.PipelineResult
import pe.pagobox.engine.compatibility.CompatibilityRegistry
import pe.pagobox.engine.compatibility.RuntimeOverride
import pe.pagobox.engine.deduplication.DeduplicationEngine
import pe.pagobox.engine.source.KnownSources
import pe.pagobox.engine.source.PaymentSourceAdapter
import pe.pagobox.engine.source.SourcePackageRegistry

/**
 * Orquesta el motor de pagos dentro de la app:
 * - Construye el registro de paquetes con los paquetes oficiales en código, los asignados desde el
 *   diagnóstico (solo si la firma del paquete instalado coincide con la guardada) y los estados
 *   FORMAT_CHANGED / TEMPORARILY_UNAVAILABLE.
 * - Procesa en serie (sin carreras de secuencia) con una secuencia persistente y monotónica.
 * - Guarda muestras anonimizadas de formatos desconocidos y de entidades en espera de muestras.
 * - Tras [CompatibilityRegistry.UNKNOWN_FORMAT_THRESHOLD] formatos desconocidos seguidos, pasa la
 *   entidad a FORMAT_CHANGED: deja de anunciar hasta que se revise (nunca anuncia algo dudoso).
 */
class PaymentProcessor(
    private val repository: PaymentRepository,
    private val assignmentDao: PackageAssignmentDao,
    private val statusDao: SourceStatusDao,
    private val settings: SettingsRepository,
    private val diagnostics: NotificationDiagnostics,
    private val inspector: PackageInspector,
    private val lastAckedSequence: suspend () -> Long,
    private val log: DiagnosticsLogger,
    private val scope: CoroutineScope,
    private val onIncome: () -> Unit,
) {
    private val mutex = Mutex()
    private val deduplication = DeduplicationEngine(repository.fingerprintStore)

    val adapters: List<PaymentSourceAdapter> = KnownSources.all()

    private val _registry = MutableStateFlow(SourcePackageRegistry(adapters))
    /** Registro vigente: la interfaz lo observa para mostrar estados y paquetes por entidad. */
    val registry: StateFlow<SourcePackageRegistry> = _registry.asStateFlow()

    /** Paquetes asignados cuya firma ya no coincide (posible app falsa o reinstalación). */
    private val _untrusted = MutableStateFlow<Set<String>>(emptySet())
    val untrustedPackages: StateFlow<Set<String>> = _untrusted.asStateFlow()

    private val _lastActivity = MutableStateFlow<Long?>(null)
    val lastActivity: StateFlow<Long?> = _lastActivity.asStateFlow()

    fun start() {
        scope.launch {
            combine(assignmentDao.observeAll(), statusDao.observeAll()) { a, s -> a to s }.collect { (assignments, statuses) ->
                rebuild(assignments, statuses)
            }
        }
    }

    private fun rebuild(assignments: List<PackageAssignmentEntity>, statuses: List<SourceStatusEntity>) {
        val untrusted = mutableSetOf<String>()
        val trusted = assignments.mapNotNull { a ->
            val source = runCatching { SourceId.valueOf(a.sourceId) }.getOrNull() ?: return@mapNotNull null
            val installed = inspector.inspect(a.packageName, refresh = true)
            val sameSignature = a.signingSha256 != null && installed.signingSha256 == a.signingSha256
            if (!sameSignature) {
                untrusted += a.packageName
                null
            } else {
                a.packageName to source
            }
        }.toMap()
        val overrides = statuses.mapNotNull { s ->
            val source = runCatching { SourceId.valueOf(s.sourceId) }.getOrNull() ?: return@mapNotNull null
            val override = runCatching { RuntimeOverride.valueOf(s.override) }.getOrDefault(RuntimeOverride.NONE)
            source to override
        }.toMap()
        if (untrusted != _untrusted.value && untrusted.isNotEmpty()) {
            log.warn("Motor", "Firma distinta en paquetes asignados: ${untrusted.joinToString()}. No se leerán.")
        }
        _untrusted.value = untrusted
        _registry.value = SourcePackageRegistry(adapters, trusted, overrides)
    }

    /** Paquete asociado a una entidad (en cualquier estado): el lector toma su contenido. */
    fun isKnownPackage(packageName: String): Boolean = _registry.value.sourceFor(packageName) != null

    fun statusOf(source: SourceId): CompatibilityStatus =
        _registry.value.statuses[source] ?: CompatibilityStatus.NOT_CONFIGURED

    /** Paquetes cuyas notificaciones pueden generar anuncios. */
    val supervisedCount: Int get() = _registry.value.supervisedPackages.size

    fun submit(capture: CapturedNotification, diagnosticMode: Boolean) {
        scope.launch {
            mutex.withLock { process(capture, diagnosticMode) }
        }
    }

    private suspend fun process(capture: CapturedNotification, diagnosticMode: Boolean) {
        val raw = capture.raw
        _lastActivity.value = System.currentTimeMillis()
        val registry = _registry.value
        // Se consulta la secuencia sin consumirla; solo se fija si se crea un evento.
        val floor = maxOf(repository.maxStoredSequence(), lastAckedSequence())
        val candidate = settings.peekNextSequence(floor)
        val pipeline = PaymentPipeline(registry = registry, deduplication = deduplication, nextSequence = { candidate })
        val result = try {
            pipeline.process(raw)
        } catch (e: Exception) {
            log.error("Motor", "Error al procesar notificación", e)
            if (diagnosticMode) diagnostics.showLive(capture, registry.sourceFor(raw.packageName)?.name, "Error: ${e.message}")
            return
        }

        val sourceId = registry.sourceFor(raw.packageName)?.name
        val summary = describe(result)
        if (diagnosticMode) diagnostics.showLive(capture, sourceId, summary)

        when (result) {
            is PipelineResult.Created -> {
                settings.commitSequence(result.event.sequence)
                repository.save(result.event, result.reason)
                resetUnknown(result.event.source)
                log.info("Motor", "${result.event.eventId} ${result.event.source.displayName}: ${result.reason}")
                if (result.event.state == EventState.INCOME_CANDIDATE) onIncome()
            }
            is PipelineResult.UnknownFormat -> {
                diagnostics.store(capture, sourceId, KIND_UNKNOWN, summary)
                registerUnknown(result.source, result.reason)
                log.warn("Motor", "${result.source.displayName}: formato no reconocido, no se anuncia")
            }
            is PipelineResult.NotProcessed -> {
                diagnostics.store(capture, sourceId, KIND_WAITING, summary)
                log.info("Motor", "${result.source.displayName}: muestra anonimizada guardada (${result.reason})")
            }
            is PipelineResult.Duplicate -> log.info("Motor", "Notificación duplicada descartada")
            is PipelineResult.Rejected -> log.info("Motor", "${result.source.displayName}: no se anuncia (${result.reason})")
            PipelineResult.Ignored -> if (diagnosticMode) diagnostics.store(capture, null, KIND_DIAGNOSTIC, summary)
        }
    }

    private suspend fun registerUnknown(source: SourceId, reason: String) {
        val current = statusDao.get(source.name)
        val count = (current?.consecutiveUnknown ?: 0) + 1
        val declared = adapters.first { it.sourceId == source }.declaredStatus
        val reachThreshold = declared.processesPayments && count >= CompatibilityRegistry.UNKNOWN_FORMAT_THRESHOLD
        val override = when {
            current?.override == RuntimeOverride.TEMPORARILY_UNAVAILABLE.name -> current.override
            reachThreshold -> RuntimeOverride.FORMAT_CHANGED.name
            else -> current?.override ?: RuntimeOverride.NONE.name
        }
        if (reachThreshold && current?.override != RuntimeOverride.FORMAT_CHANGED.name) {
            log.error("Motor", "${source.displayName}: $count formatos desconocidos seguidos. Estado: Formato cambiado. Se dejan de anunciar sus pagos.")
        }
        statusDao.upsert(
            SourceStatusEntity(
                sourceId = source.name,
                override = override,
                consecutiveUnknown = count,
                reason = if (override == RuntimeOverride.FORMAT_CHANGED.name) reason else current?.reason,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun resetUnknown(source: SourceId) {
        val current = statusDao.get(source.name) ?: return
        if (current.consecutiveUnknown != 0) statusDao.upsert(current.copy(consecutiveUnknown = 0, updatedAt = System.currentTimeMillis()))
    }

    /** Pausa/reanuda manualmente una entidad (TEMPORARILY_UNAVAILABLE). */
    suspend fun setTemporarilyUnavailable(source: SourceId, unavailable: Boolean) {
        val current = statusDao.get(source.name)
        statusDao.upsert(
            SourceStatusEntity(
                sourceId = source.name,
                override = if (unavailable) RuntimeOverride.TEMPORARILY_UNAVAILABLE.name else RuntimeOverride.NONE.name,
                consecutiveUnknown = 0,
                reason = if (unavailable) "Pausada por el usuario" else null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        log.info("Motor", "${source.displayName}: ${if (unavailable) "pausada" else "reanudada"} por el usuario (anterior: ${current?.override ?: "NONE"})")
    }

    /** Tras revisar el formato (y actualizar la app si hizo falta), se vuelve al estado declarado. */
    suspend fun clearFormatChanged(source: SourceId) {
        val current = statusDao.get(source.name) ?: return
        statusDao.upsert(current.copy(override = RuntimeOverride.NONE.name, consecutiveUnknown = 0, reason = null, updatedAt = System.currentTimeMillis()))
        log.info("Motor", "${source.displayName}: estado 'Formato cambiado' revisado y reiniciado por el usuario")
    }

    suspend fun assignPackage(packageName: String, source: SourceId) {
        val info = inspector.inspect(packageName, refresh = true)
        if (info.signingSha256 == null) {
            log.warn("Motor", "No se pudo leer la firma de $packageName; no se asigna")
            return
        }
        assignmentDao.upsert(
            PackageAssignmentEntity(
                packageName = packageName,
                sourceId = source.name,
                appLabel = info.label,
                appVersion = info.versionName,
                installer = info.installer,
                signingSha256 = info.signingSha256,
                assignedAt = System.currentTimeMillis(),
            ),
        )
        log.info("Motor", "Paquete $packageName asignado a ${source.displayName} (instalador: ${inspector.installerLabel(info.installer)})")
    }

    suspend fun unassignPackage(packageName: String) {
        assignmentDao.delete(packageName)
        log.info("Motor", "Paquete $packageName desasignado")
    }

    private fun describe(result: PipelineResult): String = when (result) {
        is PipelineResult.Created -> "Evento ${result.event.eventId} (${result.event.state.name}): ${result.reason}"
        is PipelineResult.UnknownFormat -> "Formato desconocido: ${result.reason}"
        is PipelineResult.NotProcessed -> "No procesado: ${result.reason}"
        is PipelineResult.Duplicate -> "Duplicado"
        is PipelineResult.Rejected -> "Rechazado: ${result.reason}"
        PipelineResult.Ignored -> "App no asociada a ninguna entidad"
    }

    companion object {
        const val KIND_DIAGNOSTIC = "DIAGNOSTIC"
        const val KIND_UNKNOWN = "UNKNOWN_FORMAT"
        const val KIND_WAITING = "WAITING_FOR_SAMPLE"
    }
}
