package com.cooper.wheellog.ble

import android.Manifest
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.github.tritbool.euc.ble.EucBleClient
import io.github.tritbool.euc.ble.core.ConnectionCallback
import io.github.tritbool.euc.ble.core.DataCallback
import io.github.tritbool.euc.ble.core.ErrorCallback
import io.github.tritbool.euc.ble.exceptions.BLEException
import io.github.tritbool.euc.ble.models.EUCData
import io.github.tritbool.euc.ble.models.EUCDevice
import io.github.tritbool.euc.ble.protocols.CommandType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Wrapper Koin singleton autour de EucBleClient.
 * Expose des StateFlow consommables depuis les ViewModels et Activities.
 *
 * Note threading : les callbacks de EucBleClient arrivent sur un thread background
 * (Dispatchers.IO). Les MutableStateFlow sont thread-safe, mais toute mise à jour
 * de vue Android doit passer par Dispatchers.Main côté collecteur.
 */
class EucBleManager(context: Context) {

    val client: EucBleClient = EucBleClient(context).also { it.initialize() }

    // -------------------------------------------------------------------------
    // État observable
    // -------------------------------------------------------------------------

    private val _isConnected = MutableStateFlow(false)
    /** true dès que la connexion GATT est établie et les services découverts. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _eucData = MutableStateFlow<EUCData?>(null)
    /** Dernière trame décodée reçue. null avant la première trame. */
    val eucData: StateFlow<EUCData?> = _eucData.asStateFlow()

    private val _connectedDevice = MutableStateFlow<EUCDevice?>(null)
    /** Périphérique actuellement connecté. null quand déconnecté. */
    val connectedDevice: StateFlow<EUCDevice?> = _connectedDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<EUCDevice>>(emptyList())
    /** Liste des EUC découverts pendant le scan en cours. Remise à zéro à chaque startScan(). */
    val discoveredDevices: StateFlow<List<EUCDevice>> = _discoveredDevices.asStateFlow()

    // -------------------------------------------------------------------------
    // Enregistrement des callbacks
    // -------------------------------------------------------------------------

    init {
        // ConnectionCallback étend ScanCallback : un seul objet couvre scan + connexion.
        client.setConnectionCallback(object : ConnectionCallback() {
            override fun onScanStarted() {
                Timber.d("[EUC] Scan started")
                _discoveredDevices.value = emptyList()
            }

            override fun onDeviceDiscovered(device: EUCDevice) {
                Timber.d("[EUC] Discovered: %s (%s)", device.name, device.address)
                val current = _discoveredDevices.value
                if (current.none { it.address == device.address }) {
                    _discoveredDevices.value = current + device
                }
            }

            override fun onScanCompleted(devices: List<EUCDevice>) {
                Timber.d("[EUC] Scan completed: %d devices", devices.size)
            }

            override fun onConnected() {
                Timber.d("[EUC] Connected")
                _isConnected.value = true
                _connectedDevice.value = client.getConnectedDevice()
            }

            override fun onDisconnected() {
                Timber.d("[EUC] Disconnected")
                _isConnected.value = false
                _connectedDevice.value = null
            }

            override fun onConnectionFailed(error: BLEException) {
                Timber.e("[EUC] Connection failed: %s", error.message)
            }

            override fun onServicesDiscovered(services: List<BluetoothGattService>) {
                Timber.d("[EUC] Services discovered: %d", services.size)
            }

            override fun onMtuChanged(mtu: Int) {
                Timber.d("[EUC] MTU changed: %d", mtu)
            }
        })

        client.setDataCallback(object : DataCallback {
            override fun onDataReceived(data: EUCData) {
                _eucData.value = data
            }
        })

        client.setErrorCallback(object : ErrorCallback {
            override fun onError(error: BLEException) {
                Timber.e("[EUC] BLE error: %s", error.message)
            }
        })
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() = client.startScan()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() = client.stopScan()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: EUCDevice) = client.connect(device)

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() = client.disconnect()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(commandType: CommandType, value: Any = Unit) =
        client.sendCommand(commandType, value)

    fun getCommandSupport(commandType: CommandType) = client.getCommandSupport(commandType)

    fun getConnectionState() = client.getConnectionState()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun cleanup() = client.cleanup()
}
