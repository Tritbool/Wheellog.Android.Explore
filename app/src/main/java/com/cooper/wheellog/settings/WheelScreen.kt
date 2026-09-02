package com.cooper.wheellog.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.utils.Constants
import io.github.tritbool.euc.ble.protocols.CommandType
import org.koin.compose.koinInject

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
fun wheelScreen(
    appConfig: AppConfig = koinInject(),
    viewModel: BleSessionViewModel = koinInject(),
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        baseSettings(
            name = if (viewModel.isConnected) {
                viewModel.model.ifBlank { viewModel.name.ifBlank { stringResource(R.string.unknown_device) } }
            } else {
                stringResource(R.string.unknown_device)
            },
            desc = if (viewModel.isConnected) viewModel.manufacturer else "",
        )

        if (!viewModel.isConnected) {
            return@Column
        }

        lightPowerSetting(appConfig, viewModel)
        lightModeSetting(appConfig, viewModel)
        lightBrightnessSetting(appConfig, viewModel)
        speakerVolumeSetting(appConfig, viewModel)
        lockSetting(appConfig, viewModel)
        pedalsModeSetting(appConfig, viewModel)
        alarmModeSetting(viewModel)
        rollAngleInfo(viewModel)
        ledModeSetting(appConfig, viewModel)
        milesModeInfo(viewModel)
        speedLimitSetting(appConfig, viewModel)
        alarmSpeedSetting(appConfig, viewModel)
        powerOffSetting(viewModel)
        calibrationSetting(viewModel)
        resetTripSetting(viewModel)
        connectBeepSetting(appConfig, viewModel)
        gotwayCorrectionSettings(appConfig, viewModel)
        forAllWheel(appConfig)
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun lightPowerSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    val supportsLightToggle = viewModel.isCommandSupported(CommandType.LIGHT_ON) ||
            viewModel.isCommandSupported(CommandType.LIGHT_OFF)
    if (!supportsLightToggle) return

    switchPref(
        name = stringResource(R.string.on_headlight_title),
        desc = stringResource(R.string.on_headlight_description),
        default = appConfig.lightEnabled,
    ) { enabled ->
        appConfig.lightEnabled = enabled
        viewModel.sendCommand(if (enabled) CommandType.LIGHT_ON else CommandType.LIGHT_OFF)
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun lightModeSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.SET_LIGHT_MODE)) return

    list(
        name = stringResource(R.string.light_mode_title),
        desc = stringResource(R.string.on_off_auto),
        entries = mapOf(
            "0" to stringResource(R.string.on),
            "1" to stringResource(R.string.off),
            "2" to stringResource(R.string.auto),
            "3" to stringResource(R.string.strobe),
        ),
        defaultKey = appConfig.lightMode,
    ) {
        appConfig.lightMode = it.first
        viewModel.sendCommand(CommandType.SET_LIGHT_MODE, it.first.toInt())
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun lightBrightnessSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.LIGHT_BRIGHTNESS)) return

    sliderPref(
        name = stringResource(R.string.light_brightness_title),
        desc = stringResource(R.string.light_brightness_description),
        position = appConfig.lightBrightness.toFloat(),
        min = 0f,
        max = 100f,
    ) {
        appConfig.lightBrightness = it.toInt()
        viewModel.sendCommand(CommandType.LIGHT_BRIGHTNESS, it.toInt())
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun speakerVolumeSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.SPEAKER_VOLUME)) return

    sliderPref(
        name = stringResource(R.string.speaker_volume_title),
        desc = stringResource(R.string.speaker_volume_description),
        position = appConfig.speakerVolume.toFloat(),
        min = 0f,
        max = 100f,
    ) {
        appConfig.speakerVolume = it.toInt()
        viewModel.sendCommand(CommandType.SPEAKER_VOLUME, it.toInt())
    }
}

@Composable
private fun connectBeepSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.BEEP)) return

    switchPref(
        name = stringResource(R.string.connect_beep_title),
        desc = stringResource(R.string.connect_beep_description),
        default = appConfig.connectBeep,
    ) { enabled ->
        appConfig.connectBeep = enabled
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun lockSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    val supportsLock = viewModel.isCommandSupported(CommandType.LOCK) ||
            viewModel.isCommandSupported(CommandType.UNLOCK)
    if (!supportsLock) return

    switchPref(
        name = stringResource(R.string.lock_mode_title),
        desc = stringResource(R.string.lock_mode_description),
        default = appConfig.wheelLocked,
    ) { locked ->
        appConfig.wheelLocked = locked
        viewModel.sendCommand(if (locked) CommandType.LOCK else CommandType.UNLOCK)
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun pedalsModeSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.SET_PEDALS_MODE)) return

    list(
        name = stringResource(R.string.pedals_mode_title),
        desc = stringResource(R.string.soft_medium_hard),
        entries = mapOf(
            "0" to stringResource(R.string.hard),
            "1" to stringResource(R.string.medium),
            "2" to stringResource(R.string.soft),
        ),
        defaultKey = appConfig.pedalsMode,
    ) {
        appConfig.pedalsMode = it.first
        viewModel.sendCommand(CommandType.SET_PEDALS_MODE, it.first.toInt())
    }
}

