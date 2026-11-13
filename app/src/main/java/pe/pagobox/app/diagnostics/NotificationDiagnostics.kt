package pe.pagobox.app.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.pagobox.app.data.local.NotificationSampleDao
import pe.pagobox.app.data.local.NotificationSampleEntity
import pe.pagobox.engine.privacy.NotificationAnonymizer
import pe.pagobox.engine.source.RawNotification

/** Notificación capturada por el lector, con los datos que muestra el diagnóstico. */
data class CapturedNotification(
    val raw: RawNotification,
    val appLabel: String?,
    val appVersion: String?,
    val category: String?,
    val extraKeys: List<String>,
    val listenerConnected: Boolean,
)

/** Captura vista en pantalla. El texto original solo vive en memoria y se pierde al cerrar la app. */
data class LiveCapture(
    val id: Long,
    val capture: CapturedNotification,
    val sensitive: Boolean,
    val sourceId: String?,
    val result: String,
)

/**
 * Diagnóstico de notificaciones (fase 2).
 * - En memoria: últimas capturas, para inspección en pantalla (nunca se guardan en crudo).
 * - En la base de datos: solo la versión ANONIMIZADA, borrable desde la app.
 * - Posibles códigos de seguridad (OTP, PIN, claves) se omiten por completo, también en pantalla.
 * - Nada se envía a servidores.
 */
class NotificationDiagnostics(private val dao: NotificationSampleDao) {
    private val _live = MutableStateFlow<List<LiveCapture>>(emptyList())
    val live: StateFlow<List<LiveCapture>> = _live.asStateFlow()
    private var nextId = 1L

    fun showLive(capture: CapturedNotification, sourceId: String?, result: String) {
        val sensitive = NotificationAnonymizer.looksSensitive(capture.raw.fullText)
        val item = LiveCapture(nextId++, capture, sensitive, sourceId, result)
        _live.value = (listOf(item) + _live.value).take(MAX_LIVE)
    }

    fun clearLive() {
        _live.value = emptyList()
    }

    suspend fun store(capture: CapturedNotification, sourceId: String?, kind: String, result: String) {
        val r = capture.raw
        dao.insert(
            NotificationSampleEntity(
                capturedAt = System.currentTimeMillis(),
                packageName = r.packageName,
                appLabel = capture.appLabel,
                appVersion = capture.appVersion,
                sourceId = sourceId,
                category = capture.category,
                titleAnon = NotificationAnonymizer.anonymize(r.title),
                textAnon = NotificationAnonymizer.anonymize(r.text),
                bigTextAnon = NotificationAnonymizer.anonymize(r.bigText),
                linesAnon = r.textLines.takeIf { it.isNotEmpty() }?.joinToString("\n") { NotificationAnonymizer.anonymize(it).orEmpty() },
                subTextAnon = NotificationAnonymizer.anonymize(r.subText),
                extraKeys = capture.extraKeys.joinToString(","),
                postedAt = r.postedAt.toEpochMilli(),
                whenMillis = r.whenMillis,
                listenerConnected = capture.listenerConnected,
                kind = kind,
                processingResult = result,
            ),
        )
    }

    /** Informe de texto para exportar (el usuario decide con quién compartirlo). */
    fun report(samples: List<NotificationSampleEntity>, deviceInfo: String): String = buildString {
        appendLine("PAGOBOX — Registro de diagnóstico de notificaciones (anonimizado)")
        appendLine(deviceInfo)
        appendLine("Muestras: ${samples.size}")
        appendLine("Importes reemplazados por 9, otros dígitos por #, nombres por <NOMBRE>.")
        appendLine("Códigos de seguridad omitidos. Este archivo no contiene datos originales.")
        samples.forEach { s ->
            appendLine("----")
            appendLine("Fecha: ${java.time.Instant.ofEpochMilli(s.postedAt)}  Tipo: ${s.kind}")
            appendLine("App: ${s.appLabel ?: "?"} (${s.packageName}) versión ${s.appVersion ?: "?"}")
            appendLine("Entidad: ${s.sourceId ?: "sin asignar"}  Categoría: ${s.category ?: "-"}")
            appendLine("Título: ${s.titleAnon ?: "-"}")
            appendLine("Texto: ${s.textAnon ?: "-"}")
            s.bigTextAnon?.let { appendLine("Texto ampliado: $it") }
            s.linesAnon?.let { appendLine("Líneas: $it") }
            s.subTextAnon?.let { appendLine("Subtexto: $it") }
            appendLine("Extras disponibles: ${s.extraKeys}")
            appendLine("Resultado: ${s.processingResult}")
        }
    }

    private companion object {
        const val MAX_LIVE = 30
    }
}
