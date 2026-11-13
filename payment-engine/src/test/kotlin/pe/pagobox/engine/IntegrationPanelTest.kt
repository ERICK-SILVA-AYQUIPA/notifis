package pe.pagobox.engine

import pe.pagobox.domain.model.ProviderId
import pe.pagobox.engine.integrations.BackendIntegrationStatus
import pe.pagobox.engine.integrations.IntegrationCatalog
import pe.pagobox.engine.integrations.IntegrationPanel
import pe.pagobox.engine.integrations.ProviderLiveStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrationPanelTest {
    private val mp = IntegrationCatalog.info(ProviderId.MERCADO_PAGO)
    private val izipay = IntegrationCatalog.info(ProviderId.IZIPAY)

    private fun live(status: BackendIntegrationStatus, env: String = "sandbox", provider: ProviderId = ProviderId.MERCADO_PAGO) =
        ProviderLiveStatus(provider, env, status, status != BackendIntegrationStatus.NOT_CONFIGURED, null, null, null, "falla")

    @Test
    fun `nunca se muestra activa sin confirmacion del backend`() {
        assertFalse(IntegrationPanel.summarize(mp, backendConfigured = false, live = null).active)
        assertFalse(IntegrationPanel.summarize(mp, backendConfigured = true, live = null).active)
        for (s in BackendIntegrationStatus.entries - BackendIntegrationStatus.ACTIVE) {
            assertFalse(IntegrationPanel.summarize(mp, true, live(s)).active, s.name)
        }
        val active = IntegrationPanel.summarize(mp, true, live(BackendIntegrationStatus.ACTIVE))
        assertTrue(active.active)
        assertEquals("Activa (sandbox)", active.label)
        assertEquals("Activa", IntegrationPanel.summarize(mp, true, live(BackendIntegrationStatus.ACTIVE, "production")).label)
    }

    @Test
    fun `izipay figura como no disponible porque no hay adaptador`() {
        val s = IntegrationPanel.summarize(izipay, true, live(BackendIntegrationStatus.NOT_IMPLEMENTED, provider = ProviderId.IZIPAY))
        assertEquals("No disponible", s.label)
        assertFalse(s.active)
    }

    @Test
    fun `el catalogo aclara que no consulta todos los pagos recibidos`() {
        for (p in IntegrationCatalog.providers) assertTrue("No" in p.coverage, p.id.name)
    }
}
