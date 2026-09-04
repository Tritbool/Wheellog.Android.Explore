package com.cooper.wheellog.ble

import com.cooper.wheellog.utils.Constants.wheel_type_from_string
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.models.EUCData
import io.github.tritbool.euc.ble.models.EUCDevice
import io.github.tritbool.euc.ble.protocols.EUCProtocol

/**
 * State class representing the current BLE session state for the UI.
 * This replaces the legacy WheelData singleton pattern with a modern
 * state-based approach that can be observed by the UI.
 */
data class BleSessionState(
    // Connection state
    val connectionState: BLEConstants.ConnectionState = BLEConstants.ConnectionState.DISCONNECTED,

    // Currently connected device
    val selectedDevice: EUCDevice? = null,

    // Latest telemetry data
    val lastData: EUCData? = null,

    // Latest error message
    val lastError: String? = null,

    // List of discovered devices during scanning
    val scanResults: List<EUCDevice> = emptyList(),

    // Whether a scan is currently in progress
    val isScanning: Boolean = false,

    // Session statistics (derived from EUCData)
    val sessionTopSpeed: Double? = null,
    val sessionMaxPower: Double? = null,
    val sessionMaxCurrent: Double? = null,
    val sessionMaxPwm: Double? = null,
    val sessionMaxTemperature: Double? = null,
    val sessionBatteryLowest: Int? = null,
    val sessionRidingTimeSec: Long? = null,
    val sessionDistance: Double? = null,
    val sessionRideTime: Long? = null,

    // Timestamp of last data update
    val lastDataTimestamp: Long? = null,

    // Protocol selection — populated when auto-detection fails in AUTO_WITH_MANUAL_FALLBACK mode
    val protocolSelectionRequired: Boolean = false,
    val protocolCandidates: List<EUCProtocol> = emptyList()
) {
    // Helper properties for UI binding
    val isConnected: Boolean
        get() = connectionState == BLEConstants.ConnectionState.CONNECTED

    val deviceName: String
        get() = selectedDevice?.name ?: "Unknown"

    val deviceAddress: String
        get() = selectedDevice?.address ?: ""

    val deviceModel: String
        get() = lastData?.model ?: selectedDevice?.name ?: "Unknown"

    val deviceManufacturer: String
        get() = lastData?.manufacturer ?: "Unknown"

    val currentSpeed: Double
        get() = lastData?.speed ?: 0.0

    val currentVoltage: Double
        get() = lastData?.voltage ?: 0.0

    val currentCurrent: Double
        get() = lastData?.current ?: 0.0

    val currentTemperature: Double
        get() = lastData?.temperature ?: 0.0

    val batteryLevel: Int
        get() = lastData?.batteryLevel ?: 0

    val currentPower: Double
        get() = lastData?.power ?: 0.0

    val pwm: Double?
        get() = lastData?.pwm

    val currentPressure: Double
        get() = lastData?.tirePressureKpa ?: -1.0

    val isCharging: Boolean
        get() = lastData?.isCharging ?: false

    val totalDistance: Double?
        get() = lastData?.totalDistance

    val wheelDistance: Double?
        get() = lastData?.wheelDistance

    val rideTime: Long?
        get() = lastData?.rideTime

    val motorTemperature: Double?
        get() = lastData?.motorTemperature

    val imuTemperature: Double?
        get() = lastData?.imuTemperature

    val cpuLoad: Int?
        get() = lastData?.cpuLoad

    val speedLimit: Double?
        get() = lastData?.speedLimit

    val fanStatus: Int?
        get() = lastData?.fanStatus

    val chargingStatus: Int?
        get() = lastData?.chargingStatus

    val angle: Double?
        get() = lastData?.angle

    val wheelAlarm: Boolean?
        get() = lastData?.wheelAlarm

    val topSpeed: Double?
        get() = lastData?.topSpeed ?: sessionTopSpeed

    val firmwareVersion: String?
        get() = lastData?.firmwareVersion

    val serialNumber: String?
        get() = lastData?.serialNumber

    val cellVoltages: List<Double>?
        get() = lastData?.cellVoltages


    val pedalsMode: Int?
        get() = lastData?.pedalsMode

    val alarmMode: Int?
        get() = lastData?.alarmMode

    val rollAngleMode: Int?
        get() = lastData?.rollAngleMode

    val ledMode: Int?
        get() = lastData?.ledMode

    val lightMode: Int?
        get() = lastData?.lightMode

    val alertFlags: Int?
        get() = lastData?.alertFlags

    val usesMiles: Boolean?
        get() = lastData?.usesMiles

    val autoPowerOffMinutes: Int?
        get() = lastData?.autoPowerOffMinutes

    val tiltBackSpeed: Int?
        get() = lastData?.tiltBackSpeed

    val wheelMaxSpeed: Int?
        get() = lastData?.wheelMaxSpeed

    val alarm1Speed: Int?
        get() = lastData?.alarm1Speed

    val alarm2Speed: Int?
        get() = lastData?.alarm2Speed

    val alarm3Speed: Int?
        get() = lastData?.alarm3Speed

    companion object {
        // Initial empty state
        val EMPTY = BleSessionState()
    }
}

fun BleSessionState.getWheelType(): com.cooper.wheellog.utils.Constants.WHEEL_TYPE =
    wheel_type_from_string(deviceManufacturer)