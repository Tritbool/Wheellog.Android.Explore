package com.cooper.wheellog.feature.dashboard

import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionState
import com.cooper.wheellog.utils.MathsUtil.kmToMiles
import com.cooper.wheellog.utils.MathsUtil.celsiusToFahrenheit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure mapping function: [BleSessionState] + app config → [DashboardUiState].
 *
 * No Android framework calls, no side effects.  Fully unit-testable without
 * Robolectric or any Android instrumentation (only [AppConfig] access, which
 * is easily mocked).
 */
object DashboardMapper {

    /**
     * Build a [DashboardUiState] from the current BLE session state and config.
     *
     * @param state          Latest [BleSessionState] from [BleSessionViewModel].
     * @param swapOverride   When non-null, overrides [AppConfig.swapSpeedPwm] so the
     *                       ViewModel can track a user-initiated display-mode toggle
     *                       without persisting it to SharedPreferences immediately.
     * @param appConfig      Injected app config (display preferences, alarm thresholds).
     */
    fun map(
        state: BleSessionState,
        swapOverride: Boolean?,
        appConfig: AppConfig
    ): DashboardUiState {
        val useMph = appConfig.useMph
        val maxSpeedConf = appConfig.maxSpeed

        // Return a minimal "disconnected" state that still carries display config.
        if (!state.isConnected) {
            return DashboardUiState.EMPTY.copy(
                useMph = useMph,
                speedUnit = if (useMph) "mph" else "km/h",
                maxSpeed = maxSpeedConf,
                colorPwmStart = appConfig.colorPwmStart,
                colorPwmEnd = appConfig.colorPwmEnd
            )
        }

        val speed = state.currentSpeed.toFloat()               // km/h
        val pwm = normalizePwm(state.pwm?.toFloat() ?: 0f)
        val maxPwm = normalizePwm((state.sessionMaxPwm ?: 0.0).toFloat())
        val battery = state.batteryLevel
        val batteryLowest = state.sessionBatteryLowest ?: 101
        val temp = state.currentTemperature.toFloat()          // °C
        val batteryDisplay = formatBattery(battery)
        val temperatureDisplay = formatTemperature(temp, appConfig.useFahrenheit)

        // ── Speed display ─────────────────────────────────────────────────────
        val displaySpeedValue = if (useMph) kmToMiles(speed) else speed
        val speedUnit = if (useMph) "mph" else "km/h"
        val speedDisplay = when {
            abs(displaySpeedValue) >= 100f -> displaySpeedValue.roundToInt().toString()
            else -> String.format("%.1f", displaySpeedValue)
        }

        // ── Display mode ──────────────────────────────────────────────────────
        val swapSpeedPwm = swapOverride ?: appConfig.swapSpeedPwm
        val displayMode = if (swapSpeedPwm) DisplayMode.PWM else DisplayMode.SPEED

        // ── Gauge fractions ───────────────────────────────────────────────────
        // The main dial normalises the displayed value against maxSpeed (same as
        // the legacy WheelView which uses: targetX = min(|value|, maxSpeed) / maxSpeed * 112).
        val mainDialFraction = when (displayMode) {
            DisplayMode.SPEED ->
                (abs(speed).coerceAtMost(maxSpeedConf.toFloat())) / maxSpeedConf.toFloat()
            DisplayMode.PWM ->
                (abs(pwm).coerceAtMost(maxSpeedConf.toFloat())) / maxSpeedConf.toFloat()
        }.coerceIn(0f, 1f)

        val batteryFraction = (battery / 100f).coerceIn(0f, 1f)
        val batteryLowestFraction = (batteryLowest.coerceAtMost(100) / 100f).coerceIn(0f, 1f)
        // Temperature arc uses 80 °C as 100 % (same as WheelView: 40 segments for 0-80 °C).
        val temperatureFraction = (temp.coerceIn(0f, 80f) / 80f)

        // ── Alarm level ───────────────────────────────────────────────────────
        val alarmLevel = computeAlarmLevel(state, pwm, appConfig)

        // ── Session statistics ────────────────────────────────────────────────
        val topSpeed = (state.sessionTopSpeed ?: state.topSpeed ?: 0.0).toFloat()
        val distance = (state.sessionDistance ?: state.wheelDistance ?: 0.0).toFloat()
        val totalDistance = (state.totalDistance ?: 0.0).toFloat()
        val ridingTimeSec = state.sessionRidingTimeSec ?: state.rideTime ?: 0L
        val rideTimeFormatted = formatRideTime(ridingTimeSec)
        val wheelModel = appConfig.profileName
            .takeIf { it.isNotBlank() }
            ?: state.deviceModel.takeIf { it != "Unknown" && it.isNotBlank() }
            ?: state.deviceName.takeIf { it != "Unknown" && it.isNotBlank() }
            ?: ""

        return DashboardUiState(
            isConnected = true,
            wheelModel = wheelModel,
            speed = speed,
            speedDisplay = speedDisplay,
            speedUnit = speedUnit,
            pwm = pwm,
            maxPwm = maxPwm,
            battery = battery,
            batteryDisplay = batteryDisplay,
            batteryLowest = batteryLowest,
            temperature = temp,
            temperatureDisplay = temperatureDisplay,
            voltage = state.currentVoltage.toFloat(),
            current = state.currentCurrent.toFloat(),
            topSpeed = topSpeed,
            distance = distance,
            totalDistance = totalDistance,
            rideTimeFormatted = rideTimeFormatted,
            displayMode = displayMode,
            useShortPwm = appConfig.useShortPwm,
            useMph = useMph,
            alarmLevel = alarmLevel,
            mainDialFraction = mainDialFraction,
            batteryFraction = batteryFraction,
            batteryLowestFraction = batteryLowestFraction,
            temperatureFraction = temperatureFraction,
            colorPwmStart = appConfig.colorPwmStart,
            colorPwmEnd = appConfig.colorPwmEnd,
            maxSpeed = maxSpeedConf,
            infoBlocks = emptyList()   // built by DashboardViewModel for context-sensitive labels
        )
    }