/**
 * Wheel-reported alarm mode (which speed/PWM alarm thresholds are currently disabled on
 * the wheel itself). On Gotway/Begode, [CommandType.SET_ALARM_SPEED] actually implements
 * this same preset (legacy WheelLog.Android's `GotwayAdapter.updateAlarmMode`: "o"/"u"/"i"/"I"
 * for on_level_alarm/off_level_1_alarm/off_level_2_alarm/pwm_tiltback_alarm), so it's editable
 * there. Other protocols that support [CommandType.SET_ALARM_SPEED] use it for raw speed
 * threshold(s) instead (see [alarmSpeedSetting]), so this stays read-only for them.
 */
@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun alarmModeSetting(viewModel: BleSessionViewModel) {
    val state by viewModel.sessionState.collectAsState()
    val alarmMode = state.alarmMode

    val entries = mapOf(
        "0" to stringResource(R.string.on_level_alarm),
        "1" to stringResource(R.string.off_level_1_alarm),
        "2" to stringResource(R.string.off_level_2_alarm),
        "3" to stringResource(R.string.pwm_tiltback_alarm),
    )

    if (viewModel.wheelType == Constants.WHEEL_TYPE.GOTWAY &&
        viewModel.isCommandSupported(CommandType.SET_ALARM_SPEED)
    ) {
        list(
            name = stringResource(R.string.alarm_mode_title),
            desc = stringResource(R.string.alarm_settings_title),
            entries = entries,
            defaultKey = (alarmMode ?: 0).toString(),
        ) {
            viewModel.sendCommand(CommandType.SET_ALARM_SPEED, it.first.toInt())
        }
        return
    }

    if (alarmMode == null) return
    baseSettings(
        name = stringResource(R.string.alarm_mode_title),
        rightContent = { Text(entries[alarmMode.toString()] ?: entries.getValue("3")) },
    )
}

/**
 * Read-only display of the wheel-reported roll-angle cutoff sensitivity.
 * See [alarmModeSetting] for why this isn't editable yet.
 */
@Composable
private fun rollAngleInfo(viewModel: BleSessionViewModel) {
    val state by viewModel.sessionState.collectAsState()
    val rollAngleMode = state.rollAngleMode ?: return

    val label = when (rollAngleMode) {
        0 -> stringResource(R.string.low)
        1 -> stringResource(R.string.medium)
        else -> stringResource(R.string.high)
    }
    baseSettings(
        name = stringResource(R.string.roll_angle_title),
        desc = stringResource(R.string.roll_angle_description),
        rightContent = { Text(label) },
    )
}

/**
 * Read-only display of whether the wheel's own controller currently reports
 * speed/distance in miles instead of km. See [alarmModeSetting] for why this
 * isn't editable yet.
 */
@Composable
private fun milesModeInfo(viewModel: BleSessionViewModel) {
    val state by viewModel.sessionState.collectAsState()
    val usesMiles = state.usesMiles ?: return

    baseSettings(
        name = stringResource(R.string.gw_in_miles_title),
        desc = stringResource(R.string.gw_in_miles_description),
        rightContent = {
            Text(stringResource(if (usesMiles) R.string.on else R.string.off))
        },
    )
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun ledModeSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.SET_LED_MODE)) return

    list(
        name = stringResource(R.string.led_mode_title),
        desc = stringResource(R.string.on_off),
        entries = mapOf(
            "0" to stringResource(R.string.zero),
            "1" to stringResource(R.string.one),
            "2" to stringResource(R.string.two),
            "3" to stringResource(R.string.three),
            "4" to stringResource(R.string.four),
            "5" to stringResource(R.string.five),
            "6" to stringResource(R.string.six),
            "7" to stringResource(R.string.seven),
            "8" to stringResource(R.string.eight),
            "9" to stringResource(R.string.nine),
        ),
        defaultKey = appConfig.ledMode,
    ) {
        appConfig.ledMode = it.first
        viewModel.sendCommand(CommandType.SET_LED_MODE, it.first.toInt())
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun speedLimitSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.SET_SPEED_LIMIT)) return

    sliderPref(
        name = stringResource(R.string.max_speed_title),
        desc = stringResource(R.string.tilt_back_description),
        position = appConfig.wheelMaxSpeed.toFloat(),
        min = 0f,
        max = 100f,
        unit = R.string.kmh,
    ) {
        appConfig.wheelMaxSpeed = it.toInt()
        viewModel.sendCommand(CommandType.SET_SPEED_LIMIT, it.toInt())
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun alarmSpeedSetting(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.SET_ALARM_SPEED)) return
    // Gotway/Begode overloads this same CommandType as an alarm-mode preset index
    // rather than a raw speed threshold; that case is handled by alarmModeSetting.
    if (viewModel.wheelType == Constants.WHEEL_TYPE.GOTWAY) return

    sliderPref(
        name = stringResource(R.string.wheel_alarm1_title),
        desc = stringResource(R.string.wheel_alarm1_description),
        position = appConfig.wheelAlarm1Speed.toFloat(),
        min = 0f,
        max = 100f,
        unit = R.string.kmh,
    ) {
        appConfig.wheelAlarm1Speed = it.toInt()
        viewModel.sendCommand(CommandType.SET_ALARM_SPEED, it.toInt())
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun powerOffSetting(viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.POWER_OFF)) return

    clickableAndAlert(
        name = stringResource(R.string.power_off),
        confirmButtonText = stringResource(R.string.power_off),
        alertDesc = stringResource(R.string.power_off_message),
        condition = { viewModel.speed < 1 },
        onConfirm = { viewModel.sendCommand(CommandType.POWER_OFF) },
    )
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun calibrationSetting(viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.CALIBRATE)) return

    clickableAndAlert(
        name = stringResource(R.string.wheel_calibration),
        confirmButtonText = stringResource(R.string.wheel_calibration),
        alertDesc = stringResource(R.string.wheel_calibration_message_inmo),
        condition = { viewModel.speed < 1 },
        onConfirm = { viewModel.sendCommand(CommandType.CALIBRATE) },
    )
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
private fun resetTripSetting(viewModel: BleSessionViewModel) {
    if (!viewModel.isCommandSupported(CommandType.RESET_TRIP)) return

    clickableAndAlert(
        name = stringResource(R.string.reset_trip),
        confirmButtonText = stringResource(R.string.reset_trip),
        alertDesc = stringResource(R.string.reset_trip_message),
        onConfirm = { viewModel.sendCommand(CommandType.RESET_TRIP) },
    )
}

