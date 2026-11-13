package pe.pagobox.app.ui.diagnostics

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.BuildConfig
import pe.pagobox.app.data.local.NotificationSampleEntity
import pe.pagobox.app.data.preferences.AppSettings
import pe.pagobox.app.diagnostics.LiveCapture
import pe.pagobox.app.ui.components.LabeledValue
import pe.pagobox.app.ui.components.Pill
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.ui.components.formatDateTime
import pe.pagobox.app.ui.theme.StatusError
import pe.pagobox.app.ui.theme.StatusNeutral
import pe.pagobox.app.ui.theme.StatusOk
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.privacy.NotificationAnonymizer
import java.time.Duration
import java.time.Instant

/**
 * Fase 2: diagnóstico de notificaciones en el celular real.
 * - Modo temporal (15/60 min) que muestra las notificaciones de cualquier app.
 * - En pantalla, todo se ve anonimizado; el texto original de una captura sin datos sensibles
 *   solo se puede ver tocándola, y nunca se guarda.
 * - Las muestras guardadas están anonimizadas y se pueden borrar o exportar (lo decide el usuario).
 */
@Composable
fun NotificationDiagnosticsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val live by container.notificationDiagnostics.live.collectAsStateWithLifecycle()
    val samples by container.database.notificationSamples().observeRecent(200).collectAsStateWithLifecycle(initialValue = emptyList())
    val assignments by container.database.packageAssignments().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val listenerOn by container.listenerConnected.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val until = settings.diagnosticUntil?.toEpochMilli() ?: 0L
    val active = until > now
    var assignTarget by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionCard("Modo diagnóstico") {
                Text(
                    "Mientras está activo, PAGOBOX muestra las notificaciones de todas las apps para identificar " +
                        "las de su banco. Solo se guardan versiones anonimizadas. Los posibles códigos de seguridad " +
                        "(OTP, PIN, claves) se omiten. Nada se envía a Internet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LabeledValue("Lector de notificaciones", if (listenerOn) "Conectado" else "Desconectado")
                if (active) {
                    val left = Duration.ofMillis(until - now)
                    Pill("Activo: quedan %d:%02d".format(left.toMinutes(), left.seconds % 60), StatusOk)
                    OutlinedButton(onClick = { scope.launch { container.settings.setDiagnosticUntil(null) } }) { Text("Detener") }
                } else {
                    Pill("Inactivo", StatusNeutral)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { container.settings.setDiagnosticUntil(Instant.now().plusSeconds(15 * 60)) } }) { Text("15 min") }
                        Button(onClick = { scope.launch { container.settings.setDiagnosticUntil(Instant.now().plusSeconds(60 * 60)) } }) { Text("60 min") }
                    }
                }
            }
        }

        item { Text("Notificaciones recibidas (${live.size})", style = MaterialTheme.typography.titleMedium) }
        if (live.isEmpty()) {
            item { Text("Aún no hay capturas en esta sesión. Active el modo y pida que le envíen un pago de prueba.", style = MaterialTheme.typography.bodySmall) }
        }
        items(live, key = { "live_${it.id}" }) { item ->
            LiveCaptureCard(item, onAssign = { assignTarget = item.capture.raw.packageName })
        }
        if (live.isNotEmpty()) {
            item { TextButton(onClick = { container.notificationDiagnostics.clearLive() }) { Text("Limpiar lista en pantalla") } }
        }

        item { Text("Apps asignadas a entidades", style = MaterialTheme.typography.titleMedium) }
        if (assignments.isEmpty()) item { Text("Ninguna.", style = MaterialTheme.typography.bodySmall) }
        items(assignments, key = { "as_${it.packageName}" }) { a ->
            SectionCard {
                LabeledValue("Entidad", runCatching { SourceId.valueOf(a.sourceId).displayName }.getOrDefault(a.sourceId))
                LabeledValue("App", "${a.appLabel ?: "?"} ${a.appVersion ?: ""}")
                LabeledValue("Paquete", a.packageName)
                LabeledValue("Instalada desde", container.packageInspector.installerLabel(a.installer))
                LabeledValue("Firma SHA-256", a.signingSha256?.take(23)?.plus("…") ?: "-")
                TextButton(onClick = { scope.launch { container.processor.unassignPackage(a.packageName) } }) { Text("Quitar asignación") }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Muestras anonimizadas (${samples.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = samples.isNotEmpty(), onClick = {
                    scope.launch {
                        val all = container.database.notificationSamples().all()
                        val deviceInfo = "Celular: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · PAGOBOX ${BuildConfig.VERSION_NAME}"
                        val text = container.notificationDiagnostics.report(all, deviceInfo)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "PAGOBOX - registro anonimizado")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Exportar registro anonimizado"))
                    }
                }) { Text("Exportar") }
                OutlinedButton(enabled = samples.isNotEmpty(), onClick = { confirmDeleteAll = true }) { Text("Borrar todo") }
            }
        }
        items(samples, key = { "s_${it.id}" }) { s ->
            SampleCard(s, onDelete = { scope.launch { container.database.notificationSamples().delete(s.id) } })
        }
    }

    assignTarget?.let { pkg ->
        val info = remember(pkg) { container.packageInspector.inspect(pkg, refresh = true) }
        AlertDialog(
            onDismissRequest = { assignTarget = null },
            title = { Text("Asignar app a una entidad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${info.label ?: "?"} ${info.versionName ?: ""}")
                    Text("Paquete: $pkg", style = MaterialTheme.typography.bodySmall)
                    Text("Instalada desde: ${container.packageInspector.installerLabel(info.installer)}", style = MaterialTheme.typography.bodySmall)
                    Text("Firma: ${info.signingSha256?.take(23)?.plus("…") ?: "no disponible"}", style = MaterialTheme.typography.bodySmall)
                    if (info.installer != "com.android.vending") {
                        Text("Atención: la app no se instaló desde Google Play. Asígnela solo si está seguro de que es la oficial.", color = StatusError, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Asignar no activa anuncios: la entidad queda «Esperando muestras» hasta tener formatos verificados.", style = MaterialTheme.typography.bodySmall)
                    SourceId.entries.filter { it != SourceId.OTHER }.forEach { source ->
                        Text(
                            source.displayName,
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch { container.processor.assignPackage(pkg, source) }
                                assignTarget = null
                            }.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { assignTarget = null }) { Text("Cancelar") } },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Borrar todas las muestras") },
            text = { Text("Se borrarán todas las muestras anonimizadas de este celular. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.database.notificationSamples().deleteAll() }
                    confirmDeleteAll = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LiveCaptureCard(item: LiveCapture, onAssign: () -> Unit) {
    var reveal by remember(item.id) { mutableStateOf(false) }
    val r = item.capture.raw
    SectionCard {
        LabeledValue("App", "${item.capture.appLabel ?: "?"} ${item.capture.appVersion ?: ""}")
        LabeledValue("Paquete", r.packageName)
        LabeledValue("Fecha y hora", formatDateTime(r.postedAt.toEpochMilli()))
        LabeledValue("Categoría", item.capture.category ?: "-")
        LabeledValue("Entidad", item.sourceId?.let { runCatching { SourceId.valueOf(it).displayName }.getOrDefault(it) } ?: "Sin asignar")
        LabeledValue("Lector", if (item.capture.listenerConnected) "Conectado" else "Desconectado")
        LabeledValue("Resultado", item.result)
        when {
            item.sensitive -> Text(NotificationAnonymizer.SECURITY_REDACTED, color = StatusError, style = MaterialTheme.typography.bodySmall)
            reveal -> {
                Text("Título: ${r.title ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text("Texto: ${r.bigText ?: r.text ?: "-"}", style = MaterialTheme.typography.bodySmall)
                if (r.textLines.isNotEmpty()) Text("Líneas: ${r.textLines.joinToString(" | ")}", style = MaterialTheme.typography.bodySmall)
                r.subText?.let { Text("Subtexto: $it", style = MaterialTheme.typography.bodySmall) }
                Text("Texto original solo en pantalla; no se guarda.", style = MaterialTheme.typography.labelSmall)
            }
            else -> {
                Text("Título: ${NotificationAnonymizer.anonymize(r.title) ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text("Texto: ${NotificationAnonymizer.anonymize(r.bigText ?: r.text) ?: "-"}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Extras: ${item.capture.extraKeys.joinToString()}", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!item.sensitive) {
                TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Ocultar" else "Ver original") }
            }
            if (item.sourceId == null) TextButton(onClick = onAssign) { Text("Asignar a entidad") }
        }
    }
}

@Composable
private fun SampleCard(s: NotificationSampleEntity, onDelete: () -> Unit) {
    SectionCard {
        Row {
            Text(formatDateTime(s.postedAt), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Pill(
                when (s.kind) {
                    "UNKNOWN_FORMAT" -> "Formato desconocido"
                    "WAITING_FOR_SAMPLE" -> "Muestra de entidad"
                    else -> "Diagnóstico"
                },
                if (s.kind == "UNKNOWN_FORMAT") StatusError else StatusNeutral,
            )
        }
        LabeledValue("App", "${s.appLabel ?: "?"} (${s.packageName})")
        Text("Título: ${s.titleAnon ?: "-"}", style = MaterialTheme.typography.bodySmall)
        Text("Texto: ${s.bigTextAnon ?: s.textAnon ?: "-"}", style = MaterialTheme.typography.bodySmall)
        s.linesAnon?.let { Text("Líneas: $it", style = MaterialTheme.typography.bodySmall) }
        Text("Resultado: ${s.processingResult}", style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = onDelete) { Text("Borrar") }
    }
}
