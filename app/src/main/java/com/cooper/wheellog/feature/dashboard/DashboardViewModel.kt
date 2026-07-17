package com.cooper.wheellog.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionViewModel
import kotlinx.coroutines.flow.*

/**
 * ViewModel for the dashboard screen.
 *
 * Receives the shared [BleSessionViewModel] and [AppConfig] as constructor
 * parameters so it can be created with a known [BleSessionViewModel] instance
 * (the one already scoped to the Activity's ViewModelStore) rather than letting
 * Koin create an orphaned duplicate.
 *
 * Usage in a composable:
 * ```kotlin
 * val bleVm: BleSessionViewModel = koinViewModel()
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

        val distUnit = if (state.useMph) "mi" else "km"
        val speedUnit = state.speedUnit

        // The full block catalogue – matches the legacy WheelView.viewBlockInfo list.
        val catalogue = mapOf(
            "pwm" to DashboardBlock("PWM", String.format("%.2f%%", state.pwm)),
            "max_pwm" to DashboardBlock("Max PWM", String.format("%.2f%%", state.maxPwm)),
            "voltage" to DashboardBlock("Voltage", String.format("%.2f V", state.voltage)),
            "top_speed" to DashboardBlock(
                "Top Speed",
                String.format("%.1f %s", toDisplaySpeed(state.topSpeed, state.useMph), speedUnit)
            ),
            "distance" to DashboardBlock(
                "Distance",
                formatDistance(state.distance, state.useMph)
            ),
            "total" to DashboardBlock(
                "Total",
                String.format("%.0f %s", toDisplayDistance(state.totalDistance, state.useMph), distUnit)
            ),
            "battery" to DashboardBlock("Battery", String.format("%d%%", state.battery)),
            "current" to DashboardBlock("Current", String.format("%.1f A", state.current)),
            "temperature" to DashboardBlock("Temp", String.format("%.0f °C", state.temperature)),
            "riding_time" to DashboardBlock("Ride Time", state.rideTimeFormatted)
        )

        // Use a sensible default set when no custom block list is configured.
        val selectedKeys = listOf("pwm", "max_pwm", "voltage", "top_speed", "distance", "total", "battery", "riding_time")
        return selectedKeys.mapNotNull { catalogue[it] }
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
