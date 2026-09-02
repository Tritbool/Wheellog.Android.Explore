package com.cooper.wheellog.feature.dashboard

/** Alarm severity level for dashboard display. */
enum class AlarmLevel { NONE, WARN, CRITICAL }

/** Controls what the main dial and centre text display. */
enum class DisplayMode { SPEED, PWM }

/** A single pre-formatted metric shown in the info-block area. */
data class DashboardBlock(val label: String, val value: String)

/**
 * Immutable snapshot of everything the dashboard screen needs to render.
 *
 * Produced by [DashboardMapper] from raw [com.cooper.wheellog.ble.BleSessionState] + app config.
 * Composables must NOT access SharedPreferences, singletons, or WheelData directly;
 * all display logic lives in the mapper and the ViewModel.
 */
data class DashboardUiState(
    // ── Connection ─────────────────────────────────────────────────────────
    val isConnected: Boolean = false,
    val wheelModel: String = "",

    // ── Core telemetry ──────────────────────────────────────────────────────
    /** Speed in km/h (raw, used for arc normalisation). */
    val speed: Float = 0f,
    /** Formatted speed string ready for display (no unit suffix). */
    val speedDisplay: String = "0.0",
    val speedUnit: String = "km/h",
    /** PWM duty cycle, 0–100 %. */
    val pwm: Float = 0f,
    val maxPwm: Float = 0f,
    val battery: Int = 0,
    val batteryDisplay: String = "00%",
    val batteryLowest: Int = 101,
    /** Board temperature in °C. */
    val temperature: Float = 0f,
    val temperatureDisplay: String = "00℃",
    /** Highest board temperature reached this session, in °C. */
    val maxTemperature: Float = 0f,
    /** Formatted, labelled max-temperature string (e.g. "MAX 45℃"). */
    val maxTemperatureDisplay: String = "MAX 00℃",
    val voltage: Float = 0f,
    val current: Float = 0f,
    val topSpeed: Float = 0f,
    val distance: Float = 0f,
    val totalDistance: Float = 0f,
    val rideTimeFormatted: String = "00:00:00",

    // ── Display config ──────────────────────────────────────────────────────
    val displayMode: DisplayMode = DisplayMode.SPEED,
    /** Show a compact "XX% / XX%" PWM overlay below the main dial value. */
    val useShortPwm: Boolean = false,
    val useMph: Boolean = false,

    // ── Alarm ───────────────────────────────────────────────────────────────
    val alarmLevel: AlarmLevel = AlarmLevel.NONE,

    // ── Gauge rendering hints ───────────────────────────────────────────────
    /** Main dial fill fraction, 0 – 1, driven by [displayMode]. */
    val mainDialFraction: Float = 0f,
    /** Inner-arc battery fill fraction, 0 – 1. */
    val batteryFraction: Float = 0f,
    /** Inner-arc temperature fill fraction, 0 – 1 (0 = cold, 1 = 80 °C). */
    val temperatureFraction: Float = 0f,
    /** Max-temperature marker position on the inner arc, 0 – 1 (same scale as [temperatureFraction]). */
    val maxTemperatureFraction: Float = 0f,
    /** Battery-lowest safety marker position on the inner arc, 0 – 1. */
    val batteryLowestFraction: Float = 0f,
    /** PWM % at which the dial colour starts shifting toward red. */
    val colorPwmStart: Int = 60,
    /** PWM % at which the dial colour is fully red. */
    val colorPwmEnd: Int = 90,
    /** Configured max speed (km/h) used to normalise the dial arc. */
    val maxSpeed: Int = 50,

    // ── Info blocks ─────────────────────────────────────────────────────────
    /** Pre-formatted label/value pairs rendered around the gauge. */
    val infoBlocks: List<DashboardBlock> = emptyList()
) {
    companion object {
        val EMPTY = DashboardUiState()
    }
}
