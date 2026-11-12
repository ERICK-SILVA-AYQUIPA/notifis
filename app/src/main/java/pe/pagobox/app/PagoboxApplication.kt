package pe.pagobox.app

import android.app.Application
import pe.pagobox.app.voice.AndroidVoiceEngine
import pe.pagobox.engine.PaymentPipeline
import pe.pagobox.engine.deduplication.DeduplicationEngine
import pe.pagobox.engine.deduplication.InMemorySeenFingerprintStore
import pe.pagobox.engine.queue.AnnouncementQueue
import pe.pagobox.engine.source.KnownSources
import pe.pagobox.engine.source.SourcePackageRegistry
import pe.pagobox.engine.voice.PhraseTemplateRenderer
import java.util.concurrent.atomic.AtomicLong

/**
 * Composición manual de dependencias (sin framework de DI por ahora, para no
 * introducir dependencias innecesarias). Pendiente: persistencia con Room para
 * huellas, secuencia y eventos anunciados (fase 2).
 */
class PagoboxApplication : Application() {
    lateinit var registry: SourcePackageRegistry
        private set
    lateinit var pipeline: PaymentPipeline
        private set
    lateinit var voice: AndroidVoiceEngine
        private set
    val queue = AnnouncementQueue()
    val phrases = PhraseTemplateRenderer()
    val status = ListenerStatus()

    private val sequence = AtomicLong(0)

    override fun onCreate() {
        super.onCreate()
        registry = SourcePackageRegistry(KnownSources.all())
        pipeline = PaymentPipeline(
            registry = registry,
            deduplication = DeduplicationEngine(InMemorySeenFingerprintStore()),
            nextSequence = { sequence.incrementAndGet() },
        )
        voice = AndroidVoiceEngine(this)
    }
}
