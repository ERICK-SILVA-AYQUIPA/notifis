package pe.pagobox.app.device

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Transporte de audio por Wi-Fi local (sección 3.2): el celular genera la voz y la envía
 * al equipo, que la reproduce. BLE no se usa para audio continuo.
 * El token se obtiene por el enlace BLE cifrado y evita que otro equipo de la red envíe audio.
 */
class DeviceAudioUploader {
    suspend fun upload(ip: String, token: String, eventId: String, wav: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val id = URLEncoder.encode(eventId, "UTF-8")
            val url = URL("http://$ip:${DeviceProtocol.AUDIO_PORT}/audio?eventId=$id")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 3_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "audio/wav")
                conn.setRequestProperty("X-Pagobox-Token", token)
                conn.setFixedLengthStreamingMode(wav.length())
                conn.outputStream.use { out -> wav.inputStream().use { it.copyTo(out) } }
                val code = conn.responseCode
                check(code in 200..299) { "El equipo respondió HTTP $code" }
            } finally {
                conn.disconnect()
            }
        }
    }
}
