package pe.pagobox.app.voice

import pe.pagobox.app.data.local.PhraseTemplateEntity
import pe.pagobox.domain.model.PaymentEvent
import pe.pagobox.engine.voice.DefaultPhrases
import pe.pagobox.engine.voice.PhraseTemplateRenderer

/**
 * Elige la frase de un evento: primero una plantilla personalizada de la entidad,
 * luego una general, y si ninguna se puede completar sin inventar datos, la predeterminada.
 */
object PhraseSelector {
    fun select(event: PaymentEvent, templates: List<PhraseTemplateEntity>, announceSender: Boolean): String? {
        val renderer = PhraseTemplateRenderer(announceSender = announceSender)
        val verified = event.isProviderConfirmed
        val candidates = templates.filter { it.enabled && it.forVerified == verified }
        val ordered = candidates.filter { it.sourceId == event.source.name } + candidates.filter { it.sourceId == null }
        for (t in ordered) renderer.render(t.template, event)?.let { return it }
        return renderer.render(DefaultPhrases.forEvent(event), event)
    }
}
