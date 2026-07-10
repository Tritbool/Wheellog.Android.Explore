package com.cooper.wheellog

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
import androidx.preference.PreferenceManager
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.utils.NotificationUtil
import com.cooper.wheellog.utils.ThemeEnum
import com.cooper.wheellog.utils.VolumeKeyController
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppConfig(var context: Context) : KoinComponent {
    private val notifications: NotificationUtil by inject()
    private val volumeKeyController: VolumeKeyController by inject()
    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private var specificPrefix: String = ""
    private val separator = ";"
    private val viewModel: BleSessionViewModel by inject()

    init {
        // Clear all preferences if they are incompatible
        val version = getValue("versionSettings", -1)
        val currentVer = 1
        if (version < currentVer && sharedPreferences.edit()?.clear()?.commit() == true) {
            setValue("versionSettings", currentVer)
            PreferenceManager.setDefaultValues(context, R.xml.preferences, false)
        }
    }

    //region -=[ general settings ]=-    
    //region application
    var useEng: Boolean
        get() = getValue(R.string.use_eng, false)
        set(value) = setValue(R.string.use_eng, value)

    var appThemeInt: Int
        get() = getValue(R.string.app_theme, ThemeEnum.Original.value.toString()).toInt()
        set(value) = setValue(R.string.app_theme, value.toString())

    val appTheme: Int
        get() {
            val stringVal = getValue(R.string.app_theme, ThemeEnum.Original.value.toString())
            return when (ThemeEnum.fromInt(stringVal.toInt())) {
                ThemeEnum.AJDM -> R.style.AJDMTheme
                else -> R.style.OriginalTheme
            }
        }

    var useComposeUI: Boolean = false

    var dayNightThemeMode: Int
        get() = getValue(R.string.day_night_theme, MODE_NIGHT_UNSPECIFIED.toString()).toInt()
        set(value) = setValue(R.string.day_night_theme, value.toString())

    var useBetterPercents: Boolean
        get() = getValue(R.string.use_better_percents, false)
        set(value) = setValue(R.string.use_better_percents, value)

    var customPercents: Boolean
        get() = getValue(R.string.custom_percents, false)
        set(value) = setValue(R.string.custom_percents, value)

    var cellVoltageTiltback: Int
        get() = getSpecific(R.string.cell_voltage_tiltback, 330)
        set(value) = setSpecific(R.string.cell_voltage_tiltback, value)

    var useMph: Boolean
        get() = getValue(R.string.use_mph, false)
        set(value) = setValue(R.string.use_mph, value)

    var useFahrenheit: Boolean
        get() = getValue(R.string.use_fahrenheit, false)
        set(value) = setValue(R.string.use_fahrenheit, value)

    private var viewBlocksString: String?
        get() = getValue(R.string.view_blocks_string, null)
        set(value) = setValue(R.string.view_blocks_string, value)

    var viewBlocks: Array<String>
        get() = this.viewBlocksString?.split(separator)?.toTypedArray()
            ?: arrayOf(
                context.getString(R.string.pwm),
                context.getString(R.string.max_pwm),
                context.getString(R.string.voltage),
                context.getString(R.string.average_riding_speed),
                context.getString(R.string.riding_time),
                context.getString(R.string.top_speed),
                context.getString(R.string.distance),
                context.getString(R.string.total)
            )
        set(value) {
            this.viewBlocksString = value.joinToString(separator)
        }

    var usePipMode: Boolean
        get() = getValue(R.string.use_pip_mode, false)
        set(value) = setValue(R.string.use_pip_mode, value)

    var pipBlock: String
        get() = getValue(R.string.pip_block, "")
        set(value) = setValue(R.string.pip_block, value)

    private var notificationButtonsString: String?
        get() = getValue(R.string.notification_buttons, null)
        set(value) {
            setValue(R.string.notification_buttons, value)
            notifications.update()
        }

    var notificationButtons: Array<String>
        get() = this.notificationButtonsString?.split(separator)?.toTypedArray()
            ?: arrayOf(
                context.getString(R.string.icon_connection),
                context.getString(R.string.icon_logging),
                context.getString(R.string.icon_watch)
            )
        set(value) {
            this.notificationButtonsString = value.joinToString(separator)
        }

    var maxSpeed: Int
        get() = getValue(R.string.max_speed, 50)
        set(value) = setValue(R.string.max_speed, value)

    var valueOnDial: String
        get() = getValue(R.string.value_on_dial, "0")
        set(value) = setValue(R.string.value_on_dial, value)

    var pageGraph: Boolean
        get() = getValue(R.string.show_page_graph, true)
        set(value) = setValue(R.string.show_page_graph, value)

    var pageEvents: Boolean
        get() = getValue(R.string.show_page_events, false)
        set(value) = setValue(R.string.show_page_events, value)

    var pageTrips: Boolean
        get() = getValue(R.string.show_page_trips, true)
        set(value) = setValue(R.string.show_page_trips, value)

    var connectionSound: Boolean
        get() = getValue(R.string.connection_sound, false)
        set(value) = setValue(R.string.connection_sound, value)

    var noConnectionSound: Int
        get() = getValue(R.string.no_connection_sound, 5)
        set(value) = setValue(R.string.no_connection_sound, value)

    var useStopMusic: Boolean
        get() = getValue(R.string.use_stop_music, false)
        set(value) = setValue(R.string.use_stop_music, value)

    var showUnknownDevices: Boolean
        get() = getValue(R.string.show_unknown_devices, false)
        set(value) = setValue(R.string.show_unknown_devices, value)

    var useBeepOnSingleTap: Boolean
        get() = getValue(R.string.beep_on_single_tap, false)
        set(value) = setValue(R.string.beep_on_single_tap, value)

    var useBeepOnVolumeUp: Boolean
        get() = getValue(R.string.beep_on_volume_up, false)
        set(value) {
            setValue(R.string.beep_on_volume_up, value)
            volumeKeyController.setActive(viewModel.isConnected && value)
        }

    var beepByWheel: Boolean
        get() = getValue(R.string.beep_by_wheel, false)
        set(value) = setValue(R.string.beep_by_wheel, value)

    var useCustomBeep: Boolean
        get() = getValue(R.string.custom_beep, false)
        set(value) = setValue(R.string.custom_beep, value)

    var beepFile: Uri
        get() = Uri.parse(getValue(R.string.beep_file, ""))
        set(value) = setValue(R.string.beep_file, value.toString())

    var customBeepTimeLimit: Float
        get() = getValue("custom_beep_time_limit", 2.0f)
        set(value) = setValue("custom_beep_time_limit", value)

    var useReconnect: Boolean
        get() = getValue(R.string.use_reconnect, false)
        set(value) {
        }

    var detectBatteryOptimization: Boolean
        get() = getValue(R.string.use_detect_battery_optimization, true)
        set(value) = setValue(R.string.use_detect_battery_optimization, value)

    var privatePolicyAccepted: Boolean
        get() = getValue(R.string.private_policy_accepted, false)
        set(value) = setValue(R.string.private_policy_accepted, value)

    //endregion

    //region logs
    var autoLog: Boolean
        get() = getValue(R.string.auto_log, false)
        set(value) {
            setValue(R.string.auto_log, value)
        }

    var autoWatch: Boolean
        get() = getValue(R.string.auto_watch, false)
        set(value) = setValue(R.string.auto_watch, value)

    var autoUploadEc: Boolean
        get() = getValue(R.string.auto_upload_ec, false)
        set(value) = setValue(R.string.auto_upload_ec, value)

    var logLocationData: Boolean
        get() = getValue(R.string.log_location_data, false)
        set(value) = setValue(R.string.log_location_data, value)

    var ecUserId: String?
        get() = getValue(R.string.ec_user_id, null)
        set(value) = setValue(R.string.ec_user_id, value)

    var ecToken: String?
        get() = getValue(R.string.ec_token, null)
        set(value) = setValue(R.string.ec_token, value)

    var ecGarage: String?
        get() = getSpecific(R.string.ec_garage, null)
        set(value) = setSpecific(R.string.ec_garage, value)

    var enableRawData: Boolean
        get() = getValue(R.string.use_raw_data, false)
        set(value) = setValue(R.string.use_raw_data, value)

    var startAutoLoggingWhenIsMovingMore: Float
        get() = getValue(R.string.auto_log_when_moving_more, 7f)
        set(value) = setValue(R.string.auto_log_when_moving_more, value)

    var continueThisDayLog: Boolean
        get() = getValue(R.string.continue_this_day_log, false)
        set(value) = setValue(R.string.continue_this_day_log, value)

    var continueThisDayLogMacException: String
        get() = getValue(R.string.continue_this_day_log_exception, "")
        set(value) = setValue(R.string.continue_this_day_log_exception, value)
    //endregion    

    //region watch
    var hornMode: Int
        get() = getValue(R.string.horn_mode, 0)
        set(value) = setValue(R.string.horn_mode, value)

    var garminConnectIqEnable: Boolean
        get() = getValue(R.string.garmin_connectiq_enable, false)
        set(value) = setValue(R.string.garmin_connectiq_enable, value)

    var useGarminBetaCompanion: Boolean
        get() = getValue(R.string.garmin_connectiq_use_beta, false)
        set(value) = setValue(R.string.garmin_connectiq_use_beta, value)

    var mainMenuButtons: Array<String>
        get() = getValue<String?>("main_menu_buttons", null)?.split(separator)?.toTypedArray()
            ?: arrayOf("watch")
        set(value) = setValue("main_menu_buttons", value.joinToString(separator))

    var showClock: Boolean
        get() = getValue("show_clock", true)
        set(value) = setValue("show_clock", value)

    fun getResId(resName: String?): Int {
        return if (resName == null || resName === "") {
            -1
        } else try {
            context.resources.getIdentifier(resName, "string", context.packageName)
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private fun setSpecific(resId: Int, value: Any?) {
        setValue(specificPrefix + "_" + context.getString(resId), value)
    }

    fun setValue(resId: Int, value: Any?) {
        setValue(context.getString(resId), value)
    }

    fun setValue(key: String, value: Any?) {
        when (value) {
            is String? -> sharedPreferences.edit().putString(key, value).apply()
            is String -> sharedPreferences.edit().putString(key, value).apply()
            is Int -> sharedPreferences.edit().putInt(key, value).apply()
            is Float -> sharedPreferences.edit().putFloat(key, value).apply()
            is Double -> sharedPreferences.edit().putFloat(key, value.toFloat()).apply()
            is Boolean -> sharedPreferences.edit().putBoolean(key, value).apply()
            is Long -> sharedPreferences.edit().putLong(key, value).apply()
        }
    }

    private fun <T : Any?> getSpecific(resId: Int, defaultValue: T): T {
        return getValue(specificPrefix + "_" + context.getString(resId), defaultValue)
    }

    fun <T : Any?> getValue(resId: Int, defaultValue: T): T {
        return getValue(context.getString(resId), defaultValue)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any?> getValue(key: String, defaultValue: T): T {
        return try {
            when (defaultValue) {
                is String? -> sharedPreferences.getString(key, defaultValue) as T
                is String -> sharedPreferences.getString(key, defaultValue) as T
                is Int -> sharedPreferences.getInt(key, defaultValue) as T
                is Float -> sharedPreferences.getFloat(key, defaultValue) as T
                is Double -> sharedPreferences.getFloat(key, defaultValue.toFloat()).toDouble() as T
                is Boolean -> sharedPreferences.getBoolean(key, defaultValue) as T
                is Long -> sharedPreferences.getLong(key, defaultValue) as T
                else -> defaultValue
            }
        } catch (ex: ClassCastException) {
            defaultValue
        }
    }

}