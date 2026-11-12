package pe.pagobox.app.notification_listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import pe.pagobox.app.container
import pe.pagobox.engine.source.RawNotification
import java.time.Instant

/**
 * Capa de ingreso: convierte StatusBarNotification en RawNotification y la entrega
 * al motor. No contiene reglas bancarias (sección 17). Solo lee el contenido de
 * paquetes de entidades supervisadas.
 */
class PaymentNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        container.listenerConnected.value = true
        container.log.info("Lector", "Servicio de lectura conectado")
    }

    override fun onListenerDisconnected() {
        container.listenerConnected.value = false
        container.log.warn("Lector", "Servicio de lectura desconectado por el sistema")
        // Android permite pedir la reconexión; no se garantiza (sección 7).
        requestRebind(android.content.ComponentName(this, PaymentNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val processor = container.processor
        if (!processor.isSupervised(sbn.packageName)) return
        // Las notificaciones de resumen de grupo no describen un pago individual.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras
        processor.submit(
            RawNotification(
                packageName = sbn.packageName,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                postedAt = Instant.ofEpochMilli(sbn.postTime),
            ),
        )
    }
}
