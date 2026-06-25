package com.cooper.wheellog.ble

import com.cooper.wheellog.utils.Constants.WHEEL_TYPE

/**
 * Extension functions for converting between manufacturer names and legacy WHEEL_TYPE enum.
 * These functions are used during the migration from WheelData to EucBleManager.
 */

fun String.toLegacyWheelType(): WHEEL_TYPE {
    return when (this.lowercase()) {
        "kingsong" -> WHEEL_TYPE.KINGSONG
        "gotway", "begode", "veteran", "leaperkim" -> WHEEL_TYPE.GOTWAY
        "inmotion" -> WHEEL_TYPE.INMOTION
        "ninebot" -> WHEEL_TYPE.NINEBOT
        else -> WHEEL_TYPE.Unknown
    }
}

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
