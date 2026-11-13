package pe.pagobox.app.ui.voice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.pagobox.app.AppContainer
import pe.pagobox.app.announce.AnnouncementCoordinator
import pe.pagobox.app.data.local.PhraseTemplateEntity
import pe.pagobox.app.data.local.VoiceProfileEntity
import pe.pagobox.app.ui.components.SectionCard
import pe.pagobox.app.voice.AndroidVoiceEngine
import pe.pagobox.domain.model.AnnouncementStatus
import pe.pagobox.domain.model.ProviderId
import pe.pagobox.domain.model.SourceChannel
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.Money
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.domain.model.SourceId
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.engine.voice.PhraseTemplateRenderer
import java.time.Instant

@Composable
fun VoiceSettingsScreen(container: AppContainer) {
    val stored by container.database.voiceProfiles().observe().collectAsStateWithLifecycle(initialValue = null)
    val templates by container.database.phraseTemplates().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val voices by container.voice.voices.collectAsStateWithLifecycle()
    val voiceState by container.voice.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val profile = stored ?: AnnouncementCoordinator.DEFAULT_VOICE
    var editing by remember { mutableStateOf<PhraseTemplateEntity?>(null) }

    fun save(p: VoiceProfileEntity) = scope.launch { container.database.voiceProfiles().upsert(p) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Voz") {
            when (voiceState) {
                AndroidVoiceEngine.State.READY -> Unit
                AndroidVoiceEngine.State.INITIALIZING -> Text("Iniciando el motor de voz…")
                AndroidVoiceEngine.State.NO_SPANISH_VOICE ->
                    Text("No hay voz en español instalada. Instálala en Ajustes > Sistema > Idioma > Texto a voz.", color = MaterialTheme.colorScheme.error)
                AndroidVoiceEngine.State.UNAVAILABLE ->
                    Text("Este celular no tiene un motor de texto a voz disponible.", color = MaterialTheme.colorScheme.error)
            }
            VoicePicker(voices.map { it.name to "${it.name} (${it.locale}${if (it.offline) ", sin Internet" else ", requiere Internet"})" }, profile.voiceName) {
                save(profile.copy(voiceName = it))
            }
            Text("Velocidad: ${"%.1f".format(profile.speechRate)}x")
            Slider(profile.speechRate, { save(profile.copy(speechRate = it)) }, valueRange = 0.5f..2.0f, steps = 14)
            Text("Tono: ${"%.1f".format(profile.pitch)}")
            Slider(profile.pitch, { save(profile.copy(pitch = it)) }, valueRange = 0.5f..2.0f, steps = 14)
            Text("Volumen en el celular: ${profile.volume}%")
            Slider(profile.volume.toFloat(), { save(profile.copy(volume = it.toInt())) }, valueRange = 0f..100f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anunciar el nombre del remitente", Modifier.weight(1f))
                Switch(profile.announceSender, { save(profile.copy(announceSender = it)) })
            }
            Button(
                enabled = voiceState == AndroidVoiceEngine.State.READY,
                onClick = { scope.launch { container.voice.speak("Se detectó un posible ingreso de 20 soles por Yape. Esta es una prueba de voz.", profile) } },
            ) { Text("Probar voz") }
        }

        SectionCard("Frases personalizadas") {
            Text(
                "Variables: {monto_hablado}, {monto}, {moneda}, {banco}, {remitente}, {hora}, {tipo_operacion}. " +
                    "Pon entre corchetes lo que deba omitirse si falta un dato, por ejemplo: [{remitente} te envió ]{monto_hablado}.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (templates.isEmpty()) Text("Se usan las frases predeterminadas.")
            templates.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { editing = t }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.template)
                        Text(
                            (t.sourceId?.let { SourceId.valueOf(it).displayName } ?: "Todas las entidades") +
                                " · " + (if (t.forVerified) "pagos verificados" else "pagos detectados"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(t.enabled, { v -> scope.launch { container.database.phraseTemplates().upsert(t.copy(enabled = v)) } })
                }
            }
            OutlinedButton(onClick = { editing = PhraseTemplateEntity(sourceId = null, forVerified = false, template = "", enabled = true) }) {
                Text("Agregar frase")
            }
        }
    }

    editing?.let { t ->
        PhraseDialog(
            initial = t,
            announceSender = profile.announceSender,
            onDismiss = { editing = null },
            onDelete = if (t.id != 0L) {
                { scope.launch { container.database.phraseTemplates().delete(t.id) }; editing = null }
            } else {
                null
            },
            onSave = { scope.launch { container.database.phraseTemplates().upsert(it) }; editing = null },
        )
    }
}

@Composable
private fun VoicePicker(options: List<Pair<String, String>>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: "Voz española predeterminada"
    Column {
        Text("Voz (masculina o femenina según las voces instaladas)", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Voz española predeterminada") }, onClick = { onSelect(null); open = false })
            options.forEach { (name, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(name); open = false })
            }
        }
    }
}

@Composable
private fun PhraseDialog(
    initial: PhraseTemplateEntity,
    announceSender: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (PhraseTemplateEntity) -> Unit,
) {
    var text by remember { mutableStateOf(initial.template) }
    var source by remember { mutableStateOf(initial.sourceId) }
    var verified by remember { mutableStateOf(initial.forVerified) }
    var sourceOpen by remember { mutableStateOf(false) }
    val renderer = remember(announceSender) { PhraseTemplateRenderer(announceSender = announceSender) }
    val sample = remember(source, verified) { sampleEvent(source, verified) }
    val preview = renderer.render(text, sample)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nueva frase" else "Editar frase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Frase") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { sourceOpen = true }) {
                    Text(source?.let { SourceId.valueOf(it).displayName } ?: "Todas las entidades")
                }
                DropdownMenu(expanded = sourceOpen, onDismissRequest = { sourceOpen = false }) {
                    DropdownMenuItem(text = { Text("Todas las entidades") }, onClick = { source = null; sourceOpen = false })
                    SourceId.entries.filter { it != SourceId.OTHER }.forEach { s ->
                        DropdownMenuItem(text = { Text(s.displayName) }, onClick = { source = s.name; sourceOpen = false })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Para pagos verificados", Modifier.weight(1f))
                    Switch(verified, { verified = it })
                }
                Text(
                    "Vista previa (ejemplo, no es un pago): " + (preview ?: "falta un dato obligatorio; se usará la frase predeterminada"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                onSave(initial.copy(template = text.trim(), sourceId = source, forVerified = verified))
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Eliminar") } }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/** Evento de ejemplo solo para la vista previa; nunca se guarda. */
private fun sampleEvent(source: String?, verified: Boolean) = PaymentEvent(
    eventId = "preview",
    sequence = 0,
    sourceChannel = if (verified) SourceChannel.OFFICIAL_WEBHOOK else SourceChannel.ANDROID_NOTIFICATION,
    provider = if (verified) ProviderId.MERCADO_PAGO else null,
    externalTransactionId = if (verified) "vista-previa" else null,
    source = source?.let { SourceId.valueOf(it) } ?: SourceId.YAPE,
    eventType = EventType.INCOMING_TRANSFER,
    money = Money.pen("35.50"),
    senderName = "Juan",
    eventTimestamp = Instant.now(),
    detectedTimestamp = Instant.now(),
    verificationStatus = if (verified) VerificationStatus.PROVIDER_CONFIRMED else VerificationStatus.INCOME_CANDIDATE,
    announcementStatus = AnnouncementStatus.PENDING,
    rawEventReference = "preview",
)
