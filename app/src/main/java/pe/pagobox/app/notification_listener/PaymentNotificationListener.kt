package pe.pagobox.app.notification_listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import pe.pagobox.app.container
import pe.pagobox.app.diagnostics.CapturedNotification
import pe.pagobox.engine.source.RawNotification
import java.time.Instant

/**
 * Capa de ingreso: convierte StatusBarNotification en RawNotification y la entrega al motor.
 * No contiene reglas bancarias (sección 17).
 *
 * Qué se hace con cada notificación:
 * - Paquetes asociados a una entidad (en cualquier estado): se procesan siempre.
 * - Cualquier otra app: se revisa **en memoria** si el texto contiene un importe con moneda. Si lo
 *   tiene, se registra solo la app (paquete, nombre, versión, instalador, contador), nunca el texto.
 *   Con el modo diagnóstico activo (tiempo limitado, lo activa el usuario) además se guarda una
 *   muestra anonimizada.
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
        val c = container
        if (sbn.packageName == packageName) return
        val diagnosticMode = c.diagnosticModeActive()
        // Las notificaciones de resumen de grupo no describen un pago individual.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val n = sbn.notification
        val extras = n.extras
        val raw = RawNotification(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            postedAt = Instant.ofEpochMilli(sbn.postTime),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            notificationKey = sbn.key,
            whenMillis = n.`when`.takeIf { it > 0 && extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true) },
        )
        val info = c.packageInspector.inspect(sbn.packageName)
        c.processor.submit(
            CapturedNotification(
                raw = raw,
                appLabel = info.label,
                appVersion = info.versionName,
                category = n.category,
                extraKeys = extras.keySet().sorted(),
                listenerConnected = c.listenerConnected.value,
            ),
            diagnosticMode,
        )
    }
}
