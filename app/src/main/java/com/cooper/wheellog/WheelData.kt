package com.cooper.wheellog

import android.content.Context
import com.cooper.wheellog.ble.EucBleManager
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import io.github.tritbool.euc.ble.core.BLEConstants
import org.koin.java.KoinJavaComponent

/**
 * COMPATIBILITY WRAPPER - TO BE REMOVED AFTER FULL MIGRATION
 * 
 * This is a temporary compatibility layer that delegates to EucBleManager.
 * All code should be migrated to use EucBleManager directly.
 * 
 * This file exists only to allow compilation during the migration process.
 * Once all references to WheelData are removed from the codebase, this file should be deleted.
 */
@Deprecated("Use EucBleManager directly. This compatibility layer will be removed.")
object WheelData {
    
    private val eucBleManager: EucBleManager by lazy {
        val context = KoinJavaComponent.get<Context>(Context::class.java)
        EucBleManager(context)
    }
    
    // Observe eucData changes to update max values
    init {
        // Note: This is a simplified approach. In a real migration, this should be
        // handled by the ViewModel or Activity that observes eucBleManager.eucData
        // For now, we'll update max values on each access, which is not optimal
        // but allows the legacy code to work during migration.
    }
    
    // ========== CONNECTION STATE ==========
    
    var isConnected: Boolean
        get() = eucBleManager.isConnected.value
        set(value) {
            // Read-only - controlled by BLE
            // If you need to disconnect, call eucBleManager.disconnect()
        }
    
    val connectionState: BLEConstants.ConnectionState
        get() = if (isConnected) BLEConstants.ConnectionState.CONNECTED 
               else BLEConstants.ConnectionState.DISCONNECTED
    
    // ========== DEVICE INFO ==========
    
    val mac: String
        get() = eucBleManager.connectedDevice.value?.address ?: ""
    
    val name: String
        get() = eucBleManager.connectedDevice.value?.name ?: "Unknown"
    
    val model: String
        get() = eucBleManager.eucData.value?.model ?: "Unknown"
    
    val version: String
        get() = eucBleManager.eucData.value?.firmwareVersion ?: "Unknown"
    
    val serial: String
        get() = eucBleManager.eucData.value?.serialNumber ?: "Unknown"
    
    val manufacturer: String
        get() = eucBleManager.eucData.value?.manufacturer ?: "Unknown"
    
    val wheelType: WHEEL_TYPE
        get() = manufacturer.toLegacyWheelType()
    
    var btName: String = ""
    
    // ========== TELEMETRY DATA ==========
    
    val speed: Int
        get() {
            updateMaxValues()
            return (eucBleManager.eucData.value?.speed ?: 0.0 * 100).toInt()
        }
    
    val speedDouble: Double
        get() = eucBleManager.eucData.value?.speed ?: 0.0
    
    val voltage: Int
        get() {
            updateMaxValues()
            return (eucBleManager.eucData.value?.voltage ?: 0.0 * 100).toInt()
        }
    
    val voltageDouble: Double
        get() = eucBleManager.eucData.value?.voltage ?: 0.0
    
    val current: Int
        get() {
            updateMaxValues()
            return (eucBleManager.eucData.value?.current ?: 0.0 * 100).toInt()
        }
    
    val currentDouble: Double
        get() = eucBleManager.eucData.value?.current ?: 0.0
    
    val temperature: Int
        get() {
            updateMaxValues()
            return (eucBleManager.eucData.value?.temperature ?: 0.0 * 100).toInt()
        }
    
    val temperature2: Int
        get() = (eucBleManager.eucData.value?.temperature2 ?: 0.0 * 100).toInt()
    
    val power: Int
        get() = (eucBleManager.eucData.value?.power ?: 0.0 * 100).toInt()
    
    val powerDouble: Double
        get() = eucBleManager.eucData.value?.power ?: 0.0
    
    val phaseCurrent: Int
        get() = (eucBleManager.eucData.value?.phaseCurrent ?: 0.0 * 100).toInt()
    
    val phaseCurrentDouble: Double
        get() = eucBleManager.eucData.value?.phaseCurrent ?: 0.0
    
    val calculatedPwm: Double
        get() = eucBleManager.eucData.value?.pwm ?: 0.0
    
    // Track max values locally (session statistics)
    private var _maxPwm: Double = 0.0
    private var _maxCurrent: Double = 0.0
    private var _maxPhaseCurrent: Double = 0.0
    private var _maxPower: Double = 0.0
    private var _maxTemp: Int = 0
    private var _topSpeed: Int = 0
    private var _startTotalDistance: Double = 0.0
    private var _startDistance: Double = 0.0
    
    val maxPwm: Double
        get() = _maxPwm
    
    val maxCurrentDouble: Double
        get() = _maxCurrent
    
    val maxPhaseCurrentDouble: Double
        get() = _maxPhaseCurrent
    
    val maxPowerDouble: Double
        get() = _maxPower
    
    val maxTemp: Int
        get() = _maxTemp
    
    val topSpeed: Int
        get() = _topSpeed
    
    // ========== BATTERY ==========
    
    val batteryLevel: Int
        get() = eucBleManager.eucData.value?.batteryLevel ?: 0
    
    // ========== DISTANCE & TIME ==========
    
    val distance: Int
        get() = (eucBleManager.eucData.value?.distance ?: 0.0 * 1000).toInt()
    
    val totalDistance: Long
        get() = (eucBleManager.eucData.value?.totalDistance ?: 0.0 * 1000).toLong()
    
    val wheelDistance: Long
        get() = (eucBleManager.eucData.value?.wheelDistance ?: 0.0 * 1000).toLong()
    
