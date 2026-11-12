package pe.pagobox.engine.source

import pe.pagobox.domain.model.SourceId

/**
 * Adaptadores por entidad. TODOS empiezan sin reglas (NOT_IMPLEMENTED) a propósito:
 * la especificación prohíbe inventar compatibilidad bancaria. Las reglas y los nombres
 * de paquete se agregan aquí después de recopilar notificaciones reales autorizadas
 * (fase 1) y se documentan en docs/compatibilidad-bancaria.md.
 */
object KnownSources {
    fun all(): List<PaymentSourceAdapter> = listOf(
        RuleBasedNotificationAdapter(SourceId.YAPE, packageNames = emptySet(), rules = emptyList()),
        RuleBasedNotificationAdapter(SourceId.PLIN, packageNames = emptySet(), rules = emptyList()),
        RuleBasedNotificationAdapter(SourceId.BCP, packageNames = emptySet(), rules = emptyList()),
        RuleBasedNotificationAdapter(SourceId.INTERBANK, packageNames = emptySet(), rules = emptyList()),
        RuleBasedNotificationAdapter(SourceId.BBVA, packageNames = emptySet(), rules = emptyList()),
        RuleBasedNotificationAdapter(SourceId.SCOTIABANK, packageNames = emptySet(), rules = emptyList()),
        RuleBasedNotificationAdapter(SourceId.BANCO_NACION, packageNames = emptySet(), rules = emptyList()),
    )
}
