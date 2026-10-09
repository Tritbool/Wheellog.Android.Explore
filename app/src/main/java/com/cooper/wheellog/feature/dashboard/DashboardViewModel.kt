package com.cooper.wheellog.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.utils.Calculator
import com.cooper.wheellog.utils.MathsUtil
import kotlinx.coroutines.flow.*
import java.util.Locale

/**
 * ViewModel for the dashboard screen.
 *
 * Receives the shared [BleSessionViewModel] and [AppConfig] as constructor
 * parameters so it can be created with a known [BleSessionViewModel] instance
 * (the shared app-wide singleton registered in `bleModule`) rather than letting
 * Koin create an orphaned duplicate.
 *
 * Usage in a composable:
 * ```kotlin
 * val bleVm: BleSessionViewModel = koinInject()
 * val dashVm: DashboardViewModel = koinViewModel { parametersOf(bleVm) }
 * ```
 */
class DashboardViewModel(
    application: Application,
    private val bleViewModel: BleSessionViewModel,
    private val appConfig: AppConfig
) : AndroidViewModel(application) {

    /**
     * Local display-mode override, null means "follow [AppConfig.swapSpeedPwm]".
     * Updated when the user taps the gauge to toggle speed ↔ PWM display.
     */
    private val _swapOverride = MutableStateFlow<Boolean?>(null)

    /**
     * The single source of truth for the dashboard UI.
     * Rebuilt on every BLE state update or display-mode toggle.
     */
    val uiState: StateFlow<DashboardUiState> = bleViewModel.sessionState
        .combine(_swapOverride) { state, swap -> state to swap }
        .map { (state, swap) ->
            val base = DashboardMapper.map(state, swap, appConfig)
            base.copy(infoBlocks = buildInfoBlocks(base))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DashboardUiState.EMPTY
        )

    /**
     * Toggle between speed and PWM as the primary dial / centre-text value.
     * The toggle is session-local and does not persist to SharedPreferences.
     */
    fun toggleDisplayMode() {
        val current = _swapOverride.value ?: appConfig.swapSpeedPwm
        _swapOverride.value = !current
    }

    // ── Info-block construction ────────────────────────────────────────────

    /**
     * Build the pre-formatted info blocks that are displayed around the gauge.
     * Ordering and selection mirror [AppConfig.viewBlocks] at the time of mapping.
     *
     * String formatting is intentionally done here (not in the composable) so the
     * composable remains a pure "draw what you see" component.
     */
    private fun buildInfoBlocks(state: DashboardUiState): List<DashboardBlock> {
        if (!state.isConnected) return emptyList()

        val application = getApplication<Application>()
        val distUnit = if (state.useMph) "mi" else "km"
        val speedUnit = state.speedUnit

        val catalogue = linkedMapOf(
            application.getString(R.string.pwm) to DashboardBlock(
                application.getString(R.string.pwm),
                String.format(Locale.US, "%.2f%%", state.pwm)
            ),
            application.getString(R.string.max_pwm) to DashboardBlock(
                application.getString(R.string.max_pwm),
                String.format(Locale.US, "%.2f%%", state.maxPwm)
            ),
            application.getString(R.string.voltage) to DashboardBlock(
                application.getString(R.string.voltage),
                String.format(Locale.US, "%.2f V", state.voltage)
            ),
            application.getString(R.string.average_speed) to DashboardBlock(
                application.getString(R.string.average_speed),
                String.format(
                    Locale.US,
                    "%.1f %s",
                    toDisplaySpeed(bleViewModel.averageSpeedDouble.toFloat(), state.useMph),
                    speedUnit
                )
            ),
            application.getString(R.string.average_riding_speed) to DashboardBlock(
                application.getString(R.string.average_riding_speed),
                String.format(
                    Locale.US,
                    "%.1f %s",
                    toDisplaySpeed(bleViewModel.averageRidingSpeedDouble.toFloat(), state.useMph),
                    speedUnit
                )
            ),
            application.getString(R.string.riding_time) to DashboardBlock(
                application.getString(R.string.riding_time),
                state.rideTimeFormatted
            ),
            application.getString(R.string.speed) to DashboardBlock(
                application.getString(R.string.speed),
                String.format(Locale.US, "%s %s", state.speedDisplay, speedUnit)
            ),
            application.getString(R.string.top_speed) to DashboardBlock(
                application.getString(R.string.top_speed),
                String.format("%.1f %s", toDisplaySpeed(state.topSpeed, state.useMph), speedUnit)
            ),
            application.getString(R.string.distance) to DashboardBlock(
                application.getString(R.string.distance),
                formatDistance(state.distance, state.useMph)
            ),
            application.getString(R.string.total) to DashboardBlock(
                application.getString(R.string.total),
                String.format("%.0f %s", toDisplayDistance(state.totalDistance, state.useMph), distUnit)
            ),
            application.getString(R.string.battery) to DashboardBlock(
                application.getString(R.string.battery),
                state.batteryDisplay
            ),
            application.getString(R.string.current) to DashboardBlock(
                application.getString(R.string.current),
                String.format(Locale.US, "%.1f A", state.current)
            ),
            application.getString(R.string.phase_current) to DashboardBlock(
                application.getString(R.string.phase_current),
                String.format(Locale.US, "%.1f A", bleViewModel.phaseCurrentDouble)
            ),
            application.getString(R.string.temperature) to DashboardBlock(
                application.getString(R.string.temperature),
                state.temperatureDisplay
            ),
            application.getString(R.string.wheel_distance) to DashboardBlock(
                application.getString(R.string.wheel_distance),
                formatDistance(bleViewModel.wheelDistanceDouble.toFloat(), state.useMph)
            ),
            application.getString(R.string.user_distance) to DashboardBlock(
                application.getString(R.string.user_distance),
                formatDistance(bleViewModel.userDistanceDouble.toFloat(), state.useMph)
            ),
            application.getString(R.string.remaining_distance) to DashboardBlock(
                application.getString(R.string.remaining_distance),
                formatDistance(bleViewModel.remainingDistance.toFloat(), state.useMph)
            ),
            application.getString(R.string.battery_per_km) to DashboardBlock(
                application.getString(R.string.battery_per_km),
                String.format(Locale.US, "%.2f %%", bleViewModel.batteryPerKm)
            ),
            application.getString(R.string.avg_cell_volt) to DashboardBlock(
                application.getString(R.string.avg_cell_volt),
                String.format(Locale.US, "%.2f V", bleViewModel.avgVoltagePerCell)
            ),
            application.getString(R.string.maxcurrent) to DashboardBlock(
                application.getString(R.string.maxcurrent),
                String.format(Locale.US, "%.1f A", bleViewModel.maxCurrentDouble)
            ),
            application.getString(R.string.maxphasecurrent) to DashboardBlock(
                application.getString(R.string.maxphasecurrent),
                String.format(Locale.US, "%.1f A", bleViewModel.maxPhaseCurrentDouble)
            ),
            application.getString(R.string.power) to DashboardBlock(
                application.getString(R.string.power),
                String.format(Locale.US, "%.0f W", bleViewModel.powerDouble)
            ),
            application.getString(R.string.maxpower) to DashboardBlock(
                application.getString(R.string.maxpower),
                String.format(Locale.US, "%.0f W", bleViewModel.maxPowerDouble)
            ),
            application.getString(R.string.temperature2) to DashboardBlock(
                application.getString(R.string.temperature2),
                DashboardMapper.formatTemperature(bleViewModel.motorTemperatureDouble.toFloat(), appConfig.useFahrenheit)
            ),
            application.getString(R.string.maxtemperature) to DashboardBlock(
                application.getString(R.string.maxtemperature),
                DashboardMapper.formatTemperature(state.maxTemperature, appConfig.useFahrenheit)
            ),
            application.getString(R.string.ride_time) to DashboardBlock(
                application.getString(R.string.ride_time),
                bleViewModel.rideTimeString
            ),
            application.getString(R.string.consumption) to DashboardBlock(
                application.getString(R.string.consumption),
                if (state.useMph) {
                    String.format(Locale.US, "%.1f %s", Calculator.whByKm / MathsUtil.kmToMilesMultiplier, application.getString(R.string.whmi))
                } else {
                    String.format(Locale.US, "%.1f %s", Calculator.whByKm, application.getString(R.string.whkm))
                }
            )
        )

        return appConfig.viewBlocks.mapNotNull { catalogue[it] }
    }

    private fun toDisplaySpeed(kmh: Float, useMph: Boolean): Float =
        if (useMph) com.cooper.wheellog.utils.MathsUtil.kmToMiles(kmh) else kmh

    private fun toDisplayDistance(km: Float, useMph: Boolean): Float =
        if (useMph) com.cooper.wheellog.utils.MathsUtil.kmToMiles(km) else km

    private fun formatDistance(km: Float, useMph: Boolean): String {
        return if (useMph) {
            String.format("%.2f mi", toDisplayDistance(km, useMph))
        } else {
            if (km < 1f) String.format("%.0f m", km * 1000f)
            else String.format("%.2f km", km)
        }
    }
}