/**
 * Gotway/Begode's reverse-engineered protocol has known quirks that are corrected on the
 * app side rather than by asking the wheel to change anything (see
 * [BleSessionViewModel]'s telemetry pipeline for where these are applied). Only shown for
 * Gotway/Begode/ExtremeBull-family wheels.
 */
@Composable
private fun gotwayCorrectionSettings(appConfig: AppConfig, viewModel: BleSessionViewModel) {
    if (viewModel.wheelType != Constants.WHEEL_TYPE.GOTWAY) return

    switchPref(
        name = stringResource(R.string.is_gotway_mcm_title),
        desc = stringResource(R.string.is_gotway_mcm_description),
        default = appConfig.gotwayMcm,
    ) {
        appConfig.gotwayMcm = it
    }
    list(
        name = stringResource(R.string.gotway_negative_title),
        desc = stringResource(R.string.gotway_negative_description),
        entries = mapOf(
            "-1" to stringResource(R.string.straight),
            "0" to stringResource(R.string.absolute),
            "1" to stringResource(R.string.reverse),
        ),
        defaultKey = appConfig.gotwayNegative,
    ) {
        appConfig.gotwayNegative = it.first
    }
}

@Composable
private fun forAllWheel(appConfig: AppConfig = koinInject()) {
    sliderPref(
        name = stringResource(R.string.battery_capacity_title),
        desc = stringResource(R.string.battery_capacity_description),
        position = appConfig.batteryCapacity.toFloat(),
        min = 0f,
        max = 9999f,
        unit = R.string.wh,
    ) {
        appConfig.batteryCapacity = it.toInt()
    }
    sliderPref(
        name = stringResource(R.string.charging_power_title),
        desc = stringResource(R.string.charging_power_description),
        position = appConfig.chargingPower.toFloat() / 10f,
        min = 0f,
        max = 100.0f,
        unit = R.string.amp,
        format = "%.1f",
    ) {
        appConfig.chargingPower = it.toInt() * 10
    }

    var showProfileDialog by remember { mutableStateOf(false) }
    var profileText by remember { mutableStateOf(appConfig.profileName) }
    clickablePref(
        name = stringResource(R.string.profile_name_title),
        desc = profileText
    ) {
        showProfileDialog = true
    }
    if (showProfileDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { androidx.compose.material3.Text(stringResource(R.string.profile_name_title)) },
            text = {
                androidx.compose.material3.TextField(
                    value = profileText,
                    onValueChange = { newText ->
                        profileText = newText
                        appConfig.profileName = newText
                    },
                    singleLine = true,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showProfileDialog = false
                    },
                ) {
                    androidx.compose.material3.Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showProfileDialog = false },
                ) {
                    androidx.compose.material3.Text(stringResource(android.R.string.cancel))
                }
            },
            properties = DialogProperties(
                dismissOnClickOutside = false,
            ),
        )
    }
    baseSettings(
        name = stringResource(R.string.current_mac),
    ) {
        androidx.compose.material3.Text(appConfig.lastMac.trimEnd('_'))
    }
}
