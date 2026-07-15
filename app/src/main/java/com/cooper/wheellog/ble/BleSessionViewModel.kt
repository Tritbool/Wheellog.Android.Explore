package com.cooper.wheellog.ble

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.wheellog.utils.Constants.wheel_type_from_string
import io.github.tritbool.euc.ble.EucBleClient
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.core.ConnectionCallback
import io.github.tritbool.euc.ble.core.DataCallback
import io.github.tritbool.euc.ble.core.ErrorCallback
import io.github.tritbool.euc.ble.core.ProtocolSelection
import io.github.tritbool.euc.ble.core.ProtocolSelectionMode
import io.github.tritbool.euc.ble.exceptions.BLEException
import io.github.tritbool.euc.ble.models.EUCData
import io.github.tritbool.euc.ble.models.EUCDevice
import io.github.tritbool.euc.ble.protocols.CommandSupport
import io.github.tritbool.euc.ble.protocols.CommandType
import io.github.tritbool.euc.ble.protocols.EUCProtocol
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.ArrayList
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel that manages the BLE session state and provides a reactive interface
 * for the UI to observe BLE connection, device discovery, and telemetry data.
 * 
 * This ViewModel encapsulates the EucBleClient and transforms its callbacks into
 * StateFlow streams that can be safely consumed by the UI on the main thread.
 * 
 * REPLACES: WheelData.java (legacy singleton)
 */
class BleSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val _sessionState = MutableStateFlow(BleSessionState.EMPTY)
    val sessionState: StateFlow<BleSessionState> = _sessionState.asStateFlow()

    // EucBleClient instance - the single source of truth for BLE operations
    // Renamed to _eucBleClient to avoid JVM clash with public fun getEucBleClient()
    private val _eucBleClient: EucBleClient by lazy {
        val handler = CoroutineExceptionHandler { _, throwable ->
            viewModelScope.launch { updateError(throwable.message ?: "BLE internal error") }
        }
        val client = EucBleClient(
            application,
            coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + handler)
        )
        client.initialize()
        setupCallbacks(client)
        client
    }

    // ========== SESSION STATISTICS ==========
    private var sessionTopSpeed: Double = 0.0
    private var sessionMaxPower: Double = 0.0
    private var sessionMaxCurrent: Double = 0.0
    private var sessionMaxPhaseCurrent: Double = 0.0
    private var sessionMaxPwm: Double = 0.0
    private var sessionStartTime: Long = 0
    private var sessionStartDistance: Double = 0.0
    private var sessionStartTotalDistance: Double = 0.0
    private var rideStartTime: Long = 0
    private var ridingTime: Int = 0
    private var lastRideTime: Int = 0
    private var batteryStart: Int = -1
    private var batteryLowest: Int = 101
    private var voltageSag: Int = 20000
    private var wheelIsReady: Boolean = false
    private var wheelAlarm: Boolean = false
    var bmsView: Boolean = false

    // BMS data
    val bms1 = com.cooper.wheellog.utils.SmartBms()
    val bms2 = com.cooper.wheellog.utils.SmartBms()

    // ========== GRAPH DATA (for charts) ==========
    private val graphUpdateInterval = 1000L // milliseconds
    private var graphLastUpdateTime: Long = 0
    val xAxis = ArrayList<String>()
    val currentAxis = ArrayList<Float>()
    val speedAxis = ArrayList<Float>()

    // ========== RIDING TIMER ==========
    private var ridingTimerControl: Timer? = null
    private val ridingSpeedThreshold = 200 // 2km/h in legacy format

    init {
        Timber.i("BleSessionViewModel initialized - REPLACES WheelData")
        startRidingTimerControl()
    }

    private fun setupCallbacks(client: EucBleClient) {
        client.setConnectionCallback(object : ConnectionCallback() {
            override fun onConnecting() {
                viewModelScope.launch {
                    updateConnectionState(BLEConstants.ConnectionState.CONNECTING)
                }
            }

            override fun onConnected() {
                viewModelScope.launch {
                    val device = client.getConnectedDevice()
                    if (device != null) updateConnectedDevice(device)
                }
            }

            override fun onDisconnected() {
                viewModelScope.launch {
                    updateDisconnectedState()
                }
            }

            override fun onConnectionFailed(error: BLEException) {
                viewModelScope.launch {
                    updateError(error.message ?: "Connection failed")
                }
            }

            override fun onDeviceDiscovered(device: EUCDevice) {
                viewModelScope.launch {
                    addScanResult(device)
                }
            }

            override fun onScanCompleted(devices: List<EUCDevice>) {
                viewModelScope.launch {
                    _sessionState.value = _sessionState.value.copy(
                        isScanning = false,
                        scanResults = devices
                    )
                }
            }

            override fun onProtocolSelectionRequired(protocols: List<EUCProtocol>) {
                viewModelScope.launch {
                    _sessionState.value = _sessionState.value.copy(
                        protocolSelectionRequired = true,
                        protocolCandidates = protocols
                    )
                    Timber.i("Protocol auto-detection failed, %d candidates available", protocols.size)
                }
            }

            override fun onProtocolSelected(selection: ProtocolSelection) {
                viewModelScope.launch {
                    _sessionState.value = _sessionState.value.copy(
                        protocolSelectionRequired = false,
                        protocolCandidates = emptyList()
                    )
                    Timber.i("Protocol selected: %s (reason: %s)", selection.manufacturer, selection.reason)
                }
            }
        })

        client.setDataCallback(object : DataCallback {
            override fun onDataReceived(data: EUCData) {
                viewModelScope.launch {
                    updateTelemetryData(data)
                }
            }
        })

        client.setErrorCallback(object : ErrorCallback {
            override fun onError(error: BLEException) {
                viewModelScope.launch {
                    updateError(error.message ?: "Unknown BLE error")
                }
            }
        })
    }

    private fun startRidingTimerControl() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1000L.milliseconds)
                if (_sessionState.value.isConnected && (getLegacySpeed() > ridingSpeedThreshold)) {
                    ridingTime++
                }
            }
        }
    }

    private suspend fun updateConnectionState(state: BLEConstants.ConnectionState) {
        _sessionState.value = _sessionState.value.copy(
            connectionState = state,
            lastError = if (state == BLEConstants.ConnectionState.DISCONNECTED) {
                if (_sessionState.value.connectionState == BLEConstants.ConnectionState.CONNECTED) {
                    _sessionState.value.lastError
                } else {
                    null
                }
            } else {
                null
            }
        )

        Timber.i("Connection state updated to: %s", state)
    }

    private suspend fun updateConnectedDevice(device: EUCDevice) {
        // Reset session statistics
        resetSessionStatistics()

        _sessionState.value = _sessionState.value.copy(
            connectionState = BLEConstants.ConnectionState.CONNECTED,
            selectedDevice = device,
            lastError = null,
            isScanning = false
        )

        Timber.i("Device connected: %s (%s)", device.name, device.address)
    }

    private suspend fun updateDisconnectedState() {
        wheelIsReady = false
        wheelAlarm = false

        _sessionState.value = _sessionState.value.copy(
            connectionState = BLEConstants.ConnectionState.DISCONNECTED,
            selectedDevice = null,
            lastData = null
        )

        Timber.i("Device disconnected")
    }

    private suspend fun updateTelemetryData(data: EUCData) {
        // Update session statistics
        data.speed?.let { speed ->
            if (speed > sessionTopSpeed) {
                sessionTopSpeed = speed
            }
        }

        data.power?.let { power ->
            if (power > sessionMaxPower) {
                sessionMaxPower = power
            }
        }

        data.current?.let { current ->
            if (current > sessionMaxCurrent) {
                sessionMaxCurrent = current
            }
        }

        data.phaseCurrent?.let { phaseCurrent ->
            if (phaseCurrent > sessionMaxPhaseCurrent) {
                sessionMaxPhaseCurrent = phaseCurrent
            }
        }

        data.pwm?.let { pwm ->
            if (pwm > sessionMaxPwm) {
                sessionMaxPwm = pwm
            }
        }

        // Update battery statistics
        val batteryLevel = data.batteryLevel
        if (batteryStart == -1) {
            batteryStart = batteryLevel
        }
        batteryLowest = Math.min(batteryLowest, batteryLevel)

        // Update voltage sag
        val voltage = data.voltage
        if (voltage < voltageSag && voltage > 0) {
            voltageSag = voltage.toInt()
        }

        // Calculate session distance
        val sessionDistance = data.totalDistance?.let { total ->
            if (sessionStartDistance == 0.0) {
                sessionStartDistance = total
                sessionStartTotalDistance = total
            }
            total - sessionStartDistance
        }

        // Calculate session ride time
        val sessionRideTime = if (sessionStartTime > 0) {
            (System.currentTimeMillis() - sessionStartTime) / 1000
        } else {
            null
        }

        // Update graph data
        val currentTime = Calendar.getInstance().timeInMillis
        if (graphLastUpdateTime + graphUpdateInterval < currentTime) {
            graphLastUpdateTime = currentTime
            currentAxis.add(data.current?.toFloat() ?: 0f)
            speedAxis.add(data.speed?.toFloat() ?: 0f)
            xAxis.add(
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                    .format(Calendar.getInstance().time)
            )

            // Limit graph data size
            if (speedAxis.size > (3600000 / graphUpdateInterval)) {
                speedAxis.removeAt(0)
                currentAxis.removeAt(0)
                xAxis.removeAt(0)
            }
        }

        // Check if wheel is ready
        if (!wheelIsReady && data.manufacturer.isNotEmpty()) {
            wheelIsReady = true
        }

        // Update wheel alarm based on data
        wheelAlarm = data.wheelAlarm ?: false

        _sessionState.value = _sessionState.value.copy(
            lastData = data,
            lastDataTimestamp = System.currentTimeMillis(),
            sessionTopSpeed = sessionTopSpeed.takeIf { it > 0 },
            sessionMaxPower = sessionMaxPower.takeIf { it > 0 },
            sessionMaxCurrent = sessionMaxCurrent.takeIf { it > 0 },
            sessionDistance = sessionDistance?.takeIf { it > 0 },
            sessionRideTime = sessionRideTime
        )

        Timber.d(
            "Telemetry updated: speed=%.2f, voltage=%.2f, current=%.2f",
            data.speed, data.voltage, data.current
        )
    }

    private suspend fun updateError(error: String) {
        _sessionState.value = _sessionState.value.copy(
            lastError = error
        )

        Timber.e("BLE error: %s", error)
    }

    // ========== PUBLIC API ==========

    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        viewModelScope.launch {
            try {
                _sessionState.value = _sessionState.value.copy(
                    isScanning = true,
                    scanResults = emptyList(),
                    lastError = null
                )
                _eucBleClient.startScan()
                Timber.i("BLE scan started")
            } catch (e: Exception) {
                updateError(e.message ?: "Failed to start scan")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        viewModelScope.launch {
            try {
                _eucBleClient.stopScan()
                _sessionState.value = _sessionState.value.copy(
                    isScanning = false
                )
                Timber.i("BLE scan stopped")
            } catch (e: Exception) {
                updateError(e.message ?: "Failed to stop scan")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: EUCDevice) {
        viewModelScope.launch {
            try {
                _eucBleClient.connect(device)
                Timber.i("Connecting to device: %s", device.address)
            } catch (e: Exception) {
                updateError(e.message ?: "Failed to connect")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        viewModelScope.launch {
            try {
                _eucBleClient.disconnect()
                Timber.i("Disconnecting from device")
            } catch (e: Exception) {
                updateError(e.message ?: "Failed to disconnect")
            }
        }
    }

    /**
     * Connect to a wheel by MAC address and optional device name.
     * Resolves the BluetoothDevice from the MAC address so the library can
     * establish the GATT connection without a NullPointerException.
     *
     * Unless the protocol selection mode is already FORCED (e.g. the caller has pre-selected
     * a protocol via [forceProtocol]), this switches to AUTO_WITH_MANUAL_FALLBACK so that the
     * user is prompted to pick a protocol manually if auto-detection fails.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectByAddress(mac: String, name: String = "") {
        viewModelScope.launch {
            try {
                if (_eucBleClient.getProtocolSelectionMode() != ProtocolSelectionMode.FORCED) {
                    _eucBleClient.setProtocolSelectionMode(ProtocolSelectionMode.AUTO_WITH_MANUAL_FALLBACK)
                }
                val bluetoothManager = getApplication<Application>()
                    .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val bluetoothDevice = bluetoothManager?.adapter?.getRemoteDevice(mac)
                if (bluetoothDevice == null) {
                    updateError("Bluetooth not available — cannot connect to $mac")
                    return@launch
                }
                val device = EUCDevice(
                    bluetoothDevice = bluetoothDevice,
                    address = mac,
                    name = name,
                    manufacturerId = -1,
                    rssi = 0
                )
                _eucBleClient.connect(device)
                Timber.i("Connecting to device by address: %s", mac)
            } catch (e: Exception) {
                updateError(e.message ?: "Failed to connect to $mac")
            }
        }
    }

    fun updateScanResults(devices: List<EUCDevice>) {
        viewModelScope.launch {
            _sessionState.value = _sessionState.value.copy(
                scanResults = devices
            )
        }
    }

    fun addScanResult(device: EUCDevice) {
        viewModelScope.launch {
            val currentResults = _sessionState.value.scanResults
            if (!currentResults.any { it.address == device.address }) {
                _sessionState.value = _sessionState.value.copy(
                    scanResults = currentResults + device
                )
            }
        }
    }

    fun clearScanResults() {
        viewModelScope.launch {
            _sessionState.value = _sessionState.value.copy(
                scanResults = emptyList()
            )
        }
    }

    fun getEucBleClient(): EucBleClient = _eucBleClient

    // ========== PROTOCOL SELECTION ==========

    /**
     * Returns all protocols registered in the BLE client.
     * Can be called at any time (before or during connection) to populate a protocol picker UI.
     */
    fun getAvailableProtocols(): List<EUCProtocol> = _eucBleClient.getRegisteredProtocols()

    /**
     * Force a specific protocol by class name before connecting.
     * Sets the selection mode to FORCED so the library skips auto-detection entirely.
     * Call [clearForcedProtocol] to revert to automatic detection.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun forceProtocol(protocolId: String): Boolean {
        val protocol = _eucBleClient.getRegisteredProtocols().find { it.javaClass.simpleName == protocolId }
            ?: return false
        return _eucBleClient.forceProtocol(protocol)
    }

    /**
     * Clear any previously forced protocol and revert to automatic detection.
     */
    fun clearForcedProtocol() {
        _eucBleClient.clearForcedProtocol()
    }

    /**
     * Manually select a protocol when auto-detection has failed
     * (i.e. after [BleSessionState.protocolSelectionRequired] becomes true).
     * Clears the [BleSessionState.protocolSelectionRequired] flag on success.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun selectProtocol(protocolId: String): Boolean {
        val protocol = _sessionState.value.protocolCandidates.find { it.javaClass.simpleName == protocolId }
            ?: _eucBleClient.getRegisteredProtocols().find { it.javaClass.simpleName == protocolId }
            ?: return false
        val result = _eucBleClient.selectProtocol(protocol)
        if (result) {
            viewModelScope.launch {
                _sessionState.value = _sessionState.value.copy(
                    protocolSelectionRequired = false,
                    protocolCandidates = emptyList()
                )
            }
            Timber.i("Protocol manually selected: %s", protocolId)
        }
        return result
    }

    /**
     * Dismiss the protocol selection prompt without choosing a protocol.
     * The wheel will remain connected but without an active decoder.
     */
    fun dismissProtocolSelection() {
        viewModelScope.launch {
            _sessionState.value = _sessionState.value.copy(
                protocolSelectionRequired = false,
                protocolCandidates = emptyList()
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(commandType: CommandType, value: Any = Unit) {
        _eucBleClient.sendCommand(commandType, value)
    }

    fun isCommandSupported(commandType: CommandType): Boolean {
        return _eucBleClient.getCommandSupport(commandType) == CommandSupport.SUPPORTED
    }

    // ========== WHEEL DATA COMPATIBILITY ==========

    // These functions provide compatibility with the legacy WheelData API
    // They will be used during migration and can be removed later

    fun getLegacySpeed(): Int {
        return (_sessionState.value.currentSpeed * 100).toInt()
    }

    fun getLegacyVoltage(): Int {
        return (_sessionState.value.currentVoltage * 100).toInt()
    }

    fun getLegacyCurrent(): Int {
        return (_sessionState.value.currentCurrent * 100).toInt()
    }

    fun getLegacyTemperature(): Int {
        return (_sessionState.value.currentTemperature * 100).toInt()
    }

    fun getLegacyPower(): Int {
        return (_sessionState.value.currentPower * 100).toInt()
    }

    fun getLegacyPhaseCurrent(): Int {
        return ((_sessionState.value.lastData?.phaseCurrent ?: 0.0) * 100).toInt()
    }

    fun getLegacyBatteryLevel(): Int {
        return _sessionState.value.batteryLevel
    }

    fun getLegacyDistance(): Int {
        return ((_sessionState.value.wheelDistance ?: 0.0) * 1000).toInt()
    }

    fun getLegacyTotalDistance(): Long {
        return ((_sessionState.value.totalDistance ?: 0.0) * 1000).toLong()
    }

    fun getLegacyRideTime(): Int {
        return _sessionState.value.rideTime?.toInt() ?: 0
    }

    fun getLegacyTopSpeed(): Int {
        return (sessionTopSpeed * 100).toInt()
    }

    fun getLegacyVoltageSag(): Int {
        return voltageSag
    }

    fun getLegacyBatteryLowest(): Int {
        return batteryLowest
    }

    fun isWheelReady(): Boolean {
        return wheelIsReady
    }

    fun getWheelAlarm(): Boolean {
        return wheelAlarm
    }

    // ========== SESSION MANAGEMENT ==========

    fun resetSessionStatistics() {
        viewModelScope.launch {
            sessionTopSpeed = 0.0
            sessionMaxPower = 0.0
            sessionMaxCurrent = 0.0
            sessionMaxPhaseCurrent = 0.0
            sessionMaxPwm = 0.0
            sessionStartTime = System.currentTimeMillis()
            sessionStartDistance = 0.0
            sessionStartTotalDistance = 0.0
            batteryStart = -1
            batteryLowest = 101
            voltageSag = 20000
            ridingTime = 0
            lastRideTime = 0

            _sessionState.value = _sessionState.value.copy(
                sessionTopSpeed = null,
                sessionMaxPower = null,
                sessionMaxCurrent = null,
                sessionDistance = null,
                sessionRideTime = null
            )
        }
    }

    fun resetMaxValues() {
        sessionTopSpeed = 0.0
        sessionMaxPower = 0.0
        sessionMaxCurrent = 0.0
        sessionMaxPhaseCurrent = 0.0
        sessionMaxPwm = 0.0
    }

    fun resetVoltageSag() {
        voltageSag = 20000
    }

    fun resetUserDistance() {
        sessionStartDistance = 0.0
        sessionStartTotalDistance = 0.0
    }

    fun resetBmsData() {
        // BMS data will be handled separately
    }

    fun fullReset() {
        resetSessionStatistics()
        xAxis.clear()
        currentAxis.clear()
        speedAxis.clear()
        wheelIsReady = false
        wheelAlarm = false
    }

    // ========== PUBLIC PROPERTIES FOR DIRECT ACCESS (Migration from WheelDataLegacy) ==========

    // Basic telemetry - Double values
    val speed: Double get() = speedDouble
    val speedDouble: Double get() = _sessionState.value.currentSpeed
    val voltageDouble: Double get() = _sessionState.value.currentVoltage
    val currentDouble: Double get() = _sessionState.value.currentCurrent
    val temperature: Int get() = (_sessionState.value.currentTemperature * 100).toInt()
    val temperatureDouble: Double get() = _sessionState.value.currentTemperature
    val powerDouble: Double get() = _sessionState.value.currentPower
    val phaseCurrentDouble: Double get() = _sessionState.value.lastData?.phaseCurrent ?: 0.0
    val torque: Double get() = _sessionState.value.lastData?.torque ?: 0.0
    val angle: Double get() = _sessionState.value.lastData?.angle ?: 0.0
    val roll: Double get() = _sessionState.value.lastData?.roll ?: 0.0

    // Battery and voltage
    val batteryLevel: Int get() = _sessionState.value.batteryLevel
    val batteryLowestLevel: Int get() = batteryLowest
    val voltageSagDouble: Double get() = voltageSag.toDouble() / 100

    // Distances
    val distanceDouble: Double get() = _sessionState.value.wheelDistance ?: 0.0
    val totalDistanceDouble: Double get() = _sessionState.value.totalDistance ?: 0.0
    val wheelDistanceDouble: Double get() = _sessionState.value.wheelDistance ?: 0.0
    val userDistanceDouble: Double get() = 0.0 // TODO: Implement user distance tracking

    // Speeds
    val topSpeedDouble: Double get() = sessionTopSpeed
    val averageSpeedDouble: Double get() = 0.0 // TODO: Implement average speed calculation
    val averageRidingSpeedDouble: Double get() = 0.0 // TODO: Implement average riding speed calculation
    val speedLimit: Double get() = _sessionState.value.speedLimit ?: 0.0

    // PWM
    val calculatedPwm: Double get() = _sessionState.value.pwm ?: 0.0
    val maxPwm: Double get() = sessionMaxPwm

    // Temperatures
    val maxTemp: Double
        get() = _sessionState.value.lastData?.motorTemperature
            ?: _sessionState.value.lastData?.temperature2 ?: _sessionState.value.currentTemperature
    val cpuTemp: Int get() = _sessionState.value.cpuLoad ?: 0
    val imuTemp: Int get() = 0 // TODO: Implement IMU temperature
    val temperature2: Int
        get() = ((_sessionState.value.lastData?.temperature2 ?: 0.0) * 100).toInt()

    // Device info
    val name: String get() = _sessionState.value.deviceName
    val model: String get() = _sessionState.value.deviceModel
    val version: String get() = _sessionState.value.firmwareVersion ?: "Unknown"
    val serial: String get() = _sessionState.value.serialNumber ?: "Unknown"
    val mac: String get() = _sessionState.value.deviceAddress
    val manufacturer: String get() = _sessionState.value.deviceManufacturer
    val getMac: String get() = _sessionState.value.deviceAddress


    // Status
    val isConnected: Boolean get() = _sessionState.value.isConnected
    val fanStatus: Int get() = _sessionState.value.fanStatus ?: 0
    val chargingStatus: Int get() = _sessionState.value.chargingStatus ?: 0
    val output: Int get() = 0 // TODO: Implement output calculation

    val error: String get() = _sessionState.value.lastError ?: ""

    // Time
    val rideTimeString: String get() = formatRideTime(_sessionState.value.rideTime ?: 0)
    val ridingTimeString: String get() = formatRideTime(ridingTime.toLong())
    val sleepTimerString: String get() = "00:00" // TODO: Implement sleep timer

    // Mode and protocol
    val modeStr: String get() = _sessionState.value.lastData?.mode ?: ""
    val protoVer: String get() = "" // TODO: Implement protocol version
    val chargeTime: String get() = "00:00" // TODO: Implement charge time

    // Wheel type (computed from manufacturer)
    val wheelType: com.cooper.wheellog.utils.Constants.WHEEL_TYPE
        get() = wheel_type_from_string(
            manufacturer
        )

    // BMS data
    val bms: Any? get() = null // TODO: Implement BMS data access

    // Current limit
    val currentLimit: Double get() = _sessionState.value.lastData?.current ?: 0.0

    // Motor power
    val motorPower: Double get() = _sessionState.value.currentPower

    // Max values (for display)
    val maxCurrentDouble: Double get() = sessionMaxCurrent
    val maxPhaseCurrentDouble: Double get() = sessionMaxPhaseCurrent
    val maxPowerDouble: Double get() = sessionMaxPower

    // Stats placeholders (computed by other components)
    val remainingDistance: Double get() = 0.0
    val batteryPerKm: Double get() = 0.0
    val avgVoltagePerCell: Double get() = 0.0

    // Feature flags
    val isVoltageTiltbackUnsupported: Boolean get() = false

    // Legacy adapter placeholder (flashlight etc.)
    val adapter: Any? get() = null

    // Wheel alarm (public accessor)
    val wheelAlarmState: Boolean get() = wheelAlarm

    // CPU load
    val cpuLoad: Int get() = _sessionState.value.cpuLoad ?: 0

    // Beep via wheel
    fun wheelBeep() {
        // TODO: Implement via CommandType when available
    }

    private fun formatRideTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    // ========== CLEANUP ==========
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCleared() {
        super.onCleared()
        ridingTimerControl?.cancel()
        ridingTimerControl = null

        viewModelScope.launch {
            try {
                _eucBleClient.cleanup()
                Timber.i("BleSessionViewModel cleanup completed")
            } catch (e: Exception) {
                Timber.e("Error during cleanup: %s", e.message)
            }
        }
    }

    companion object {
        private const val TAG = "BleSessionViewModel"
    }
}
