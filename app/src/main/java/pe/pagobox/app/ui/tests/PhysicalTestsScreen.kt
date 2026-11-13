package pe.pagobox.app.ui.tests

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.BuildConfig
import pe.pagobox.app.data.local.PhysicalTestResultEntity
import pe.pagobox.app.diagnostics.PhysicalTestCase
import pe.pagobox.app.diagnostics.PhysicalTestCatalog
import pe.pagobox.app.diagnostics.PhysicalTestResult
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.ui.components.formatDateTime

private val deviceModel get() = "${Build.MANUFACTURER} ${Build.MODEL}"
private val androidVersion get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

/** Fase 6: registro manual de las pruebas físicas. Modelo y versiones se toman del celular. */
@Composable
fun PhysicalTestsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by container.database.physicalTests().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val byId = rows.associateBy { it.testId }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionCard("Celular de prueba") {
                Text("$deviceModel · $androidVersion · PAGOBOX ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Marque cada prueba solo después de observarla. Las versiones de las apps bancarias se incluyen en la exportación.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val done = rows.count { it.result != PhysicalTestResult.NOT_RUN.name }
                Text("Registradas: $done de ${PhysicalTestCatalog.cases.size}", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = {
                    scope.launch {
                        val text = report(container, container.database.physicalTests().all())
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "PAGOBOX - pruebas físicas $deviceModel")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Exportar resultados"))
                    }
                }) { Text("Exportar resultados") }
            }
        }
        items(PhysicalTestCatalog.cases, key = { it.id }) { case ->
            TestCard(case, byId[case.id]) { result, notes ->
                scope.launch {
                    container.database.physicalTests().upsert(
                        PhysicalTestResultEntity(
                            testId = case.id,
                            result = result.name,
                            notes = notes,
                            deviceModel = deviceModel,
                            androidVersion = androidVersion,
                            appVersion = BuildConfig.VERSION_NAME,
                            testedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TestCard(case: PhysicalTestCase, saved: PhysicalTestResultEntity?, onSave: (PhysicalTestResult, String) -> Unit) {
    val current = saved?.result?.let { runCatching { PhysicalTestResult.valueOf(it) }.getOrNull() } ?: PhysicalTestResult.NOT_RUN
    var notes by remember(saved?.notes) { mutableStateOf(saved?.notes.orEmpty()) }
    SectionCard("${case.id} · ${case.title}") {
        Text(case.area, style = MaterialTheme.typography.labelMedium)
        Text("Pasos: ${case.steps}", style = MaterialTheme.typography.bodySmall)
        Text("Esperado: ${case.expected}", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PhysicalTestResult.entries.filter { it != PhysicalTestResult.NOT_RUN }.forEach { r ->
                FilterChip(selected = current == r, onClick = { onSave(r, notes) }, label = { Text(r.label) })
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(500) },
            label = { Text("Observaciones (sin datos personales)") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (notes != saved?.notes.orEmpty()) {
            OutlinedButton(onClick = { onSave(current, notes) }) { Text("Guardar observación") }
        }
        saved?.let { Text("Última actualización: ${formatDateTime(it.testedAt)}", style = MaterialTheme.typography.labelSmall) }
    }
}

private fun report(container: AppContainer, rows: List<PhysicalTestResultEntity>): String = buildString {
    val byId = rows.associateBy { it.testId }
    appendLine("PAGOBOX — Resultados de pruebas físicas")
    appendLine("Celular: $deviceModel · $androidVersion · PAGOBOX ${BuildConfig.VERSION_NAME}")
    val registry = container.processor.registry.value
    container.processor.adapters.forEach { a ->
        val pkgs = registry.packagesOf(a.sourceId)
        val versions = pkgs.joinToString { "$it ${container.packageInspector.inspect(it).versionName ?: "?"}" }
        appendLine("${a.sourceId.displayName}: ${registry.statuses[a.sourceId]?.label} ${if (versions.isNotBlank()) "($versions)" else ""}")
    }
    appendLine()
    PhysicalTestCatalog.cases.forEach { c ->
        val r = byId[c.id]
        val label = r?.result?.let { runCatching { PhysicalTestResult.valueOf(it).label }.getOrNull() } ?: PhysicalTestResult.NOT_RUN.label
        appendLine("${c.id} ${c.title}: $label")
        r?.notes?.takeIf { it.isNotBlank() }?.let { appendLine("   Observaciones: $it") }
        r?.let { appendLine("   ${formatDateTime(it.testedAt)} · ${it.deviceModel} · ${it.androidVersion} · app ${it.appVersion}") }
    }
}
