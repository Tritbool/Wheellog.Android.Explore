package com.cooper.wheellog.ble

import com.cooper.wheellog.WheelDataLegacyLegacy
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import io.github.tritbool.euc.ble.models.EUCData
import io.github.tritbool.euc.ble.models.EUCDevice

/**
 * Extension functions to help migrate from WheelData to EUCData/EUCDevice.
 * These functions provide a compatibility layer during the migration process.
 */

// Convert EUCData to legacy WheelData values for compatibility
fun EUCData.toLegacySpeed(): Int {
    return (speed * 100).toInt() // Convert km/h to legacy format (speed * 100)
}

fun EUCData.toLegacyVoltage(): Int {
    return (voltage * 100).toInt() // Convert volts to legacy format (voltage * 100)
}

fun EUCData.toLegacyCurrent(): Int {
    return (current * 100).toInt() // Convert amps to legacy format (current * 100)
}

fun EUCData.toLegacyTemperature(): Int {
    return (temperature * 100).toInt() // Convert °C to legacy format (temp * 100)
}

fun EUCData.toLegacyPower(): Int {
    return (power * 100).toInt() // Convert watts to legacy format (power * 100)
}

fun EUCData.toLegacyDistance(): Long {
    return (distance * 1000).toLong() // Convert km to meters
}

fun EUCData.toLegacyTotalDistance(): Long {
    return (totalDistance ?: 0.0) * 1000).toLong() // Convert km to meters
}

fun EUCData.toLegacyWheelDistance(): Long {
    return (wheelDistance ?: 0.0) * 1000).toLong() // Convert km to meters
}

fun EUCData.toLegacyRideTime(): Int {
    return rideTime.toInt() // Convert seconds to int
}

// Convert EUCDevice to legacy wheel type
fun EUCDevice.toLegacyWheelType(): WHEEL_TYPE {
    return when (manufacturer.lowercase()) {
        "kingsong" -> WHEEL_TYPE.KINGSONG
        "gotway", "begode", "veteran", "leaperkim" -> WHEEL_TYPE.GOTWAY
        "inmotion" -> WHEEL_TYPE.INMOTION
        "ninebot" -> WHEEL_TYPE.NINEBOT
        else -> WHEEL_TYPE.Unknown
    }
}

// Helper function to get wheel type from manufacturer name
fun String.toLegacyWheelType(): WHEEL_TYPE {
    return when (this.lowercase()) {
        "kingsong" -> WHEEL_TYPE.KINGSONG
        "gotway", "begode", "veteran", "leaperkim" -> WHEEL_TYPE.GOTWAY
        "inmotion" -> WHEEL_TYPE.INMOTION
        "ninebot" -> WHEEL_TYPE.NINEBOT
        else -> WHEEL_TYPE.Unknown
    }
}

// Helper function to get manufacturer from wheel type
fun WHEEL_TYPE.toManufacturer(): String {
    return when (this) {
        WHEEL_TYPE.KINGSONG -> "KingSong"
        WHEEL_TYPE.GOTWAY -> "GotWay"
        WHEEL_TYPE.VETERAN -> "Veteran"
        WHEEL_TYPE.INMOTION -> "InMotion"
        WHEEL_TYPE.INMOTION_V2 -> "InMotion"
        WHEEL_TYPE.NINEBOT -> "Ninebot"
        WHEEL_TYPE.NINEBOT_Z -> "Ninebot"
        else -> "Unknown"
    }
}

// Extension to use WheelDataLegacy instead of WheelData
fun getWheelDataLegacy() = WheelDataLegacy

// Create a WheelData-like interface for BleSessionState for easier migration
fun BleSessionState.getSpeed(): Int {
    return (currentSpeed * 100).toInt()
}

fun BleSessionState.getVoltage(): Int {
    return (currentVoltage * 100).toInt()
}

fun BleSessionState.getCurrent(): Int {
    return (currentCurrent * 100).toInt()
}

fun BleSessionState.getTemperature(): Int {
    return (currentTemperature * 100).toInt()
}

fun BleSessionState.getPower(): Int {
    return (currentPower * 100).toInt()
}

fun BleSessionState.getBatteryLevel(): Int {
    return batteryLevel
}

fun BleSessionState.getDistance(): Int {
    return (wheelDistance ?: 0.0 * 1000).toInt()
}

fun BleSessionState.getTotalDistance(): Long {
    return (totalDistance ?: 0.0 * 1000).toLong()
}

fun BleSessionState.getRideTime(): Int {
    return rideTime?.toInt() ?: 0
}

fun BleSessionState.getTopSpeed(): Int {
    return (topSpeed ?: 0.0 * 100).toInt()
}

fun BleSessionState.getMac(): String {
    return deviceAddress
}

fun BleSessionState.getName(): String {
    return deviceName
}

fun BleSessionState.getModel(): String {
    return deviceModel
}

fun BleSessionState.getVersion(): String {
    return firmwareVersion ?: "Unknown"
}

fun BleSessionState.getSerial(): String {
    return serialNumber ?: "Unknown"
}

fun BleSessionState.getWheelType(): WHEEL_TYPE {
    return deviceManufacturer.toLegacyWheelType()
}

fun BleSessionState.isWheelConnected(): Boolean {
    return isConnected
}

// Check if speed is low (for safety checks)
fun BleSessionState.isSpeedLow(threshold: Int = 1): Boolean {
    return currentSpeed < threshold
}

// Check if wheel is ready (has received data)
fun BleSessionState.isWheelReady(): Boolean {
    return lastData != null && lastDataTimestamp != null && 
           (System.currentTimeMillis() - lastDataTimestamp!!) < 5000 // Data received in last 5 seconds
}
