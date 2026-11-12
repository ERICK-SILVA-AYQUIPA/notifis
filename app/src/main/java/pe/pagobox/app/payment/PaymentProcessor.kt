package pe.pagobox.app.payment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.pagobox.app.data.local.PaymentSourceDao
import pe.pagobox.app.data.repositories.DiagnosticsLogger
import pe.pagobox.app.data.repositories.PaymentRepository
import pe.pagobox.domain.model.EventState
import pe.pagobox.engine.PaymentPipeline
import pe.pagobox.engine.PipelineResult
import pe.pagobox.engine.deduplication.DeduplicationEngine
import pe.pagobox.engine.source.KnownSources
import pe.pagobox.engine.source.PaymentSourceAdapter
import pe.pagobox.engine.source.RawNotification
import pe.pagobox.engine.source.SourcePackageRegistry

/**
 * Orquesta el motor de pagos dentro de la app: filtra por entidades activadas,
 * procesa en serie (sin carreras de secuencia), persiste en Room y avisa al anunciador.
 */
class PaymentProcessor(
    private val repository: PaymentRepository,
    private val sourceDao: PaymentSourceDao,
    private val log: DiagnosticsLogger,
    private val scope: CoroutineScope,
    private val onIncome: () -> Unit,
) {
    private val mutex = Mutex()
    private val deduplication = DeduplicationEngine(repository.fingerprintStore)

    @Volatile private var registry = SourcePackageRegistry(emptyList())

    /** Último instante en que se recibió cualquier notificación supervisada (para diagnóstico). */
    private val _lastActivity = MutableStateFlow<Long?>(null)
    val lastActivity: StateFlow<Long?> = _lastActivity.asStateFlow()

    val adapters: List<PaymentSourceAdapter> = KnownSources.all()

    fun start() {
        scope.launch {
            sourceDao.observeAll().collect { rows ->
                val disabled = rows.filter { !it.enabled }.map { it.sourceId }.toSet()
                registry = SourcePackageRegistry(adapters.filter { it.sourceId.name !in disabled })
            }
        }
    }

    /** Filtro rápido: el lector solo lee el contenido de paquetes supervisados. */
    fun isSupervised(packageName: String): Boolean = registry.adapterFor(packageName) != null

    val supervisedCount: Int get() = registry.supervisedPackages.size

    fun submit(raw: RawNotification) {
        scope.launch {
            mutex.withLock { process(raw) }
        }
    }

    private suspend fun process(raw: RawNotification) {
        _lastActivity.value = System.currentTimeMillis()
        val sequence = repository.nextSequence()
        val pipeline = PaymentPipeline(registry = registry, deduplication = deduplication, nextSequence = { sequence })
        try {
            when (val result = pipeline.process(raw)) {
                is PipelineResult.Created -> {
                    repository.save(result.event, result.reason)
                    log.info("Motor", "${result.event.eventId} ${result.event.source.displayName}: ${result.reason}")
                    if (result.event.state == EventState.INCOME_CANDIDATE) onIncome()
                }
                is PipelineResult.Duplicate -> log.info("Motor", "Notificación duplicada descartada")
                is PipelineResult.Rejected -> log.info("Motor", "Notificación rechazada: ${result.reason}")
                PipelineResult.Ignored -> Unit
            }
        } catch (e: Exception) {
            log.error("Motor", "Error al procesar notificación", e)
        }
    }
}
