package com.cooper.wheellog

import android.content.Context
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import org.koin.java.KoinJavaComponent

/**
 * LEGACY COMPATIBILITY LAYER - TO BE REMOVED AFTER MIGRATION
 * 
 * This file provides temporary compatibility with the old WheelData singleton
 * by delegating to BleSessionViewModel. All references to WheelData should be
 * replaced with direct calls to BleSessionViewModel or BleSessionState.
 * 
 * THIS FILE SHOULD BE DELETED ONCE MIGRATION IS COMPLETE.
 */
@Deprecated("Use BleSessionViewModel directly. This compatibility layer will be removed.")
object WheelDataLegacy {
    
    private val viewModel: BleSessionViewModel by lazy {
        val app = KoinJavaComponent.get<Context>(Context::class.java).applicationContext
        BleSessionViewModel(app)
    }
    
    // ========== BASIC PROPERTIES ==========
    
    val speed: Int
        get() = viewModel.getLegacySpeed()
    
    val speedDouble: Double
        get() = viewModel.sessionState.value.currentSpeed
    
    val voltage: Int
        get() = viewModel.getLegacyVoltage()
    
    val voltageDouble: Double
        get() = viewModel.sessionState.value.currentVoltage
    
    val current: Int
        get() = viewModel.getLegacyCurrent()
    
    val currentDouble: Double
        get() = viewModel.sessionState.value.currentCurrent
    
    val temperature: Int
        get() = viewModel.getLegacyTemperature()
    
    val power: Int
        get() = viewModel.getLegacyPower()
    
    val powerDouble: Double
        get() = viewModel.sessionState.value.currentPower
    
    val batteryLevel: Int
        get() = viewModel.getLegacyBatteryLevel()
    
    val distance: Int
        get() = viewModel.getLegacyDistance()
    
    val totalDistance: Long
        get() = viewModel.getLegacyTotalDistance()
    
    val rideTime: Int
        get() = viewModel.getLegacyRideTime()
    
    val topSpeed: Int
        get() = viewModel.getLegacyTopSpeed()
    
    val topSpeedDouble: Double
        get() = viewModel.sessionState.value.topSpeed ?: 0.0
    
    val voltageSag: Int
        get() = viewModel.getLegacyVoltageSag()
    
    val batteryLowest: Int
        get() = viewModel.getLegacyBatteryLowest()
    
    // ========== DEVICE INFO ==========
    
    val mac: String
        get() = viewModel.getMac()
    
    val name: String
        get() = viewModel.getName()
    
    val model: String
        get() = viewModel.getModel()
    
    val version: String
        get() = viewModel.getVersion()
    
    val serial: String
        get() = viewModel.getSerial()
    
    val manufacturer: String
        get() = viewModel.getManufacturer()
    
    val wheelType: WHEEL_TYPE
        get() = manufacturer.toLegacyWheelType()
    
    // ========== STATE ==========
    
    var isConnected: Boolean
        get() = viewModel.isConnected()
        set(value) { /* Read-only, controlled by BLE */ }
    
    val isWheelReady: Boolean
        get() = viewModel.isWheelReady()
    
    val wheelAlarm: Boolean
        get() = viewModel.getWheelAlarm()
    
    // ========== GRAPH DATA ==========
    
    val xAxis: ArrayList<String>
        get() = viewModel.getXAxis()
    
    val currentAxis: ArrayList<Float>
        get() = viewModel.getCurrentAxis()
    
    val speedAxis: ArrayList<Float>
        get() = viewModel.getSpeedAxis()
    
    // ========== METHODS ==========
    
    fun resetMaxValues() {
        viewModel.resetMaxValues()
    }
    
    fun resetVoltageSag() {
        viewModel.resetVoltageSag()
    }
    
    fun resetUserDistance() {
        viewModel.resetUserDistance()
    }
    
    fun resetExtremumValues() {
        resetMaxValues()
        viewModel.resetVoltageSag()
    }
    
    fun full_reset() {
        viewModel.fullReset()
    }
    
    fun resetBmsData() {
        viewModel.resetBmsData()
    }
    
    // ========== COMPATIBILITY FOR EXISTING CODE ==========
    
    // These properties/methods are here to help existing code compile
    // They should be replaced with proper BleSessionViewModel usage
    
    @Deprecated("Use BleSessionViewModel.getEucBleClient()")
    var bluetoothService: Any? = null // BluetoothService is removed, use EucBleClient
    
    @Deprecated("Adapters are removed, use EucBleClient commands")
    val adapter: Any? = null // All brand adapters are removed
    
    @Deprecated("Use BleSessionViewModel directly")
    fun getInstance(): WheelDataLegacy = this
    
    // Additional properties for MainActivity compatibility
    var btName: String = ""
        get() = viewModel.getName()
        set(value) { field = value }
    
    val timeStamp: Long
        get() = viewModel.sessionState.value.lastDataTimestamp ?: System.currentTimeMillis()
    
    val phaseCurrentDouble: Double
        get() = viewModel.sessionState.value.lastData?.phaseCurrent ?: 0.0
    
    val calculatedPwm: Double
        get() = viewModel.sessionState.value.lastData?.pwm ?: 0.0
    
    val maxCurrentDouble: Double
        get() = viewModel.sessionState.value.sessionMaxCurrent ?: 0.0
    
    @Deprecated("Use BleSessionViewModel")
    fun initiate() {
        // No-op - initialization is handled by Koin
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
