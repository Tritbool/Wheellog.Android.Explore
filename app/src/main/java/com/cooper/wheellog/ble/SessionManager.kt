package com.cooper.wheellog.ble

import io.github.tritbool.euc.ble.models.EUCData
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages session statistics that are tracked across the application.
 * This includes max values, trip distances, etc.
 * 
 * This is a separate manager from EucBleManager because session statistics
 * are application-level state, not BLE device state.
 */
class SessionManager(private val eucBleManager: EucBleManager) {
    
    // Session statistics (max values tracking)
    private var _sessionMaxCurrent: Double = 0.0
    private var _sessionMaxPhaseCurrent: Double = 0.0
    private var _sessionMaxPower: Double = 0.0
    private var _sessionMaxPwm: Double = 0.0
    private var _sessionMaxTemperature: Int = 0
    private var _sessionTopSpeed: Double = 0.0
    private var _sessionStartTotalDistance: Double = 0.0
    private var _sessionStartDistance: Double = 0.0
    private var _sessionBatteryLowest: Int = 101
    
    val eucData: StateFlow<EUCData?> = eucBleManager.eucData
    
    // Session statistics accessors
    val sessionMaxCurrent: Double get() = _sessionMaxCurrent
    val sessionMaxPhaseCurrent: Double get() = _sessionMaxPhaseCurrent
    val sessionMaxPower: Double get() = _sessionMaxPower
    val sessionMaxPwm: Double get() = _sessionMaxPwm
    val sessionMaxTemperature: Int get() = _sessionMaxTemperature
    val sessionTopSpeed: Double get() = _sessionTopSpeed
    val sessionBatteryLowest: Int get() = _sessionBatteryLowest
    
    init {
        // Start observing eucData changes to update session max values
        eucBleManager.eucData.value?.let { data ->
            updateSessionMaxValues(data)
        }
    }
    
    /**
     * Update session max values based on new EUCData
     */
    fun updateSessionMaxValues(data: EUCData) {
        data.speed?.let { speed ->
            if (speed > _sessionTopSpeed) _sessionTopSpeed = speed
        }
        data.pwm?.let { pwm ->
            if (pwm > _sessionMaxPwm) _sessionMaxPwm = pwm
        }
        data.current?.let { current ->
            if (current > _sessionMaxCurrent) _sessionMaxCurrent = current
        }
        data.phaseCurrent?.let { phaseCurrent ->
            if (phaseCurrent > _sessionMaxPhaseCurrent) _sessionMaxPhaseCurrent = phaseCurrent
        }
        data.power?.let { power ->
            if (power > _sessionMaxPower) _sessionMaxPower = power
        }
        data.temperature?.let { temp ->
            val tempInt = (temp * 100).toInt()
            if (tempInt > _sessionMaxTemperature) _sessionMaxTemperature = tempInt
        }
        data.batteryLevel?.let { battery ->
            if (battery < _sessionBatteryLowest) _sessionBatteryLowest = battery
        }
    }
    
    /**
     * Reset all max values (session statistics)
     */
    fun resetMaxValues() {
        _sessionMaxCurrent = 0.0
        _sessionMaxPhaseCurrent = 0.0
        _sessionMaxPower = 0.0
        _sessionMaxPwm = 0.0
        _sessionMaxTemperature = 0
        _sessionTopSpeed = 0.0
    }
    
    /**
     * Reset battery lowest value
     */
    fun resetBatteryLowest() {
        _sessionBatteryLowest = 101
    }
    
    /**
     * Reset user distance tracking
     */
    fun resetUserDistance() {
        _sessionStartDistance = eucBleManager.eucData.value?.totalDistance ?: 0.0
    }
    
    /**
     * Reset voltage sag - not applicable in new architecture
     */
    fun resetVoltageSag() {
        // Voltage sag is not tracked in the new architecture
        // This is a no-op for compatibility
    }
    
    /**
     * Full reset of all session statistics
     */
    fun fullReset() {
        resetMaxValues()
        resetBatteryLowest()
        resetUserDistance()
        resetVoltageSag()
    }
}
