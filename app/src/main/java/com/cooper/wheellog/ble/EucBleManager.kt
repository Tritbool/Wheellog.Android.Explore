package com.cooper.wheellog.ble

import android.content.Context
import io.github.tritbool.euc.ble.core.BLEManager
import io.github.tritbool.euc.ble.core.ConnectionCallback
import io.github.tritbool.euc.ble.core.DataCallback
import io.github.tritbool.euc.ble.core.ErrorCallback
import io.github.tritbool.euc.ble.core.ScanCallback
import io.github.tritbool.euc.ble.models.BLEException
import io.github.tritbool.euc.ble.models.EUCData
import io.github.tritbool.euc.ble.models.EUCDevice
import io.github.tritbool.euc.ble.protocols.CommandType
import io.github.tritbool.euc.ble.protocols.GotwayProtocol
import io.github.tritbool.euc.ble.protocols.InMotionProtocol
import io.github.tritbool.euc.ble.protocols.KingsongProtocol
import io.github.tritbool.euc.ble.protocols.LeaperkimProtocol
import io.github.tritbool.euc.ble.protocols.NinebotProtocol
import io.github.tritbool.euc.ble.protocols.NinebotZProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Wrapper Koin singleton autour de BLEManager.
 * Expose des StateFlow consommables depuis les ViewModels et Activities.
 */
class EucBleManager(context: Context) {

    val bleManager: BLEManager = BLEManager(context).also { mgr ->
        mgr.initialize()
        mgr.registerProtocol(KingsongProtocol())
        mgr.registerProtocol(GotwayProtocol())
        mgr.registerProtocol(InMotionProtocol())
        mgr.registerProtocol(NinebotProtocol())
        mgr.registerProtocol(NinebotZProtocol())
        mgr.registerProtocol(LeaperkimProtocol())
    }

    // -------------------------------------------------------------------------
    // État observable
    // -------------------------------------------------------------------------

    private val _isConnected = MutableStateFlow(false)
    /** true dès que la connexion GATT est établie. */
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
        bleManager.setScanCallback(object : ScanCallback {
            override fun onScanStarted() {
                Timber.d("[EUC] Scan started")
                _discoveredDevices.value = emptyList()
            }

            override fun onDeviceDiscovered(device: EUCDevice) {
                Timber.d("[EUC] Discovered: ${device.name} (${device.address})")
                val current = _discoveredDevices.value
                if (current.none { it.address == device.address }) {
                    _discoveredDevices.value = current + device
                }
            }

            override fun onScanCompleted(devices: List<EUCDevice>) {
                Timber.d("[EUC] Scan completed, ${devices.size} devices")
            }
        })

        bleManager.setConnectionCallback(object : ConnectionCallback {
            override fun onConnected() {
                Timber.d("[EUC] Connected")
                _isConnected.value = true
                // BLEManager n'expose pas getConnectedDevice() dans le README ;
                // on récupère le device depuis la dernière trame découverte sélectionnée.
                // Si BLEManager expose cette méthode, remplace la ligne ci-dessous.
            }

            override fun onDisconnected() {
                Timber.d("[EUC] Disconnected")
                _isConnected.value = false
                _connectedDevice.value = null
            }

            override fun onServicesDiscovered(services: List<android.bluetooth.BluetoothGattService>) {
                Timber.d("[EUC] Services discovered: ${services.size}")
            }
        })

        bleManager.setDataCallback(object : DataCallback {
            override fun onDataReceived(data: EUCData) {
                _eucData.value = data
            }
        })

        bleManager.setErrorCallback(object : ErrorCallback {
            override fun onError(error: BLEException) {
                Timber.e("[EUC] BLE error [${error.errorType}]: ${error.message}")
            }
        })
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() = bleManager.startScan()

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() = bleManager.startScan() // TODO: remplacer par bleManager.stopScan() quand exposé

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: EUCDevice) {
        _connectedDevice.value = device
        bleManager.connect(device)
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() = bleManager.disconnect()

    /**
     * Envoie une commande au wheel connecté.
     * @param commandType Le type de commande (ex. CommandType.LIGHT_ON).
     * @param value Paramètre optionnel de la commande. Utilise Unit si aucun paramètre.
     */
    fun sendCommand(commandType: CommandType, value: Any = Unit) {
        val device = _connectedDevice.value
        if (device == null) {
            Timber.w("[EUC] sendCommand called but no device connected")
            return
        }
        val command = bleManager.createCommand(commandType, value)
        bleManager.sendCommand(command, device.getDataCharacteristicUUID())
    }

    fun getCommandSupport(commandType: CommandType) =
        bleManager.getCommandSupport(commandType)

    fun setScanTimeout(ms: Long) = bleManager.setScanTimeout(ms)
    fun setAutoReconnect(enabled: Boolean) = bleManager.setAutoReconnect(enabled)
    fun setMaxRetries(count: Int) = bleManager.setMaxRetries(count)
}
