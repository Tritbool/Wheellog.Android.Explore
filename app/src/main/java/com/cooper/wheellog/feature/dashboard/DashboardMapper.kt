package com.cooper.wheellog.feature.dashboard

import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionState
import kotlin.math.abs

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
     * @param swapOverride   Optional mapping override; production gestures persist
     *                       [AppConfig.swapSpeedPwm] and do not use an override.
     * @param appConfig      Injected app config (display preferences, alarm thresholds).
     */
    fun map(
        state: BleSessionState,
        swapOverride: Boolean?,
        appConfig: AppConfig
    ): DashboardUiState {
        val useMph = appConfig.useMph
        val maxSpeedConf = appConfig.maxSpeed

        val speedTenths = (state.currentSpeed * 10).toInt()
        val speed = speedTenths / 10f
        val pwm = normalizePwm(state.pwm?.toFloat() ?: 0f)
        val maxPwm = normalizePwm((state.sessionMaxPwm ?: 0.0).toFloat())
        val battery = state.batteryLevel.coerceIn(0, 100)
        val batteryLowest = state.sessionBatteryLowest ?: 101
        val temp = state.currentTemperature.toInt().coerceIn(-100, 100).toFloat()
        val maxTemp = (state.sessionMaxTemperature ?: temp.toDouble()).toInt().toFloat()
        val batteryDisplay = formatBattery(battery)
        val temperatureDisplay = formatTemperature(temp, appConfig.useFahrenheit)
        val maxTemperatureDisplay = formatTemperature(maxTemp, appConfig.useFahrenheit)

        // ── Speed display ─────────────────────────────────────────────────────
        val speedUnit = if (useMph) "mph" else "km/h"
        val speedDisplay = DashboardFormatting.speed(speedTenths, useMph)

        // ── Display mode ──────────────────────────────────────────────────────
        val swapSpeedPwm = swapOverride ?: appConfig.swapSpeedPwm
        val displayMode = if (swapSpeedPwm) DisplayMode.PWM else DisplayMode.SPEED

        // ── Gauge fractions ───────────────────────────────────────────────────
        // valueOnDial controls the arc independently of the central speed/PWM swap.
        val speedFraction = DashboardFormatting.fraction(abs(speed), maxSpeedConf)
        val currentFraction = DashboardFormatting.fraction(state.currentCurrent.toFloat(), maxSpeedConf)
        val mainDialFraction = when (appConfig.valueOnDial) {
            "1" -> currentFraction
            "2" -> DashboardFormatting.fraction(pwm, maxSpeedConf)
            "3" -> DashboardFormatting.fraction((state.lastData?.phaseCurrent ?: 0.0).toFloat(), maxSpeedConf)
            else -> speedFraction
        }

        val batteryFraction = (battery / 100f).coerceIn(0f, 1f)
        val batteryLowestFraction = if (batteryLowest > 100) 0f
            else (batteryLowest / 100f).coerceIn(0f, 1f)
        // Temperature arc preserves 80 °C as 100 %: 40 segments for 0-80 °C.
        val temperatureFraction = (temp.coerceIn(0f, 80f) / 80f)
        val maxTemperatureFraction = (maxTemp.coerceIn(0f, 80f) / 80f)

        // ── Alarm level ───────────────────────────────────────────────────────
        val alarmLevel = computeAlarmLevel(state, pwm, appConfig)

        // ── Session statistics ────────────────────────────────────────────────
        val topSpeed = (state.sessionTopSpeed ?: 0.0).toFloat()
        val distance = (state.sessionDistance ?: state.wheelDistance ?: 0.0).toFloat()
        val totalDistance = (state.totalDistance ?: 0.0).toFloat()
        val ridingTimeSec = state.sessionRidingTimeSec ?: 0L
        val rideTimeFormatted = formatRideTime(ridingTimeSec)
        val wheelModel = appConfig.profileName
            .takeIf { it.isNotBlank() }
            ?: state.deviceModel.takeIf { it != "Unknown" && it.isNotBlank() }
            ?: state.deviceName.takeIf { it != "Unknown" && it.isNotBlank() }
            ?: ""

        return DashboardUiState(
            isConnected = state.isConnected,
            appTheme = appConfig.appTheme,
            nightMode = appConfig.dayNightThemeMode,
            speedWarning = !appConfig.pwmBasedAlarms && appConfig.alarm1Speed > 0 &&
                speedTenths >= appConfig.alarm1Speed * 10,
            valueOnDial = appConfig.valueOnDial,
            secondaryDialFraction = if (appConfig.valueOnDial == "1" || appConfig.valueOnDial == "3")
                speedFraction else currentFraction,
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
            maxTemperature = maxTemp,
            maxTemperatureDisplay = maxTemperatureDisplay,
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
            maxTemperatureFraction = maxTemperatureFraction,
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
        return DashboardFormatting.format("%02d:%02d:%02d", h, m, s)
    }

    internal fun formatBattery(battery: Int): String =
        DashboardFormatting.format("%02d%%", battery.coerceIn(0, 100))

    internal fun formatTemperature(celsius: Float, useFahrenheit: Boolean): String {
        return DashboardFormatting.temperature(celsius.toInt(), useFahrenheit)
    }

    internal fun normalizePwm(pwm: Float): Float {
        if (!pwm.isFinite()) return 0f
        return pwm
    }
}
