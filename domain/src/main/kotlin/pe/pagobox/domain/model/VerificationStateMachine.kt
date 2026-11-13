package pe.pagobox.domain.model

/**
 * Transiciones permitidas del estado de verificación.
 *
 * Regla central: **una notificación Android nunca llega a PROVIDER_CONFIRMED**. Solo un evento del
 * canal OFFICIAL_WEBHOOK puede confirmarse, y solo desde DETECTED o PENDING_VERIFICATION.
 * Un posible ingreso de Android que coincide con una confirmación oficial pasa a DUPLICATE
 * (apuntando al evento oficial), no a confirmado.
 */
object VerificationStateMachine {
    private val allowed: Map<VerificationStatus, Set<VerificationStatus>> = mapOf(
        VerificationStatus.DETECTED to setOf(
            VerificationStatus.INCOME_CANDIDATE,
            VerificationStatus.PENDING_VERIFICATION,
            VerificationStatus.PROVIDER_CONFIRMED,
            VerificationStatus.REJECTED,
            VerificationStatus.DUPLICATE,
        ),
        VerificationStatus.INCOME_CANDIDATE to setOf(
            VerificationStatus.PENDING_VERIFICATION,
            VerificationStatus.REJECTED,
            VerificationStatus.DUPLICATE,
        ),
        VerificationStatus.PENDING_VERIFICATION to setOf(
            VerificationStatus.INCOME_CANDIDATE,
            VerificationStatus.PROVIDER_CONFIRMED,
            VerificationStatus.REJECTED,
            VerificationStatus.DUPLICATE,
        ),
        VerificationStatus.PROVIDER_CONFIRMED to emptySet(),
        VerificationStatus.REJECTED to emptySet(),
        VerificationStatus.DUPLICATE to emptySet(),
    )

    fun canTransition(channel: SourceChannel, from: VerificationStatus, to: VerificationStatus): Boolean {
        if (to == VerificationStatus.PROVIDER_CONFIRMED && channel != SourceChannel.OFFICIAL_WEBHOOK) return false
        if (to == VerificationStatus.INCOME_CANDIDATE && channel != SourceChannel.ANDROID_NOTIFICATION) return false
        return to in allowed.getValue(from)
    }

    fun transition(channel: SourceChannel, from: VerificationStatus, to: VerificationStatus): VerificationStatus {
        check(canTransition(channel, from, to)) { "Transición no permitida en $channel: $from -> $to" }
        return to
    }
}
