package pe.pagobox.app.notification_listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import pe.pagobox.app.PagoboxApplication
import pe.pagobox.engine.PipelineResult
import pe.pagobox.engine.source.RawNotification
import java.time.Instant

/**
 * Capa de ingreso: convierte StatusBarNotification en RawNotification y la entrega
 * al motor. No contiene reglas bancarias (sección 17).
 */
class PaymentNotificationListener : NotificationListenerService() {
    private val app get() = application as PagoboxApplication

    override fun onListenerConnected() {
        app.status.setConnected(true)
    }

    override fun onListenerDisconnected() {
        app.status.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Filtrar antes de leer el contenido: solo paquetes supervisados.
        if (app.registry.adapterFor(sbn.packageName) == null) return

        val extras = sbn.notification.extras
        val raw = RawNotification(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            postedAt = Instant.ofEpochMilli(sbn.postTime),
        )

        when (val result = app.pipeline.process(raw)) {
            is PipelineResult.Created -> {
                app.status.setLastResult("${result.event.eventId}: ${result.reason}")
                if (app.queue.enqueue(result.event)) app.voice.drain(app.queue, app.phrases)
            }
            is PipelineResult.Duplicate -> app.status.setLastResult("Duplicado descartado")
            is PipelineResult.Rejected -> app.status.setLastResult("Rechazado: ${result.reason}")
            PipelineResult.Ignored -> Unit
        }
    }
}
