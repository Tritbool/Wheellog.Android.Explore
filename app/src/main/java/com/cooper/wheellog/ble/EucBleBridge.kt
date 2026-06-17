package com.cooper.wheellog.ble

import android.content.Context
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.WheelData
import com.cooper.wheellog.utils.*
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import com.euc.ble.EucBleClient
import com.euc.ble.core.ConnectionCallback
import com.euc.ble.core.DataCallback
import com.euc.ble.core.ErrorCallback
import com.euc.ble.exceptions.BLEException
import com.euc.ble.models.EUCData
import com.euc.ble.models.EUCDevice
import com.euc.ble.protocols.CommandType
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridge between the new euc_ble_library and the existing Wheellog adapter system.
 * 
 * This bridge allows gradual migration by:
 * 1. Running both new and legacy adapters in parallel
 * 2. Comparing outputs to validate the new library
 * 3. Providing a feature flag to switch between modes
 * 4. Logging discrepancies for debugging
 * 
 * Usage:
 * - Set AppConfig.useNewBleLibrary = true to use the new library
 * - Set AppConfig.useNewBleLibrary = false to use legacy adapters
 * - Set AppConfig.bleComparisonMode = true to run both and compare
 */
class EucBleBridge private constructor() : BaseAdapter() {
    
    companion object {
        private var instance: EucBleBridge? = null
        private val isInitialized = AtomicBoolean(false)
        
        fun getInstance(): EucBleBridge {
            if (instance == null) {
                instance = EucBleBridge()
            }
            return instance!!
        }
        
        fun resetInstance() {
            instance?.cleanup()
            instance = null
            isInitialized.set(false)
        }
    }
    
    private val appConfig: AppConfig by KoinJavaComponent.inject(AppConfig::class.java)
    private var eucBleClient: EucBleClient? = null
    private var context: Context? = null
    private var lastEucData: EUCData? = null
    private var lastLegacyData: Boolean = false
    
    // Legacy adapters (for comparison mode)
    private val legacyAdapters: Map<String, BaseAdapter> by lazy {
        mapOf(
            "KINGSONG" to KingsongAdapter.getInstance(),
            "GOTWAY" to GotwayAdapter.getInstance(),
            "INMOTION" to InMotionAdapter.getInstance(),
            "INMOTION_V2" to InmotionAdapterV2.getInstance(),
            "NINEBOT" to NinebotAdapter.getInstance(),
            "NINEBOT_Z" to NinebotZAdapter.getInstance(),
            "VETERAN" to VeteranAdapter.getInstance()
        )
    }
    