    val wheelDistanceDouble: Double
        get() = eucBleManager.eucData.value?.wheelDistance ?: 0.0
    
    val rideTime: Int
        get() = eucBleManager.eucData.value?.rideTime?.toInt() ?: 0
    
    val rideTimeString: String
        get() = formatRideTime(rideTime)
    
    val userDistanceDouble: Double
        get() = 0.0 // TODO: Implement user distance tracking
    
    val remainingDistance: Double
        get() = 0.0 // TODO: Implement remaining distance calculation
    
    val batteryPerKm: Double
        get() = calculateBatteryPerKm()
    
    val avgVoltagePerCell: Double
        get() = calculateAvgVoltagePerCell()
    
    val averageSpeedDouble: Double
        get() = calculateAverageSpeed()
    
    val averageRidingSpeedDouble: Double
        get() = calculateAverageRidingSpeed()
    
    // ========== CALCULATED PROPERTIES ==========
    
    private fun calculateBatteryPerKm(): Double {
        val distance = wheelDistanceDouble
        if (distance <= 0) return 0.0
        val batteryConsumed = 100.0 - batteryLevel
        return batteryConsumed * 1000 / distance
    }
    
    private fun calculateAvgVoltagePerCell(): Double {
        val data = eucBleManager.eucData.value ?: return 0.0
        val cells = when (data.manufacturer.lowercase()) {
            "kingsong" -> 84.0
            "gotway", "begode" -> 84.0
            "inmotion" -> 60.0
            "ninebot" -> 48.0
            else -> 60.0
        }
        return data.voltage / cells
    }
    
    private fun calculateAverageSpeed(): Double {
        val data = eucBleManager.eucData.value ?: return 0.0
        val distanceKm = data.totalDistance ?: 0.0
        val rideTimeSec = data.rideTime
        if (rideTimeSec <= 0) return 0.0
        return distanceKm * 3600 / rideTimeSec
    }
    
    private fun calculateAverageRidingSpeed(): Double {
        // Similar to averageSpeed but only counts moving time
        // For now, return same as averageSpeed
        return calculateAverageSpeed()
    }
    
    val remainingDistance: Double
        get() = calculateRemainingDistance()
    
    private fun calculateRemainingDistance(): Double {
        val batteryPerKm = this.batteryPerKm
        if (batteryPerKm <= 0) return 0.0
        return batteryLevel.toDouble() / batteryPerKm
    }
    
    val userDistanceDouble: Double
        get() = eucBleManager.eucData.value?.totalDistance ?: 0.0
    
    // ========== STATE ==========
    
    var wheelIsReady: Boolean = false
    var wheelAlarm: Boolean = false
    
    // ========== ADAPTER (LEGACY - NOT USED) ==========
    
    @Deprecated("Adapters are removed, use EucBleClient commands")
    val adapter: Any? = null
    
    @Deprecated("BluetoothService is removed, use BleService")
    var bluetoothService: Any? = null
    
    // ========== METHODS ==========
    
    fun getInstance(): WheelData = this
    
    fun initiate() {
        // No-op - initialization is handled by Koin
    }
    
    fun full_reset() {
        // Reset all session statistics
        _maxPwm = 0.0
        _maxCurrent = 0.0
        _maxPhaseCurrent = 0.0
        _maxPower = 0.0
        _maxTemp = 0
        _topSpeed = 0
        _startTotalDistance = 0.0
        _startDistance = 0.0
        wheelIsReady = false
        wheelAlarm = false
    }
    
    fun resetExtremumValues() {
        // Reset max values but keep session data
        _maxPwm = 0.0
        _maxCurrent = 0.0
        _maxPhaseCurrent = 0.0
        _maxPower = 0.0
        _maxTemp = 0
        _topSpeed = 0
    }
    
    fun resetVoltageSag() {
        // Reset voltage sag
        // TODO: Implement proper reset
    }
    
    fun resetUserDistance() {
        _startDistance = eucBleManager.eucData.value?.totalDistance ?: 0.0
    }
    
    fun updateMaxValues() {
        val data = eucBleManager.eucData.value ?: return
        
        // Update max values
        data.speed?.let { speed ->
            val legacySpeed = (speed * 100).toInt()
            if (legacySpeed > _topSpeed) _topSpeed = legacySpeed
        }
        
        data.pwm?.let { pwm ->
            if (pwm > _maxPwm) _maxPwm = pwm
        }
        
        data.current?.let { current ->
            if (current > _maxCurrent) _maxCurrent = current
        }
        
        data.phaseCurrent?.let { phaseCurrent ->
            if (phaseCurrent > _maxPhaseCurrent) _maxPhaseCurrent = phaseCurrent
        }
        
        data.power?.let { power ->
            if (power > _maxPower) _maxPower = power
        }
        
        data.temperature?.let { temp ->
            val legacyTemp = (temp * 100).toInt()
            if (legacyTemp > _maxTemp) _maxTemp = legacyTemp
        }
        
        // Update wheel ready state
        wheelIsReady = data.manufacturer.isNotEmpty()
        wheelAlarm = data.wheelAlarm ?: false
    }
    
    // ========== HELPERS ==========
    
    private fun formatRideTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
    }
}

// Extension function for String to convert manufacturer to WHEEL_TYPE
fun String.toLegacyWheelType(): WHEEL_TYPE {
    return when (this.lowercase()) {
        "kingsong" -> WHEEL_TYPE.KINGSONG
        "gotway", "begode", "veteran", "leaperkim" -> WHEEL_TYPE.GOTWAY
        "inmotion" -> WHEEL_TYPE.INMOTION
        "ninebot" -> WHEEL_TYPE.NINEBOT
        else -> WHEEL_TYPE.Unknown
    }
}
