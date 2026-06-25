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
        get() = (eucBleManager.eucData.value?.speed ?: 0.0 * 100).toInt()
    
    val speedDouble: Double
        get() = eucBleManager.eucData.value?.speed ?: 0.0
    
    val voltage: Int
        get() = (eucBleManager.eucData.value?.voltage ?: 0.0 * 100).toInt()
    
    val voltageDouble: Double
        get() = eucBleManager.eucData.value?.voltage ?: 0.0
    
    val current: Int
        get() = (eucBleManager.eucData.value?.current ?: 0.0 * 100).toInt()
    
    val currentDouble: Double
        get() = eucBleManager.eucData.value?.current ?: 0.0
    
    val temperature: Int
        get() = (eucBleManager.eucData.value?.temperature ?: 0.0 * 100).toInt()
    
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
    
    val maxPwm: Double
        get() = eucBleManager.eucData.value?.pwm ?: 0.0 // TODO: Track max PWM
    
    val maxCurrentDouble: Double
        get() = 0.0 // TODO: Track max current
    
    val maxPhaseCurrentDouble: Double
        get() = 0.0 // TODO: Track max phase current
    
    val maxPowerDouble: Double
        get() = 0.0 // TODO: Track max power
    
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
        get() = 0.0 // TODO: Implement battery per km calculation
    
    val avgVoltagePerCell: Double
        get() = 0.0 // TODO: Implement avg voltage per cell
    
    val averageSpeedDouble: Double
        get() = 0.0 // TODO: Implement average speed
    
    val averageRidingSpeedDouble: Double
        get() = 0.0 // TODO: Implement average riding speed
    
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
        // Reset session statistics
        // TODO: Implement proper reset
    }
    
    fun resetExtremumValues() {
        // Reset max values
        // TODO: Implement proper reset
    }
    
    fun resetVoltageSag() {
        // Reset voltage sag
        // TODO: Implement proper reset
    }
    
    fun resetUserDistance() {
        // Reset user distance
        // TODO: Implement proper reset
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