    /**
     * Initialize the bridge with a context
     */
    fun initialize(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            this.context = context.applicationContext
            
            try {
                eucBleClient = EucBleClient(context)
                setupCallbacks()
                eucBleClient?.initialize()
                Timber.i("EUC BLE Bridge initialized with new library")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize EUC BLE library")
                eucBleClient = null
            }
        }
    }
    
    private fun setupCallbacks() {
        eucBleClient?.setConnectionCallback(object : ConnectionCallback() {
            override fun onScanStarted() {
                Timber.d("EUC BLE: Scan started")
            }
            
            override fun onDeviceDiscovered(device: EUCDevice) {
                Timber.d("EUC BLE: Device discovered - ${device.name} (${device.address})")
            }
            
            override fun onConnected() {
                Timber.d("EUC BLE: Connected")
            }
            
            override fun onDisconnected() {
                Timber.d("EUC BLE: Disconnected")
            }
        })
        
        eucBleClient?.setDataCallback(object : DataCallback {
            override fun onDataReceived(data: EUCData) {
                lastEucData = data
                updateWheelDataFromEUCData(data)
                
                // In comparison mode, also log for validation
                if (appConfig.bleComparisonMode) {
                    logComparisonData("NEW_LIB", data)
                }
            }
        })
        
        eucBleClient?.setErrorCallback(object : ErrorCallback {
            override fun onError(error: BLEException) {
                Timber.e("EUC BLE Error: ${error.message}")
            }
        })
    }
    
    /**
     * Decode data using the appropriate system based on configuration
     */
    override fun decode(data: ByteArray?): Boolean {
        if (data == null || data.isEmpty()) return false
        
        val wd = WheelData.getInstance()
        val wheelType = wd.wheelType.toString()
        
        // Always try legacy for now to maintain compatibility
        val legacyResult = try {
            getLegacyAdapter(wheelType)?.decode(data) ?: false
        } catch (e: Exception) {
            Timber.e(e, "Legacy adapter decode failed")
            false
        }
        
        // Try new library if enabled
        val newLibResult = if (appConfig.useNewBleLibrary) {
            try {
                // Feed data to the new library
                // It will call our DataCallback asynchronously
                eucBleClient?.decode(data)
                // Return true to indicate we're processing it
                // Actual data will come via callback
                true
            } catch (e: Exception) {
                Timber.e(e, "New library decode failed")
                false
            }
        } else {
            false
        }
        
        lastLegacyData = legacyResult
        
        // In comparison mode, log both results
        if (appConfig.bleComparisonMode) {
            logComparisonResult(wheelType, data, legacyResult, newLibResult)
        }
        
        // Return true if either system processed data
        return legacyResult || newLibResult
    }
    
    /**
     * Get the appropriate legacy adapter for the wheel type
     */
    private fun getLegacyAdapter(wheelType: String?): BaseAdapter? {
        if (wheelType == null) return null
        
        return when (wheelType.uppercase()) {
            "KINGSONG" -> KingsongAdapter.getInstance()
            "GOTWAY" -> GotwayAdapter.getInstance()
            "GOTWAY_VIRTUAL" -> GotwayVirtualAdapter.getInstance()
            "INMOTION" -> InMotionAdapter.getInstance()
            "INMOTION_V2" -> InmotionAdapterV2.getInstance()
            "NINEBOT" -> NinebotAdapter.getInstance()
            "NINEBOT_Z" -> NinebotZAdapter.getInstance()
            "VETERAN" -> VeteranAdapter.getInstance()
            else -> null
        }
    }
    
    /**
     * Update WheelData from EUCData
     */
    private fun updateWheelDataFromEUCData(data: EUCData) {
        val wd = WheelData.getInstance()
        
        // Only update if we're using the new library or in comparison mode
        if (!appConfig.useNewBleLibrary && !appConfig.bleComparisonMode) {
            return
        }
        
        // Map EUCData to WheelData fields
        wd.setSpeed(data.speed)
        wd.setVoltage(data.voltage)
        wd.setCurrent(data.current)
        wd.setTemperature(data.temperature)
        wd.setBatteryLevel(data.batteryLevel)
        
        // Distance - check if it's trip or total
        if (data.distance > 0) {
            wd.setDistance(data.distance)
        }
        
        wd.setPower(data.power)
        
        // PWM output
        data.pwm?.let { pwm ->
            wd.setOutput(pwm)
        }
        
        // Model and manufacturer
        if (data.model.isNotEmpty()) {
            wd.setModel(data.model)
        }
        if (data.firmwareVersion != null) {
            wd.setVersion(data.firmwareVersion)
        }
        if (data.serialNumber != null) {
            wd.setSerial(data.serialNumber)
        }
        
        // Timestamps - use available setters/getters
        // Note: timestamp_raw and mLastLifeData are private fields in WheelData.java
        // We'll use the available getTimeStamp() and getLastLifeData() methods
        // For now, we'll skip setting these directly as they're managed internally
        
        // Additional fields
        data.topSpeed?.let { wd.setTopSpeed(it) }
        data.motorTemperature?.let { wd.setTemperature2(it) }
        data.totalDistance?.let { wd.setTotalDistance(it) }
        
        // Note: Pedals mode, alarm mode, roll angle mode, LED mode, light mode
        // are not directly available in WheelData.java, so we skip them for now
        // These would need to be mapped to the appropriate legacy adapter methods
        
        // Fan status
        data.fanStatus?.let { wd.setFanStatus(it) }
        
        // Charging status
        data.chargingStatus?.let { wd.setChargingStatus(it) }
        
        // Temperature 2
        data.temperature2?.let { wd.setTemperature2(it) }
        
        // CPU load
        data.cpuLoad?.let { wd.setCpuLoad(it) }
        
        // Speed limit
        data.speedLimit?.let { wd.setSpeedLimit(it) }
        
        // Note: Alarm speeds and wheel max speed are not directly available in WheelData.java
        // These would need to be mapped to the appropriate legacy adapter methods
        
        // Wheel distance (trip)
        data.wheelDistance?.let { wd.setWheelDistance(it) }
        
        // Angle
        data.angle?.let { wd.setAngle(it) }
        
        // Note: Cell voltages are not directly available in WheelData.java
        // These would need to be mapped to the appropriate BMS handling
        
        Timber.d("EUC BLE: Updated WheelData - Speed: ${data.speed}, Voltage: ${data.voltage}, Model: ${data.model}")
    }
    
    /**
     * Log comparison data for validation
     */
    private fun logComparisonData(source: String, data: EUCData) {
        Timber.d("[$source] Speed: ${data.speed}, Voltage: ${data.voltage}, Current: ${data.current}, " +
                "Temp: ${data.temperature}, Battery: ${data.batteryLevel}, Model: ${data.model}")
    }
    
    /**
     * Log comparison results
     */
    private fun logComparisonResult(wheelType: String?, data: ByteArray, legacyResult: Boolean, newLibResult: Boolean) {
        val hexData = data.joinToString("") { "%02X".format(it) }
        Timber.d("[COMPARISON] Type: $wheelType, Legacy: $legacyResult, NewLib: $newLibResult, Data: $hexData")
    }
    
    // Command implementations - delegate to appropriate system
    
    override fun setLightState(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            val cmd = if (on) CommandType.LIGHT_ON else CommandType.LIGHT_OFF
            eucBleClient?.sendCommand(cmd)
            Timber.d("EUC BLE: Sending light command - ${if (on) "ON" else "OFF"}")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLightState(on)
        }
    }
    
    override fun setLedState(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            // Map to appropriate command
            // Note: New library may not have direct LED state command
            // This needs to be mapped based on the protocol
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLedState(on)
        }
    }
    
    override fun wheelBeep() {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.BEEP)
            Timber.d("EUC BLE: Sending beep command")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.wheelBeep()
        }
    }
    
    override fun powerOff() {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.POWER_OFF)
            Timber.d("EUC BLE: Sending power off command")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.powerOff()
        }
    }
    
    override fun setLightBrightness(value: Int) {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.LIGHT_BRIGHTNESS, value)
            Timber.d("EUC BLE: Sending light brightness command - $value")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLightBrightness(value)
        }
    }
    
    override fun updatePedalsMode(pedalsMode: Int) {
        if (appConfig.useNewBleLibrary) {
            // Map pedals mode to appropriate command
            // This may need protocol-specific handling
            Timber.w("EUC BLE: updatePedalsMode not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.updatePedalsMode(pedalsMode)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.updatePedalsMode(pedalsMode)
        }
    }
    
    override fun setRollAngleMode(rollAngleMode: Int) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setRollAngleMode not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setRollAngleMode(rollAngleMode)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setRollAngleMode(rollAngleMode)
        }
    }
    
    override fun setMilesMode(milesMode: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setMilesMode not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setMilesMode(milesMode)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setMilesMode(milesMode)
        }
    }
    
    override fun setLightMode(lightMode: Int) {
        if (appConfig.useNewBleLibrary) {
            // Map light mode to appropriate command
            eucBleClient?.sendCommand(CommandType.SET_LIGHT_MODE, lightMode)
            Timber.d("EUC BLE: Sending light mode command - $lightMode")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLightMode(lightMode)
        }
    }
    
    override fun setLedMode(ledMode: Int) {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.SET_LED_MODE, ledMode)
            Timber.d("EUC BLE: Sending LED mode command - $ledMode")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLedMode(ledMode)
        }
    }
    
    override fun setTailLightState(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setTailLightState not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setTailLightState(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setTailLightState(on)
        }
    }
    
    override fun setHandleButtonState(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setHandleButtonState not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setHandleButtonState(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setHandleButtonState(on)
        }
    }
    
    override fun setBrakeAssist(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setBrakeAssist not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setBrakeAssist(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setBrakeAssist(on)
        }
    }
    
    override fun setLedColor(value: Int, ledNum: Int) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setLedColor not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLedColor(value, ledNum)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLedColor(value, ledNum)
        }
    }
    
    override fun setAlarmEnabled(on: Boolean, num: Int) {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.SET_ALARM_SPEED, num)
            Timber.d("EUC BLE: Sending alarm enabled command - $on, num: $num")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setAlarmEnabled(on, num)
        }
    }
    
    override fun setLimitedModeEnabled(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.SET_SPEED_LIMIT, if (on) 1 else 0)
            Timber.d("EUC BLE: Sending limited mode command - $on")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLimitedModeEnabled(on)
        }
    }
    
    override fun setLimitedSpeed(value: Int) {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.SET_SPEED_LIMIT, value)
            Timber.d("EUC BLE: Sending limited speed command - $value")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLimitedSpeed(value)
        }
    }
    
    override fun setAlarmSpeed(value: Int, num: Int) {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.SET_ALARM_SPEED, value)
            Timber.d("EUC BLE: Sending alarm speed command - $value, num: $num")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setAlarmSpeed(value, num)
        }
    }
    
    override fun setRideMode(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setRideMode not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setRideMode(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setRideMode(on)
        }
    }
    
    override fun setLockMode(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.LOCK, if (on) 1 else 0)
            Timber.d("EUC BLE: Sending lock mode command - $on")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setLockMode(on)
        }
    }
    
    override fun setTransportMode(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setTransportMode not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setTransportMode(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setTransportMode(on)
        }
    }
    
    override fun setDrl(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setDrl not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setDrl(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setDrl(on)
        }
    }
    
    override fun setGoHomeMode(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setGoHomeMode not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setGoHomeMode(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setGoHomeMode(on)
        }
    }
    
    override fun setFancierMode(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setFancierMode not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setFancierMode(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setFancierMode(on)
        }
    }
    
    override fun setMute(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setMute not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setMute(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setMute(on)
        }
    }
    
    override fun setFanQuiet(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setFanQuiet not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setFanQuiet(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setFanQuiet(on)
        }
    }
    
    override fun setFan(on: Boolean) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setFan not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setFan(on)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setFan(on)
        }
    }
    
    override fun switchFlashlight() {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: switchFlashlight not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.switchFlashlight()
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.switchFlashlight()
        }
    }
    
    override fun updateMaxSpeed(wheelMaxSpeed: Int) {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.SET_SPEED_LIMIT, wheelMaxSpeed)
            Timber.d("EUC BLE: Sending max speed command - $wheelMaxSpeed")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.updateMaxSpeed(wheelMaxSpeed)
        }
    }
    
    override fun setSpeakerVolume(speakerVolume: Int) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setSpeakerVolume not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setSpeakerVolume(speakerVolume)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setSpeakerVolume(speakerVolume)
        }
    }
    
    override fun setPedalTilt(angle: Int) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setPedalTilt not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setPedalTilt(angle)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setPedalTilt(angle)
        }
    }
    
    override fun setPedalSensivity(sensivity: Int) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: setPedalSensivity not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setPedalSensivity(sensivity)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.setPedalSensivity(sensivity)
        }
    }
    
    override fun wheelCalibration() {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.CALIBRATE)
            Timber.d("EUC BLE: Sending calibration command")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.wheelCalibration()
        }
    }
    
    override fun updateLedMode(ledMode: Int) {
        if (appConfig.useNewBleLibrary) {
            eucBleClient?.sendCommand(CommandType.SET_LED_MODE, ledMode)
            Timber.d("EUC BLE: Sending LED mode update command - $ledMode")
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.updateLedMode(ledMode)
        }
    }
    
    override fun updateStrobeMode(strobeMode: Int) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: updateStrobeMode not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.updateStrobeMode(strobeMode)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.updateStrobeMode(strobeMode)
        }
    }
    
    override fun updateAlarmMode(alarmMode: Int) {
        if (appConfig.useNewBleLibrary) {
            Timber.w("EUC BLE: updateAlarmMode not yet mapped to new library")
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.updateAlarmMode(alarmMode)
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.updateAlarmMode(alarmMode)
        }
    }
    
    override val ledModeString: String?
        get() = getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.ledModeString
    
    override fun getLedIsAvailable(ledNum: Int): Boolean {
        return getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.getLedIsAvailable(ledNum) ?: false
    }
    
    override val cellsForWheel: Int
        get() = getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.cellsForWheel ?: 1
    
    override val isReady: Boolean
        get() = if (appConfig.useNewBleLibrary) {
            eucBleClient != null
        } else {
            getLegacyAdapter(WheelData.getInstance().wheelType.toString())?.isReady ?: false
        }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        eucBleClient?.cleanup()
        eucBleClient = null
        context = null
    }
    
    /**
     * Get the underlying EucBleClient for advanced usage
     */
    fun getEucBleClient(): EucBleClient? {
        return eucBleClient
    }
}
