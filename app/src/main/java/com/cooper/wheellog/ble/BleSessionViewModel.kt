package com.cooper.wheellog.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.wheellog.utils.Calculator
import com.cooper.wheellog.utils.Constants
import com.cooper.wheellog.utils.Constants.wheel_type_from_string
import com.cooper.wheellog.utils.SmartBms
import io.github.tritbool.euc.ble.EucBleClient
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.core.ConnectionCallback
import io.github.tritbool.euc.ble.core.DataCallback
import io.github.tritbool.euc.ble.core.ErrorCallback
import io.github.tritbool.euc.ble.core.ProtocolSelection
import io.github.tritbool.euc.ble.core.ProtocolSelectionMode
import io.github.tritbool.euc.ble.exceptions.BLEException
import io.github.tritbool.euc.ble.models.BMSData
import io.github.tritbool.euc.ble.models.EUCData
import io.github.tritbool.euc.ble.models.EUCDevice
import io.github.tritbool.euc.ble.protocols.CommandSupport
import io.github.tritbool.euc.ble.protocols.CommandType
import io.github.tritbool.euc.ble.protocols.EUCProtocol
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.cooper.wheellog.AppConfig

/**
 * ViewModel that manages the BLE session state and provides a reactive interface
 * for the UI to observe BLE connection, device discovery, and telemetry data.
 * 
 * This ViewModel encapsulates the EucBleClient and transforms its callbacks into
 * StateFlow streams that can be safely consumed by the UI on the main thread.
 * 
 * REPLACES: WheelData.java (legacy singleton)
 */
class BleSessionViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val appConfig: AppConfig by inject()

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
    private var sessionMaxTemperature: Double = 0.0
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
    val bms1 = SmartBms()
    val bms2 = SmartBms()

    // ========== GRAPH DATA (for charts) ==========
    private val graphUpdateInterval = 1000L // milliseconds
    private var graphLastUpdateTime: Long = 0
    val xAxis = ArrayList<String>()
    val currentAxis = ArrayList<Float>()
    val speedAxis = ArrayList<Float>()

    // ========== RIDING TIMER ==========
    private var ridingTimerControl: Timer? = null
    private val ridingSpeedThreshold = 200 // 2km/h in legacy format

    // ========== PROTOCOL SELECTION ==========
    // True once the library reported an active protocol for the current connection.
    private var protocolActive: Boolean = false
    private var protocolWatchdogJob: Job? = null

    // Grace period given to the library to auto-detect the wheel protocol after the
    // GATT connection is established, before the user is asked to pick one manually.
    private val protocolDetectionTimeoutMs = 6000L

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
                    updateConnectedDevice(client.getConnectedDevice())
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

            override fun onScanStarted() {
                viewModelScope.launch {
                    _sessionState.value = _sessionState.value.copy(isScanning = true)
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
                        // The library keeps its results in a hash map, so this list is in
                        // arbitrary order. Merge it into the discovery-ordered list built
                        // from onDeviceDiscovered instead of replacing it, otherwise the
                        // device list visibly reshuffles when the scan ends.
                        scanResults = ScanResultMerger.merge(_sessionState.value.scanResults, devices)
                    )
                }
            }

            override fun onProtocolSelectionRequired(protocols: List<EUCProtocol>) {
                viewModelScope.launch {
                    // The library also emits this callback as soon as
                    // AUTO_WITH_MANUAL_FALLBACK is enabled while no protocol is active,
                    // i.e. before the wheel is even connected. Auto-detection has not run
                    // at that point, so prompting the user would be premature (and would
                    // fail, since a protocol can only be selected on a connected device).
                    if (!_sessionState.value.isConnected) {
                        Timber.d("Ignoring protocol selection request received before connection")
                        return@launch
                    }
                    requestProtocolSelection(protocols)
                }
            }

            override fun onProtocolSelected(selection: ProtocolSelection) {
                viewModelScope.launch {
                    protocolActive = true
                    cancelProtocolWatchdog()
                    _sessionState.value = _sessionState.value.copy(
                        protocolSelectionRequired = false,
                        protocolCandidates = emptyList()
                    )
                    Timber.i(
                        "Protocol selected: %s (reason: %s)",
                        selection.manufacturer,
                        selection.reason
                    )
                    sendConnectBeepIfEnabled()
                }
            }
        })

        client.setDataCallback(object : DataCallback {
            override fun onDataReceived(data: EUCData) {
                viewModelScope.launch {
                    try {
                        updateTelemetryData(data)
                    } catch (e: Exception) {
                        // A single malformed packet must not tear down the update pipeline
                        Timber.e(e, "Failed to process telemetry packet")
                    }
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
                    _sessionState.value = _sessionState.value.copy(
                        sessionRidingTimeSec = ridingTime.toLong()
                    )
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

    private suspend fun updateConnectedDevice(device: EUCDevice?) {
        // Reset session statistics
        resetSessionStatistics()

        _sessionState.value = _sessionState.value.copy(
            connectionState = BLEConstants.ConnectionState.CONNECTED,
            selectedDevice = device ?: _sessionState.value.selectedDevice,
            lastError = null,
            isScanning = false
        )

        if (device != null) {
            Timber.i("Device connected: %s (%s)", device.name, device.address)
        } else {
            Timber.i("Device connected; device metadata is not available yet")
        }

        startProtocolWatchdog()
    }

    private suspend fun updateDisconnectedState() {
        wheelIsReady = false
        wheelAlarm = false
        bms1.reset()
        bms2.reset()
        protocolActive = false
        cancelProtocolWatchdog()

        _sessionState.value = _sessionState.value.copy(
            connectionState = BLEConstants.ConnectionState.DISCONNECTED,
            selectedDevice = null,
            lastData = null,
            // Telemetry is stale from now on; consumers (logging service, UI) must not
            // keep treating the last received sample as fresh.
            lastDataTimestamp = null,
            protocolSelectionRequired = false,
            protocolCandidates = emptyList()
        )

        Timber.i("Device disconnected")
    }

    /**
     * Publishes a manual protocol selection request to the UI.
     */
    private fun requestProtocolSelection(protocols: List<EUCProtocol>) {
        if (protocols.isEmpty() || protocolActive) return
        cancelProtocolWatchdog()
        _sessionState.value = _sessionState.value.copy(
            protocolSelectionRequired = true,
            protocolCandidates = protocols
        )
        Timber.i("Protocol auto-detection failed, %d candidates available", protocols.size)
    }

    /**
     * Watches the connection after it is established: if the library has not activated any
     * protocol within [protocolDetectionTimeoutMs], the wheel would stay connected without
     * ever producing telemetry, so the user is asked to pick a protocol manually.
     *
     * This is required because the library only emits `onProtocolSelectionRequired` once per
     * selection mode change: the notification it raises when AUTO_WITH_MANUAL_FALLBACK is
     * enabled (before connecting) latches the request and suppresses the one that would
     * otherwise be raised when auto-detection actually fails.
     */
    private fun startProtocolWatchdog() {
        cancelProtocolWatchdog()
        if (protocolActive) return
        protocolWatchdogJob = viewModelScope.launch {
            delay(protocolDetectionTimeoutMs.milliseconds)
            val state = _sessionState.value
            if (protocolActive || !state.isConnected || state.lastDataTimestamp != null) {
                return@launch
            }
            Timber.w("No protocol activated %d ms after connecting", protocolDetectionTimeoutMs)
            requestProtocolSelection(_eucBleClient.getRegisteredProtocols())
        }
    }

    private fun cancelProtocolWatchdog() {
        protocolWatchdogJob?.cancel()
        protocolWatchdogJob = null
    }

    private suspend fun updateTelemetryData(rawData: EUCData) {
        val data = applyGotwayCorrections(rawData)
        updateBmsData(data)

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

        // Track peak board temperature. Some protocols occasionally emit a packet
        // without a valid temperature reading (reported as 0), so only the running
        // maximum is kept — a momentary 0 reading never lowers it and therefore
        // never causes the displayed value to flicker.
        if (data.temperature > sessionMaxTemperature) {
            sessionMaxTemperature = data.temperature
        }

        // Feed the rolling power/distance window used to compute Wh/km
        // consumption (see Calculator.whByKm). Distance is tracked in km
        // elsewhere in this app, but Calculator expects metres.
        data.power?.let { power ->
            val totalDistanceKm = data.totalDistance ?: data.wheelDistance
            totalDistanceKm?.let { Calculator.pushPower(power, (it * 1000).toInt()) }
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
            }
            max(total - sessionStartDistance, 0.0)
        } ?: data.wheelDistance?.let { max(it, 0.0) }

        if (sessionStartTotalDistance == 0.0) {
            data.totalDistance?.takeIf { it > 0.0 }?.let { sessionStartTotalDistance = it }
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

        val mergedData = carryForwardSettingsFields(data)

        _sessionState.value = _sessionState.value.copy(
            lastData = mergedData,
            lastDataTimestamp = System.currentTimeMillis(),
            sessionTopSpeed = sessionTopSpeed.takeIf { it > 0 },
            sessionMaxPower = sessionMaxPower.takeIf { it > 0 },
            sessionMaxCurrent = sessionMaxCurrent.takeIf { it > 0 },
            sessionMaxPwm = sessionMaxPwm.takeIf { it > 0 },
            sessionMaxTemperature = sessionMaxTemperature.takeIf { it > 0 },
            sessionBatteryLowest = batteryLowest.takeIf { it < 101 },
            sessionDistance = sessionDistance?.takeIf { it > 0 },
            sessionRideTime = sessionRideTime
        )

        Timber.d(
            "Telemetry updated: manufacturer=%s, model =%s, speed=%.2f, voltage=%.2f, current=%.2f, pwm=%.2f",
            data.manufacturer, data.model, data.speed, data.voltage, data.current, data.pwm
        )
    }

    /**
     * Gotway/Begode's reverse-engineered protocol has two long-standing quirks that legacy
     * WheelLog corrected on the app side (the wheel itself is never asked to change anything):
     *  - Some MCM boards report speed/distance ~12.5% too high, corrected via [AppConfig.gotwayMcm].
     *  - Depending on firmware/mounting, reported speed/phase current/PWM can be negative while
     *    riding forward; [AppConfig.gotwayNegative] lets the user force them positive ("absolute"),
     *    flip their sign ("straight"), or leave them untouched ("reverse").
     */
    private fun applyGotwayCorrections(data: EUCData): EUCData {
        if (wheel_type_from_string(data.manufacturer) != Constants.WHEEL_TYPE.GOTWAY) return data

        var speed = data.speed
        var phaseCurrent = data.phaseCurrent
        var pwm = data.pwm
        var distance = data.distance
        var wheelDistance = data.wheelDistance
        var totalDistance = data.totalDistance

        val gotwayNegative = appConfig.gotwayNegative.toIntOrNull() ?: 0
        if (gotwayNegative == 0) {
            speed = abs(speed)
            phaseCurrent = phaseCurrent?.let { abs(it) }
            pwm = pwm?.let { abs(it) }
        } else {
            speed *= gotwayNegative
            phaseCurrent = phaseCurrent?.times(gotwayNegative)
            pwm = pwm?.times(gotwayNegative)
        }

        if (appConfig.gotwayMcm) {
            speed *= GOTWAY_MCM_RATIO
            distance *= GOTWAY_MCM_RATIO
            wheelDistance = wheelDistance?.times(GOTWAY_MCM_RATIO)
            totalDistance = totalDistance?.times(GOTWAY_MCM_RATIO)
        }

        return data.copy(
            speed = speed,
            phaseCurrent = phaseCurrent,
            pwm = pwm,
            distance = distance,
            wheelDistance = wheelDistance,
            totalDistance = totalDistance,
        )
    }

    /**
     * Some protocols (e.g. Gotway/Begode) split their settings fields across multiple
     * frame types: a frequent "telemetry" frame that never carries them (always null)
     * and a much rarer "settings" frame that does. Since every incoming [EUCData] fully
     * replaces [BleSessionState.lastData], these fields would otherwise flicker
     * null <-> value in the UI at the telemetry frame rate.
     *
     * Carry forward the last known non-null value for each of these fields instead of
     * letting a null in the current packet overwrite a previously known value. Only a
     * genuine disconnect (which clears lastData entirely) should reset them.
     */
    private fun carryForwardSettingsFields(data: EUCData): EUCData {
        val previous = _sessionState.value.lastData ?: return data
        if (previous.manufacturer != data.manufacturer) return data

        return data.copy(
            pedalsMode = data.pedalsMode ?: previous.pedalsMode,
            alarmMode = data.alarmMode ?: previous.alarmMode,
            rollAngleMode = data.rollAngleMode ?: previous.rollAngleMode,
            usesMiles = data.usesMiles ?: previous.usesMiles,
            autoPowerOffMinutes = data.autoPowerOffMinutes ?: previous.autoPowerOffMinutes,
            tiltBackSpeed = data.tiltBackSpeed ?: previous.tiltBackSpeed,
            ledMode = data.ledMode ?: previous.ledMode,
            lightMode = data.lightMode ?: previous.lightMode,
            alertFlags = data.alertFlags ?: previous.alertFlags,
        )
    }

    private fun updateBmsData(data: EUCData) {
        // Wheels with two battery packs (e.g. ExtremeBull Rocket) report per-pack cell
        // voltages through the active protocol's getBMSData(), while EUCData.cellVoltages
        // only carries the two packs concatenated together. Prefer the per-pack data when
        // the protocol exposes it so each BMS page reflects its own pack.
        val bmsPacks = runCatching {
            _eucBleClient.getBMSData() //.getRegisteredProtocols()
                //.firstOrNull { it.manufacturer == data.manufacturer }
                ?.filter { !it.cellVoltages.isNullOrEmpty() }
                ?.sortedBy { it.bmsIndex }
        }.onFailure {
            // getBMSData() reads decoder state that is mutated on the BLE thread, so a
            // transient failure here must never break the telemetry update pipeline.
            Timber.w(it, "Unable to read per-pack BMS data")
        }.getOrNull()

        if (!bmsPacks.isNullOrEmpty()) {
            applyBmsPackData(bms1, data, bmsPacks[0])
            val secondPack = bmsPacks.getOrNull(1)
            if (secondPack != null) {
                applyBmsPackData(bms2, data, secondPack)
            } else {
                bms2.reset()
            }
            return
        }

        val cellVoltages = data.cellVoltages.orEmpty().filter { it > 0.0 }

        bms1.voltage = data.voltage
        bms1.current = data.current
        bms1.temp1 = data.temperature
        bms1.temp2 = data.motorTemperature ?: 0.0
        bms1.remPerc = data.batteryLevel

        if (cellVoltages.isEmpty()) {
            // Cell voltages are not part of every telemetry packet for some
            // protocols (e.g. Gotway/Begode), so a packet without them doesn't
            // mean the BMS has no cells. Keep the last known cell data instead
            // of clearing it, otherwise the BMS page would flicker between the
            // detailed and fallback layouts on every update.
            return
        }

        val firstPackCount = minOf(cellVoltages.size, bms1.cells.size)
        bms1.cellNum = firstPackCount
        for (i in bms1.cells.indices) {
            bms1.cells[i] = if (i < firstPackCount) cellVoltages[i] else 0.0
        }

        val minCell = cellVoltages.minOrNull() ?: 0.0
        val maxCell = cellVoltages.maxOrNull() ?: 0.0
        bms1.minCell = minCell
        bms1.maxCell = maxCell
        bms1.avgCell = cellVoltages.average()
        bms1.cellDiff = maxCell - minCell
        bms1.minCellNum = (cellVoltages.indexOf(minCell) + 1).coerceAtLeast(1)
        bms1.maxCellNum = (cellVoltages.indexOf(maxCell) + 1).coerceAtLeast(1)

        bms2.reset()
    }

    /**
     * Populates a single BMS pack (bms1 or bms2) from a [BMSData] snapshot returned by the
     * active protocol. Since library 0.0.8, Gotway/Begode protocols decode per-pack
     * voltage/current/temperatures (from Type 1 BMS summary frames) in addition to
     * per-pack cell voltages, so those are preferred over the shared [EUCData] fields
     * whenever the protocol provides them; fields the protocol doesn't measure per-pack
     * fall back to the overall [EUCData] values.
     */
    private fun applyBmsPackData(bms: SmartBms, data: EUCData, pack: BMSData) {
        bms.voltage = pack.voltage ?: data.voltage
        bms.current = pack.current ?: data.current

        val temperatures = pack.temperatures
        if (temperatures != null && temperatures.size >= 4) {
            bms.temp1 = temperatures[0]
            bms.temp2 = temperatures[1]
            bms.temp3 = temperatures[2]
            bms.temp4 = temperatures[3]
        } else {
            bms.temp1 = data.temperature
            bms.temp2 = data.motorTemperature ?: 0.0
        }

        bms.remPerc = data.batteryLevel
        pack.remainingCapacity?.let { bms.remCap = it }
        pack.factoryCapacity?.let { bms.factoryCap = it }
        pack.cycles?.let { bms.fullCycles = it }

        val cellVoltages = pack.cellVoltages.orEmpty().filter { it > 0.0 }
        if (cellVoltages.isEmpty()) {
            // Keep last known cell data instead of clearing it, mirroring the
            // single-pack fallback behaviour above.
            return
        }

        val packCount = minOf(cellVoltages.size, bms.cells.size)
        bms.cellNum = packCount
        for (i in bms.cells.indices) {
            bms.cells[i] = if (i < packCount) cellVoltages[i] else 0.0
        }

        val minCell = cellVoltages.minOrNull() ?: 0.0
        val maxCell = cellVoltages.maxOrNull() ?: 0.0
        bms.minCell = minCell
        bms.maxCell = maxCell
        bms.avgCell = cellVoltages.average()
        bms.cellDiff = maxCell - minCell
        bms.minCellNum = (cellVoltages.indexOf(minCell) + 1).coerceAtLeast(1)
        bms.maxCellNum = (cellVoltages.indexOf(maxCell) + 1).coerceAtLeast(1)
    }

    private suspend fun updateError(error: String) {
        val state = _sessionState.value
        _sessionState.value = state.copy(
            lastError = error,
            // A failing scan must not leave the UI stuck in the "scanning" state.
            isScanning = if (state.isScanning && error.contains("scan", ignoreCase = true)) {
                false
            } else {
                state.isScanning
            }
        )

        Timber.e("BLE error: %s", error)
    }

    // ========== PUBLIC API ==========

    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        viewModelScope.launch {
            try {
                // `isScanning` is only raised once the library reports the platform scan has
                // actually started (onScanStarted); otherwise a refused scan would leave the
                // flag stuck at true forever.
                _sessionState.value = _sessionState.value.copy(
                    scanResults = emptyList(),
                    lastError = null
                )
                _eucBleClient.startScan()
                Timber.i("BLE scan requested")
            } catch (e: Exception) {
                _sessionState.value = _sessionState.value.copy(isScanning = false)
                updateError(e.message ?: "Failed to start scan")
            }
        }
    }

    /**
     * Stops an ongoing scan. Safe (and cheap) to call when no scan is running.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        viewModelScope.launch {
            try {
                _eucBleClient.stopScan()
                Timber.i("BLE scan stopped")
            } catch (e: Exception) {
                // Stopping a scan that is not running is not an error worth surfacing.
                Timber.w(e, "Failed to stop BLE scan")
            } finally {
                _sessionState.value = _sessionState.value.copy(
                    isScanning = false
                )
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: EUCDevice) {
        viewModelScope.launch {
            try {
                prepareForConnection()
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
                prepareForConnection()
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
                // Protocol auto-detection also relies on the advertised device name
                // (e.g. to tell an ExtremeBull apart from a plain Gotway/Begode), so fall
                // back to the name known by the Bluetooth stack when none was supplied.
                val resolvedName = name.ifBlank {
                    runCatching { bluetoothDevice.name }.getOrNull().orEmpty()
                }
                val device = EUCDevice(
                    bluetoothDevice = bluetoothDevice,
                    address = mac,
                    name = resolvedName,
                    manufacturerId = -1,
                    rssi = 0
                )
                _eucBleClient.connect(device)
                Timber.i("Connecting to device by address: %s (%s)", mac, resolvedName)
            } catch (e: Exception) {
                updateError(e.message ?: "Failed to connect to $mac")
            }
        }
    }

    /**
     * Resets the per-connection protocol tracking before a new connection attempt.
     */
    private fun prepareForConnection() {
        protocolActive = false
        cancelProtocolWatchdog()
        _sessionState.value = _sessionState.value.copy(
            lastDataTimestamp = null,
            protocolSelectionRequired = false,
            protocolCandidates = emptyList()
        )
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
            _sessionState.value = _sessionState.value.copy(
                scanResults = ScanResultMerger.merge(_sessionState.value.scanResults, listOf(device))
            )
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
        val protocol =
            _eucBleClient.getRegisteredProtocols().find { it.javaClass.simpleName == protocolId }
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
        val protocol =
            _sessionState.value.protocolCandidates.find { it.javaClass.simpleName == protocolId }
                ?: _eucBleClient.getRegisteredProtocols()
                    .find { it.javaClass.simpleName == protocolId }
                ?: return false
        val result = _eucBleClient.selectProtocol(protocol)
        if (result) {
            protocolActive = true
            cancelProtocolWatchdog()
            viewModelScope.launch {
                _sessionState.value = _sessionState.value.copy(
                    protocolSelectionRequired = false,
                    protocolCandidates = emptyList()
                )
            }
            Timber.i("Protocol manually selected: %s", protocolId)
        } else {
            Timber.w("Manual protocol selection failed: %s", protocolId)
        }
        return result
    }

    /**
     * Dismiss the protocol selection prompt without choosing a protocol.
     * The wheel will remain connected but without an active decoder.
     */
    fun dismissProtocolSelection() {
        viewModelScope.launch {
            cancelProtocolWatchdog()
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

    /**
     * Sends a confirmation beep right after a protocol is selected, mirroring legacy
     * WheelLog's connect-beep behavior (a Gotway/Begode wheel double-beeps to confirm
     * it accepted the BLE connection). Only fires when the active protocol supports
     * [CommandType.BEEP] and the user hasn't disabled it via [AppConfig.connectBeep].
     * The BLE connection callback that triggers this only runs once GATT is already
     * connected, so BLUETOOTH_CONNECT is guaranteed to have been granted.
     */
    @SuppressLint("MissingPermission")
    private fun sendConnectBeepIfEnabled() {
        if (!appConfig.connectBeep) return
        if (!isCommandSupported(CommandType.BEEP)) return
        sendCommand(CommandType.BEEP)
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
            sessionMaxTemperature = 0.0
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
                sessionMaxPwm = null,
                sessionMaxTemperature = null,
                sessionBatteryLowest = null,
                sessionRidingTimeSec = null,
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
        sessionMaxTemperature = 0.0
    }

    fun resetVoltageSag() {
        voltageSag = 20000
    }

    fun resetUserDistance() {
        sessionStartTotalDistance = _sessionState.value.totalDistance ?: 0.0
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
    val distanceDouble: Double
        get() = _sessionState.value.sessionDistance
            ?: _sessionState.value.wheelDistance
            ?: 0.0
    val totalDistanceDouble: Double get() = _sessionState.value.totalDistance ?: 0.0
    val wheelDistanceDouble: Double get() = _sessionState.value.wheelDistance ?: 0.0
    val userDistanceDouble: Double
        get() {
            val totalDistance = _sessionState.value.totalDistance ?: return distanceDouble
            if (sessionStartTotalDistance == 0.0) return 0.0
            return max(totalDistance - sessionStartTotalDistance, 0.0)
        }

    // Speeds
    val topSpeedDouble: Double get() = sessionTopSpeed
    val averageSpeedDouble: Double
        get() = calculateAverageSpeed(distanceDouble, _sessionState.value.sessionRideTime)
    val averageRidingSpeedDouble: Double
        get() = calculateAverageSpeed(distanceDouble, ridingTime.toLong())
    val speedLimit: Double get() = _sessionState.value.speedLimit ?: 0.0

    // PWM
    val calculatedPwm: Double get() = _sessionState.value.pwm ?: 0.0
    val maxPwm: Double get() = sessionMaxPwm

    // Temperatures
    // Highest board temperature reached this session (previously this getter
    // returned the *current* motor/board temperature via a duplicated
    // Elvis-chain, so it never actually tracked a running maximum).
    val maxTemp: Double
        get() = _sessionState.value.sessionMaxTemperature ?: _sessionState.value.currentTemperature
    val cpuTemp: Int get() = _sessionState.value.cpuLoad ?: 0
    val imuTemp: Int get() = (_sessionState.value.lastData?.imuTemperature?: 0.0).toInt()
    // NOTE: scaled ×100 for legacy CSV-logging compatibility (see LoggingService,
    // which divides by 100.0 again). Any new UI code should use
    // [motorTemperatureDouble] instead to avoid displaying/alarming on the
    // raw scaled integer.
    val motorTemperature: Int
        get() = ((_sessionState.value.lastData?.motorTemperature ?: 0.0) * 100).toInt()
    val motorTemperatureDouble: Double get() = _sessionState.value.motorTemperature ?: 0.0

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
    val output: Int get() = normalizePwm(calculatedPwm).roundToInt()

    val error: String get() = _sessionState.value.lastError ?: ""

    // Time
    val rideTimeString: String get() = formatRideTime(_sessionState.value.rideTime ?: 0)
    val ridingTimeString: String get() = formatRideTime(ridingTime.toLong())
    val sleepTimerString: String
        get() = _sessionState.value.autoPowerOffMinutes
            ?.takeIf { it >= 0 }
            ?.let { String.format("%02d:%02d", it / 60, it % 60) }
            ?: "00:00"

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
    val bms: Any? get() = bms1

    // Current limit
    val currentLimit: Double get() = _sessionState.value.lastData?.current ?: 0.0

    // Motor power
    val motorPower: Double get() = _sessionState.value.currentPower

    // Max values (for display)
    val maxCurrentDouble: Double get() = sessionMaxCurrent
    val maxPhaseCurrentDouble: Double get() = sessionMaxPhaseCurrent
    val maxPowerDouble: Double get() = sessionMaxPower

    // Stats placeholders (computed by other components)
    val remainingDistance: Double
        get() = if (batteryPerKm > 0.0) batteryLevel / batteryPerKm else 0.0
    val batteryPerKm: Double
        get() {
            if (batteryStart < 0 || distanceDouble <= 0.0) return 0.0
            return max((batteryStart - batteryLevel) / distanceDouble, 0.0)
        }
    val avgVoltagePerCell: Double
        get() = when {
            bms1.cellNum > 0 -> bms1.avgCell
            else -> 0.0
        }

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

    private fun calculateAverageSpeed(distanceKm: Double, durationSeconds: Long?): Double {
        val seconds = durationSeconds ?: return 0.0
        if (distanceKm <= 0.0 || seconds <= 0L) return 0.0
        return distanceKm / (seconds / 3600.0)
    }

    private fun normalizePwm(pwm: Double): Double {
        if (!pwm.isFinite()) return 0.0
        var normalized = abs(pwm)
        while (normalized > 100.0) {
            normalized /= 10.0
        }
        return normalized.coerceIn(0.0, 100.0)
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

        // Correction factor for Gotway/Begode MCM boards that over-report speed/distance.
        private const val GOTWAY_MCM_RATIO = 0.875
    }
}
