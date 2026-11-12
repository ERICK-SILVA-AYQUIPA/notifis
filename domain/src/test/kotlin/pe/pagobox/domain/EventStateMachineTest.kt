package pe.pagobox.domain

import pe.pagobox.domain.model.EventState
import pe.pagobox.domain.model.EventStateMachine
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventStateMachineTest {
    @Test
    fun `no se puede pasar de RECEIVED a VERIFIED directamente`() {
        assertFalse(EventStateMachine.canTransition(EventState.RECEIVED, EventState.VERIFIED))
        assertFailsWith<IllegalStateException> {
            EventStateMachine.transition(EventState.RECEIVED, EventState.VERIFIED)
        }
    }

    @Test
    fun `un candidato a ingreso puede verificarse y luego anunciarse`() {
        assertTrue(EventStateMachine.canTransition(EventState.INCOME_CANDIDATE, EventState.VERIFIED))
        assertTrue(EventStateMachine.canTransition(EventState.VERIFIED, EventState.ANNOUNCED))
    }

    @Test
    fun `los estados finales no tienen salida`() {
        for (final in listOf(EventState.ANNOUNCED, EventState.DUPLICATE, EventState.REJECTED)) {
            for (target in EventState.entries) {
                assertFalse(EventStateMachine.canTransition(final, target))
            }
        }
    }
}
