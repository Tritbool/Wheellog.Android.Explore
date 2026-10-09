package com.cooper.wheellog.utils

object Constants {
    const val ACTION_LOGGING_SERVICE_TOGGLED = "com.cooper.wheellog.loggingServiceToggled"
    const val ACTION_PREFERENCE_RESET = "com.cooper.wheellog.preferenceReset"
    const val ACTION_ALARM_TRIGGERED = "com.cooper.wheellog.alarmTriggered"
    const val ACTION_WHEEL_MODEL_CHANGED = "com.cooper.wheellog.wheelModelChanged"
    const val NOTIFICATION_BUTTON_CONNECTION = "com.cooper.wheellog.notificationConnectionButton"
    const val NOTIFICATION_BUTTON_LOGGING = "com.cooper.wheellog.notificationLoggingButton"
    const val NOTIFICATION_BUTTON_BEEP = "com.cooper.wheellog.notificationBeepButton"
    const val NOTIFICATION_BUTTON_LIGHT = "com.cooper.wheellog.notificationLightButton"
    const val NOTIFICATION_CHANNEL_ID_NOTIFICATION = "com.cooper.wheellog.Channel_Notification"
    const val notificationChannelName = "Notify"
    const val notificationChannelDescription = "Default Notify"
    const val INTENT_EXTRA_LOGGING_FILE_LOCATION = "logging_file_location"
    const val INTENT_EXTRA_IS_RUNNING = "is_running"
    const val INTENT_EXTRA_ALARM_TYPE = "alarm_type"
    const val INTENT_EXTRA_ALARM_VALUE = "alarm_value"
    const val MAIN_NOTIFICATION_ID = 423411
    const val LOG_FOLDER_NAME = "WheelLog"
    const val LOG_FOLDER_OLD_NAME = "WheelLog Logs"

    enum class WHEEL_TYPE {
        Unknown, KINGSONG, GOTWAY, NINEBOT, NINEBOT_Z, INMOTION, INMOTION_V2, VETERAN
    }

    fun wheel_type_from_string(wt: String): WHEEL_TYPE {
        return when (wt.lowercase()) {
            "kingsong" -> WHEEL_TYPE.KINGSONG
            "gotway", "gw", "begode", "eb", "extreme bull", "extreme_bull" -> WHEEL_TYPE.GOTWAY
            "ninebot", "segway" -> WHEEL_TYPE.NINEBOT
            "ninebot_z", "ninebotz" -> WHEEL_TYPE.NINEBOT_Z
            "inmotion" -> WHEEL_TYPE.INMOTION
            "leaperkim", "veteran" -> WHEEL_TYPE.VETERAN
            else -> WHEEL_TYPE.Unknown
        }
    }

    enum class ALARM_TYPE(val value: Int) {
        SPEED1(1),
        SPEED2(2),
        SPEED3(3),
        CURRENT(4),
        TEMPERATURE(5),
        PWM(6),
        BATTERY(7),
        WHEEL(8);
    }
}