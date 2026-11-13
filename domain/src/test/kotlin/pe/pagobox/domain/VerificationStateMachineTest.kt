package pe.pagobox.domain

import pe.pagobox.domain.model.SourceChannel.ANDROID_NOTIFICATION
import pe.pagobox.domain.model.SourceChannel.OFFICIAL_WEBHOOK
import pe.pagobox.domain.model.VerificationStateMachine
import pe.pagobox.domain.model.VerificationStatus
import pe.pagobox.domain.model.VerificationStatus.DETECTED
import pe.pagobox.domain.model.VerificationStatus.DUPLICATE
import pe.pagobox.domain.model.VerificationStatus.INCOME_CANDIDATE
import pe.pagobox.domain.model.VerificationStatus.PENDING_VERIFICATION
import pe.pagobox.domain.model.VerificationStatus.PROVIDER_CONFIRMED
import pe.pagobox.domain.model.VerificationStatus.REJECTED
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationStateMachineTest {
    @Test
    fun `una notificacion Android nunca llega a PROVIDER_CONFIRMED`() {
        for (from in VerificationStatus.entries) {
            assertFalse(VerificationStateMachine.canTransition(ANDROID_NOTIFICATION, from, PROVIDER_CONFIRMED), "desde $from")
        }
        assertFailsWith<IllegalStateException> {
            VerificationStateMachine.transition(ANDROID_NOTIFICATION, INCOME_CANDIDATE, PROVIDER_CONFIRMED)
        }
    }

    @Test
    fun `un evento oficial se confirma desde DETECTED o PENDING_VERIFICATION`() {
        assertTrue(VerificationStateMachine.canTransition(OFFICIAL_WEBHOOK, DETECTED, PROVIDER_CONFIRMED))
        assertTrue(VerificationStateMachine.canTransition(OFFICIAL_WEBHOOK, PENDING_VERIFICATION, PROVIDER_CONFIRMED))
        assertFalse(VerificationStateMachine.canTransition(OFFICIAL_WEBHOOK, DETECTED, INCOME_CANDIDATE))
    }

    @Test
    fun `un posible ingreso Android puede marcarse como duplicado de un pago oficial`() {
        assertTrue(VerificationStateMachine.canTransition(ANDROID_NOTIFICATION, INCOME_CANDIDATE, DUPLICATE))
    }

    @Test
    fun `estados finales no tienen salida`() {
        for (final in listOf(PROVIDER_CONFIRMED, DUPLICATE, REJECTED)) {
            for (target in VerificationStatus.entries) {
                assertFalse(VerificationStateMachine.canTransition(OFFICIAL_WEBHOOK, final, target), "$final -> $target")
                assertFalse(VerificationStateMachine.canTransition(ANDROID_NOTIFICATION, final, target), "$final -> $target")
            }
        }
    }
}
