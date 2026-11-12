package pe.pagobox.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado real del lector de notificaciones, informado por el propio servicio. */
class ListenerStatus {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    fun setLastResult(description: String) {
        _lastResult.value = description
    }
}
