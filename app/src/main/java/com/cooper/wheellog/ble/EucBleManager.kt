package com.cooper.wheellog.ble

import android.Manifest
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
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
class EucBleManager(private val context: Context) {

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

    /**
     * Connexion directe à partir d'un EUCDevice complet (bluetoothDevice != null).
     * N'appelle jamais cette méthode avec un EUCDevice reconstruit depuis un MAC sauvegardé
     * (i.e. bluetoothDevice == null) : BLEManager ferait un !! et planterait.
     * Utilise [connectByAddress] dans ce cas.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: EUCDevice) {
        require(device.bluetoothDevice != null) {
            "EucBleManager.connect() called with bluetoothDevice=null. Use connectByAddress() instead."
        }
        client.connect(device)
    }

    /**
     * Connexion à partir d'une adresse MAC seule (cas typique : lastMac sauvegardé en prefs).
     *
     * Retrouve le BluetoothDevice via BluetoothAdapter.getRemoteDevice().
     * Cette méthode fonctionne même si le device n'est pas pairé,
     * tant que l'adresse MAC est valide — Android crée un objet BluetoothDevice synthétique.
     *
     * Retourne false (et ne tente pas la connexion) si :
     * - l'adresse est vide
     * - l'adapter Bluetooth est null ou désactivé
     *
     * En cas de succès retourne true ; le résultat réel (connecté / échec timeout)
     * arrivera via le StateFlow [isConnected] ou le callback onConnectionFailed.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectByAddress(address: String, name: String = "", manufacturerId: Int = 0): Boolean {
        if (address.isBlank()) return false
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: return false
        if (!adapter.isEnabled) return false
        @Suppress("MissingPermission")
        val bluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Timber.e("[EUC] Invalid MAC address: %s", address)
            return false
        }
        val device = EUCDevice(
            bluetoothDevice  = bluetoothDevice,
            name             = name,
            address          = address,
            manufacturerId   = manufacturerId,
            rssi             = 0
        )
        client.connect(device)
        return true
    }

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
