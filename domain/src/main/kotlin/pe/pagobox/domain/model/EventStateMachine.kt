package pe.pagobox.domain.model

/**
 * Transiciones permitidas entre estados. En particular, no existe un camino
 * RECEIVED -> VERIFIED: la verificación solo llega desde un adaptador oficial
 * sobre un evento ya clasificado como INCOME_CANDIDATE.
 */
object EventStateMachine {
    private val allowed: Map<EventState, Set<EventState>> = mapOf(
        EventState.RECEIVED to setOf(EventState.PARSED, EventState.REJECTED, EventState.DUPLICATE),
        EventState.PARSED to setOf(
            EventState.INCOME_CANDIDATE,
            EventState.REJECTED,
            EventState.PENDING_REVIEW,
            EventState.DUPLICATE,
        ),
        EventState.INCOME_CANDIDATE to setOf(
            EventState.VERIFIED,
            EventState.ANNOUNCED,
            EventState.DELIVERY_FAILED,
            EventState.DUPLICATE,
        ),
        EventState.VERIFIED to setOf(EventState.ANNOUNCED, EventState.DELIVERY_FAILED),
        EventState.DELIVERY_FAILED to setOf(EventState.ANNOUNCED, EventState.DELIVERY_FAILED),
        EventState.PENDING_REVIEW to setOf(EventState.INCOME_CANDIDATE, EventState.REJECTED),
        EventState.ANNOUNCED to emptySet(),
        EventState.DUPLICATE to emptySet(),
        EventState.REJECTED to emptySet(),
    )

    fun canTransition(from: EventState, to: EventState): Boolean = to in allowed.getValue(from)

    fun transition(from: EventState, to: EventState): EventState {
        check(canTransition(from, to)) { "Transición no permitida: $from -> $to" }
        return to
    }
}
