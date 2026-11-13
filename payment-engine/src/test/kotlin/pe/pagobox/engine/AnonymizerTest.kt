package pe.pagobox.engine

import pe.pagobox.engine.privacy.NotificationAnonymizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnonymizerTest {
    private fun anon(s: String) = NotificationAnonymizer.anonymize(s)!!

    @Test
    fun `codigos de seguridad se omiten completos`() {
        assertEquals(NotificationAnonymizer.SECURITY_REDACTED, anon("Tu código de verificación es 482913. No lo compartas"))
        assertEquals(NotificationAnonymizer.SECURITY_REDACTED, anon("OTP: 123456"))
        assertEquals(NotificationAnonymizer.SECURITY_REDACTED, anon("Ingresa tu clave dinámica 9988"))
        assertTrue(NotificationAnonymizer.looksSensitive("Tu PIN fue cambiado"))
    }

    @Test
    fun `nombres, importes, numeros y correos se ocultan conservando el formato`() {
        val out = anon("JUAN PEREZ te envió S/ 1,250.50 al 987654321. Operación 00123 a las 15:30. juan@mail.com")
        assertFalse("JUAN" in out || "PEREZ" in out, out)
        assertTrue(out.startsWith("<NOMBRE> te envió S/ 9,999.99"), out)
        assertTrue("#########" in out, out)
        assertTrue("##:##" in out, out)
        assertTrue("<correo>" in out, out)
        assertFalse("1,250" in out || "987654321" in out || "juan@" in out, out)
    }

    @Test
    fun `terminos comunes y entidades se conservan para poder escribir reglas`() {
        assertEquals("¡Yapeo recibido! <NOMBRE> te yapeó S/ 99.99", anon("¡Yapeo recibido! María José Ñúñez te yapeó S/ 20.00"))
        assertEquals("Recibiste una transferencia de <NOMBRE> por S/ 99.99", anon("Recibiste una transferencia de Ana Lú por S/ 10.00"))
    }

    @Test
    fun `nulos y vacios no fallan`() {
        assertEquals(null, NotificationAnonymizer.anonymize(null))
        assertEquals("", NotificationAnonymizer.anonymize(""))
    }
}