    // ── Alarm level computation ───────────────────────────────────────────────

    internal fun computeAlarmLevel(
        state: BleSessionState,
        pwm: Float,
        appConfig: AppConfig
    ): AlarmLevel {
        if (!appConfig.alarmsEnabled) return AlarmLevel.NONE

        return if (appConfig.pwmBasedAlarms) {
            computePwmAlarmLevel(pwm, appConfig)
        } else {
            computeSpeedAlarmLevel(state, appConfig)
        }
    }

    private fun computePwmAlarmLevel(pwm: Float, appConfig: AppConfig): AlarmLevel {
        val fraction = pwm / 100.0
        return when {
            fraction >= appConfig.alarmFactor2 / 100.0 -> AlarmLevel.CRITICAL
            fraction >= appConfig.alarmFactor1 / 100.0 -> AlarmLevel.WARN
            else -> AlarmLevel.NONE
        }
    }

    private fun computeSpeedAlarmLevel(state: BleSessionState, appConfig: AppConfig): AlarmLevel {
        val speed = state.currentSpeed
        val battery = state.batteryLevel
        return when {
            checkSpeedAlarm(speed, battery, appConfig.alarm3Speed, appConfig.alarm3Battery) ->
                AlarmLevel.CRITICAL
            checkSpeedAlarm(speed, battery, appConfig.alarm2Speed, appConfig.alarm2Battery) ->
                AlarmLevel.WARN
            checkSpeedAlarm(speed, battery, appConfig.alarm1Speed, appConfig.alarm1Battery) ->
                AlarmLevel.WARN
            else -> AlarmLevel.NONE
        }
    }

    private fun checkSpeedAlarm(
        speed: Double, battery: Int,
        alarmSpeed: Int, alarmBattery: Int
    ): Boolean = alarmSpeed > 0 && alarmBattery > 0
            && battery <= alarmBattery
            && speed >= alarmSpeed

    // ── Helpers ───────────────────────────────────────────────────────────────

    internal fun formatRideTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    internal fun formatBattery(battery: Int): String =
        String.format("%02d%%", battery.coerceIn(0, 100))

    internal fun formatTemperature(celsius: Float, useFahrenheit: Boolean): String {
        val roundedCelsius = celsius.roundToInt()
        return if (useFahrenheit) {
            String.format("%02d℉", celsiusToFahrenheit(roundedCelsius.toDouble()).toInt())
        } else {
            String.format("%02d℃", roundedCelsius)
        }
    }

    internal fun normalizePwm(pwm: Float): Float {
        if (!pwm.isFinite()) return 0f
        var normalized = pwm
        while (abs(normalized) > 100f) {
            normalized /= 10f
        }
        return normalized
    }
}
