package pe.pagobox.app.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import pe.pagobox.app.data.repositories.DiagnosticsLogger

data class FoundDevice(val name: String, val address: String, val rssi: Int)

/**
 * Enlace BLE GATT con el equipo PAGOBOX (sección 3.2 y 4).
 * - El equipo exige enlace cifrado y autenticado (LE Secure Connections con código
 *   de 6 dígitos mostrado en su pantalla); Android muestra el diálogo de vinculación.
 * - Mensajes JSON terminados en '\n', fragmentados según el MTU negociado.
 * - Reconexión automática con autoConnect cuando el equipo vuelve a estar en rango.
 *
 * Los permisos BLUETOOTH_SCAN/CONNECT se comprueban en la interfaz antes de llamar aquí.
 */
@SuppressLint("MissingPermission")
class BleDeviceLink(context: Context, private val log: DiagnosticsLogger) {
    enum class State { BLUETOOTH_OFF, DISCONNECTED, CONNECTING, BONDING, READY }

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private val _state = MutableStateFlow(if (adapter?.isEnabled == true) State.DISCONNECTED else State.BLUETOOTH_OFF)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<DeviceProtocol.Incoming>(extraBufferCapacity = 32)
    val incoming: SharedFlow<DeviceProtocol.Incoming> = _incoming.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private val rxBuffer = StringBuilder()
    private val writeMutex = Mutex()
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    /** Busca equipos PAGOBOX cercanos (filtrando por el UUID del servicio). */
    fun scan(): Flow<List<FoundDevice>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || adapter?.isEnabled != true) {
            _state.value = State.BLUETOOTH_OFF
            close()
            return@callbackFlow
        }
        val found = linkedMapOf<String, FoundDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: "PAGOBOX"
                found[result.device.address] = FoundDevice(name, result.device.address, result.rssi)
                trySend(found.values.sortedByDescending { it.rssi })
            }

            override fun onScanFailed(errorCode: Int) {
                log.error("BLE", "Fallo de búsqueda BLE: $errorCode")
                close()
            }
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(DeviceProtocol.SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    fun connect(address: String) {
        val bt = adapter ?: return
        if (!bt.isEnabled) {
            _state.value = State.BLUETOOTH_OFF
            return
        }
        disconnect()
        val device = bt.getRemoteDevice(address)
        _state.value = State.CONNECTING
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, true, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, true, callback)
        }
        log.info("BLE", "Conectando con $address")
    }

    fun disconnect() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        rx = null
        pendingWrite?.complete(false)
        _state.value = if (adapter?.isEnabled == true) State.DISCONNECTED else State.BLUETOOTH_OFF
    }

    /** Envía un mensaje completo. Devuelve false si no hay enlace o falló alguna escritura. */
    suspend fun send(message: String): Boolean = writeMutex.withLock {
        val g = gatt ?: return false
        val c = rx ?: return false
        if (_state.value != State.READY) return false
        val bytes = (message + "\n").toByteArray(Charsets.UTF_8)
        val chunk = (mtu - 3).coerceAtLeast(20)
        var offset = 0
        while (offset < bytes.size) {
            val part = bytes.copyOfRange(offset, minOf(bytes.size, offset + chunk))
            val done = CompletableDeferred<Boolean>().also { pendingWrite = it }
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, part, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    c.value = part
                    g.writeCharacteristic(c)
                }
            }
            if (!started || withTimeoutOrNull(WRITE_TIMEOUT_MS) { done.await() } != true) {
                log.warn("BLE", "Falló la escritura BLE")
                return false
            }
            offset += part.size
        }
        true
    }

    /** Continúa la conexión cuando termina la vinculación (o informa si fue rechazada). */
    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val g = gatt ?: return
            if (device?.address != g.device.address) return
            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_BONDED -> {
                    log.info("BLE", "Vinculación completada; negociando MTU")
                    g.requestMtu(REQUESTED_MTU)
                }
                BluetoothDevice.BOND_NONE -> if (_state.value == State.BONDING) {
                    log.error("BLE", "Vinculación rechazada o clave incorrecta")
                    _state.value = State.DISCONNECTED
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // E8: no se negocia el MTU mientras el enlace se cifra; se espera a BOND_BONDED.
                    if (g.device.bondState == BluetoothDevice.BOND_BONDED) {
                        log.info("BLE", "Conectado; negociando MTU")
                        g.requestMtu(REQUESTED_MTU)
                    } else {
                        _state.value = State.BONDING
                        log.info("BLE", "Conectado; esperando vinculación con clave")
                        if (g.device.bondState == BluetoothDevice.BOND_NONE) g.device.createBond()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    rx = null
                    pendingWrite?.complete(false)
                    _state.value = if (adapter?.isEnabled == true) State.CONNECTING else State.BLUETOOTH_OFF
                    log.warn("BLE", "Equipo desconectado (estado $status); se reconectará automáticamente")
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(DeviceProtocol.SERVICE_UUID)
            val tx = service?.getCharacteristic(DeviceProtocol.TX_UUID)
            rx = service?.getCharacteristic(DeviceProtocol.RX_UUID)
            if (service == null || tx == null || rx == null) {
                log.error("BLE", "El equipo no expone el servicio PAGOBOX")
                return
            }
            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(DeviceProtocol.CCCD_UUID) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.READY
                log.info("BLE", "Enlace listo (MTU $mtu)")
            } else {
                // Normalmente: falta de vinculación. Android pedirá el código y se reintentará.
                log.warn("BLE", "No se pudo activar notificaciones (estado $status)")
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onData(value)
        }

        @Deprecated("Android 12 y anteriores")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onData(c.value ?: return)
        }
    }

    private fun onData(value: ByteArray) {
        rxBuffer.append(String(value, Charsets.UTF_8))
        while (true) {
            val idx = rxBuffer.indexOf("\n")
            if (idx < 0) break
            val line = rxBuffer.substring(0, idx).trim()
            rxBuffer.delete(0, idx + 1)
            if (line.isNotEmpty()) _incoming.tryEmit(DeviceProtocol.parse(line))
        }
        if (rxBuffer.length > MAX_BUFFER) rxBuffer.clear()
    }

    private companion object {
        const val REQUESTED_MTU = 247
        const val WRITE_TIMEOUT_MS = 5_000L
        const val MAX_BUFFER = 4096
    }
}
