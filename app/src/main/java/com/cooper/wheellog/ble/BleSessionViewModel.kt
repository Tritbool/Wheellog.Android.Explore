package com.cooper.wheellog.ble

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tritbool.euc.ble.EucBleClient
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.core.ConnectionCallback
import io.github.tritbool.euc.ble.core.DataCallback
import io.github.tritbool.euc.ble.core.ErrorCallback
import io.github.tritbool.euc.ble.models.EUCData
import io.github.tritbool.euc.ble.models.EUCDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * ViewModel that manages the BLE session state and provides a reactive interface
 * for the UI to observe BLE connection, device discovery, and telemetry data.
 * 
 * This ViewModel encapsulates the EucBleClient and transforms its callbacks into
 * StateFlow streams that can be safely consumed by the UI on the main thread.
 */
class BleSessionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _sessionState = MutableStateFlow(BleSessionState.EMPTY)
    val sessionState: StateFlow<BleSessionState> = _sessionState.asStateFlow()
    
    // EucBleClient instance - the single source of truth for BLE operations
    private val eucBleClient: EucBleClient by lazy {
        EucBleClient(application).apply {
            initialize()
            setupCallbacks()
        }
    }
    
    // Session statistics that we track locally
    private var sessionTopSpeed: Double = 0.0
    private var sessionMaxPower: Double = 0.0
    private var sessionMaxCurrent: Double = 0.0
    private var sessionStartTime: Long = 0
    private var sessionStartDistance: Double = 0.0
    
    init {
        Timber.i("BleSessionViewModel initialized")
    }
    
    private fun setupCallbacks() {
        eucBleClient.setConnectionCallback(object : ConnectionCallback {
            override fun onConnectionStateChange(state: BLEConstants.ConnectionState) {
                viewModelScope.launch {
                    updateConnectionState(state)
                }
            }
            
            override fun onDeviceConnected(device: EUCDevice) {
                viewModelScope.launch {
                    updateConnectedDevice(device)
                }
            }
            
            override fun onDeviceDisconnected(device: EUCDevice) {
                viewModelScope.launch {
                    updateDisconnectedDevice(device)
                }
            }
        })
        
        eucBleClient.setDataCallback(object : DataCallback {
            override fun onDataReceived(data: EUCData) {
                viewModelScope.launch {
                    updateTelemetryData(data)
                }
            }
        })
        
        eucBleClient.setErrorCallback(object : ErrorCallback {
            override fun onError(error: String) {
                viewModelScope.launch {
                    updateError(error)
                }
            }
            
            override fun onError(error: Throwable) {
                viewModelScope.launch {
                    updateError(error.message ?: "Unknown error")
                }
            }
        })
    }
    
    private suspend fun updateConnectionState(state: BLEConstants.ConnectionState) {
        _sessionState.value = _sessionState.value.copy(
            connectionState = state,
            lastError = if (state == BLEConstants.ConnectionState.DISCONNECTED) {
                // Keep the last error if we were connected before
                if (_sessionState.value.connectionState == BLEConstants.ConnectionState.CONNECTED) {
                    _sessionState.value.lastError
                } else {
                    null
                }
            } else {
                null // Clear error on successful connection
            }
        )
        
        Timber.i("Connection state updated to: %s", state)
    }
    
    private suspend fun updateConnectedDevice(device: EUCDevice) {
        // Reset session statistics when a new device connects
        sessionTopSpeed = 0.0
        sessionMaxPower = 0.0
        sessionMaxCurrent = 0.0
        sessionStartTime = System.currentTimeMillis()
        sessionStartDistance = 0.0
        
        _sessionState.value = _sessionState.value.copy(
            connectionState = BLEConstants.ConnectionState.CONNECTED,
            selectedDevice = device,
            lastError = null,
            isScanning = false
        )
        
        Timber.i("Device connected: %s (%s)", device.name, device.address)
    }
    
    private suspend fun updateDisconnectedDevice(device: EUCDevice) {
        _sessionState.value = _sessionState.value.copy(
            connectionState = BLEConstants.ConnectionState.DISCONNECTED,
            selectedDevice = null,
            lastData = null
        )
        
        Timber.i("Device disconnected: %s (%s)", device.name, device.address)
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
        
        // Calculate session distance if we have total distance
        val sessionDistance = data.totalDistance?.let { total ->
            if (sessionStartDistance == 0.0) {
                sessionStartDistance = total
            }
            total - sessionStartDistance
        }
        
        // Calculate session ride time
        val sessionRideTime = if (sessionStartTime > 0) {
            (System.currentTimeMillis() - sessionStartTime) / 1000
        } else {
            null
        }
        
        _sessionState.value = _sessionState.value.copy(
            lastData = data,
            lastDataTimestamp = System.currentTimeMillis(),
            sessionTopSpeed = sessionTopSpeed.takeIf { it > 0 },
            sessionMaxPower = sessionMaxPower.takeIf { it > 0 },
            sessionMaxCurrent = sessionMaxCurrent.takeIf { it > 0 },
            sessionDistance = sessionDistance?.takeIf { it > 0 },
            sessionRideTime = sessionRideTime
        )
        
        Timber.d("Telemetry updated: speed=%.2f, voltage=%.2f, current=%.2f", 
            data.speed, data.voltage, data.current)
    }
    
    private suspend fun updateError(error: String) {
        _sessionState.value = _sessionState.value.copy(
            lastError = error
        )
        
        Timber.e("BLE error: %s", error)
    }
    
    // Public API for BLE operations
    
    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        viewModelScope.launch {
            try {
                eucBleClient.startScan()
                _sessionState.value = _sessionState.value.copy(
                    isScanning = true,
                    scanResults = emptyList(),
                    lastError = null
                )
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
                eucBleClient.stopScan()
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
                eucBleClient.connect(device)
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
                eucBleClient.disconnect()
                Timber.i("Disconnecting from device")
            } catch (e: Exception) {
                updateError(e.message ?: "Failed to disconnect")
            }
        }
    }
    
    // Function to update scan results (called from scan callback)
    fun updateScanResults(devices: List<EUCDevice>) {
        viewModelScope.launch {
            _sessionState.value = _sessionState.value.copy(
                scanResults = devices
            )
        }
    }
    
    // Function to add a single device to scan results
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
    
    // Function to clear scan results
    fun clearScanResults() {
        viewModelScope.launch {
            _sessionState.value = _sessionState.value.copy(
                scanResults = emptyList()
            )
        }
    }
    
    // Get the current EucBleClient for advanced operations
    fun getEucBleClient(): EucBleClient = eucBleClient
    
    // Reset session statistics
    fun resetSessionStatistics() {
        viewModelScope.launch {
            sessionTopSpeed = 0.0
            sessionMaxPower = 0.0
            sessionMaxCurrent = 0.0
            sessionStartTime = System.currentTimeMillis()
            sessionStartDistance = 0.0
            
            _sessionState.value = _sessionState.value.copy(
                sessionTopSpeed = null,
                sessionMaxPower = null,
                sessionMaxCurrent = null,
                sessionDistance = null,
                sessionRideTime = null
            )
        }
    }
    
    // Cleanup resources
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                eucBleClient.cleanup()
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