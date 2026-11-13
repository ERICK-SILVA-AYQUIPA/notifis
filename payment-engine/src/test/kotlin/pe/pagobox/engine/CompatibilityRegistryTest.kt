package pe.pagobox.engine

import pe.pagobox.domain.model.CompatibilityStatus
import pe.pagobox.domain.model.EventType
import pe.pagobox.domain.model.SourceId
import pe.pagobox.engine.compatibility.CompatibilityRegistry
import pe.pagobox.engine.compatibility.EvidenceKind
import pe.pagobox.engine.compatibility.RuntimeOverride
import pe.pagobox.engine.compatibility.TestEvidence
import pe.pagobox.engine.source.BankNotificationAdapter
import pe.pagobox.engine.source.NotificationFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompatibilityRegistryTest {
    private val realLikeFormat = NotificationFormat(
        id = "f1", eventType = EventType.INCOMING_TRANSFER, pattern = Regex("x"),
        anonymizedSample = "x", expectedAmount = null, provenance = "muestra",
    )

    private class Adapter(status: CompatibilityStatus, formats: List<NotificationFormat>) :
        BankNotificationAdapter(SourceId.YAPE, emptySet(), formats, status)

    private val device = TestEvidence(EvidenceKind.DEVICE, "2026-11-20", "Modelo X", "14", "1.0", true, "docs/pruebas/x.md")
    private val full = device.copy(kind = EvidenceKind.FULL_PROTOCOL)

    @Test
    fun `no se puede declarar compatible sin evidencia real`() {
        assertFailsWith<IllegalArgumentException> {
            CompatibilityRegistry.validate(Adapter(CompatibilityStatus.VERIFIED_COMPATIBLE, listOf(realLikeFormat)), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            CompatibilityRegistry.validate(Adapter(CompatibilityStatus.VERIFIED_COMPATIBLE, listOf(realLikeFormat)), listOf(device))
        }
        CompatibilityRegistry.validate(Adapter(CompatibilityStatus.VERIFIED_COMPATIBLE, listOf(realLikeFormat)), listOf(device, full))
    }

    @Test
    fun `evidencia fallida no cuenta`() {
        assertFailsWith<IllegalArgumentException> {
            CompatibilityRegistry.validate(Adapter(CompatibilityStatus.DEVICE_TESTED, listOf(realLikeFormat)), listOf(device.copy(passed = false)))
        }
    }

    @Test
    fun `no se puede declarar configurado sin formatos`() {
        assertFailsWith<IllegalArgumentException> { Adapter(CompatibilityStatus.CONFIGURED, emptyList()) }
    }

    @Test
    fun `formatos sinteticos nunca validan una entidad real`() {
        assertFailsWith<IllegalArgumentException> {
            CompatibilityRegistry.validate(Adapter(CompatibilityStatus.CONFIGURED, listOf(realLikeFormat.copy(synthetic = true))))
        }
    }

    @Test
    fun `estado efectivo`() {
        val a = Adapter(CompatibilityStatus.LAB_TESTED, listOf(realLikeFormat))
        assertEquals(CompatibilityStatus.NOT_CONFIGURED, CompatibilityRegistry.effectiveStatus(a, false, RuntimeOverride.NONE))
        assertEquals(CompatibilityStatus.LAB_TESTED, CompatibilityRegistry.effectiveStatus(a, true, RuntimeOverride.NONE))
        assertEquals(CompatibilityStatus.FORMAT_CHANGED, CompatibilityRegistry.effectiveStatus(a, true, RuntimeOverride.FORMAT_CHANGED))
        assertEquals(CompatibilityStatus.TEMPORARILY_UNAVAILABLE, CompatibilityRegistry.effectiveStatus(a, true, RuntimeOverride.TEMPORARILY_UNAVAILABLE))
        val waiting = Adapter(CompatibilityStatus.WAITING_FOR_SAMPLE, emptyList())
        assertEquals(CompatibilityStatus.WAITING_FOR_SAMPLE, CompatibilityRegistry.effectiveStatus(waiting, true, RuntimeOverride.FORMAT_CHANGED))
    }
}
