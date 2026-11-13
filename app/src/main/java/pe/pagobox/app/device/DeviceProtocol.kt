package pe.pagobox.app.device

import org.json.JSONObject
import java.util.UUID

/**
 * Constantes y mensajes del protocolo BLE (docs/protocolo.md).
 * Cada mensaje es un JSON terminado en '\n'; si supera el MTU se envía en fragmentos.
 */
object DeviceProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("6a4e0001-7a1b-4c2e-9b0a-50a90b0c0001")
    /** Android -> equipo (write). Requiere enlace cifrado y autenticado. */
    val RX_UUID: UUID = UUID.fromString("6a4e0002-7a1b-4c2e-9b0a-50a90b0c0001")
    /** Equipo -> Android (notify): ACK y estado. */
    val TX_UUID: UUID = UUID.fromString("6a4e0003-7a1b-4c2e-9b0a-50a90b0c0001")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val AUDIO_PORT = 8080

    sealed interface Incoming {
        data class Ack(val eventId: String, val sequence: Long, val stage: String) : Incoming
        data class Status(
            val deviceId: String,
            val firmware: String?,
            val battery: Int?,
            val charging: Boolean,
            val volume: Int?,
            val wifiIp: String?,
            val audioToken: String?,
            val lastSequence: Long,
            val error: String?,
        ) : Incoming
        /** Informe de autoprueba. Pantalla y audio indican inicialización correcta; el técnico confirma lo que ve y oye. */
        data class SelfTestResult(
            val firmware: String?,
            val display: Boolean,
            val audio: Boolean,
            val ble: Boolean,
            val wifi: Boolean,
            val battery: Int?,
            val charging: Boolean,
            val buttons: Map<String, Int>,
            val resetReason: String?,
            val uptimeS: Long,
            val freeHeap: Long,
        ) : Incoming
        data class Unknown(val raw: String) : Incoming
    }

    fun parse(line: String): Incoming = runCatching {
        val o = JSONObject(line)
        when (o.optString("type")) {
            "ACK" -> Incoming.Ack(o.getString("eventId"), o.getLong("sequence"), o.getString("stage"))
            "STATUS" -> Incoming.Status(
                deviceId = o.getString("deviceId"),
                firmware = o.optStringOrNull("firmware"),
                battery = o.optIntOrNull("battery"),
                charging = o.optBoolean("charging", false),
                volume = o.optIntOrNull("volume"),
                wifiIp = o.optStringOrNull("wifiIp")?.takeIf { it.isNotBlank() && it != "0.0.0.0" },
                audioToken = o.optStringOrNull("audioToken"),
                lastSequence = o.optLong("lastSequence", 0),
                error = o.optStringOrNull("error"),
            )
            "SELF_TEST_RESULT" -> {
                val b = o.optJSONObject("buttons")
                Incoming.SelfTestResult(
                    firmware = o.optStringOrNull("firmware"),
                    display = o.optBoolean("display", false),
                    audio = o.optBoolean("audio", false),
                    ble = o.optBoolean("ble", false),
                    wifi = o.optBoolean("wifi", false),
                    battery = o.optIntOrNull("battery"),
                    charging = o.optBoolean("charging", false),
                    buttons = listOf("volUp", "volDown", "mute").associateWith { b?.optInt(it, 0) ?: 0 },
                    resetReason = o.optStringOrNull("resetReason"),
                    uptimeS = o.optLong("uptimeS", 0),
                    freeHeap = o.optLong("freeHeap", 0),
                )
            }
            else -> Incoming.Unknown(line)
        }
    }.getOrElse { Incoming.Unknown(line) }

    fun statusRequest(): String = JSONObject().put("type", "GET_STATUS").toString()

    fun setVolume(volume: Int): String =
        JSONObject().put("type", "SET_VOLUME").put("volume", volume.coerceIn(0, 100)).toString()

    fun wifiConfig(ssid: String, password: String): String =
        JSONObject().put("type", "WIFI_CONFIG").put("ssid", ssid).put("password", password).toString()

    fun selfTest(): String = JSONObject().put("type", "SELF_TEST").toString()

    fun testAnnouncement(): String = JSONObject().put("type", "PLAY_TEST").toString()

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null
}
