package com.cooper.wheellog.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionState
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.utils.Calculator
import com.cooper.wheellog.utils.MathsUtil
import kotlinx.coroutines.flow.*

class DashboardViewModel(
    application: Application,
    private val bleViewModel: BleSessionViewModel,
    private val appConfig: AppConfig
) : AndroidViewModel(application) {
    private val refresh = MutableStateFlow(0)
    private var resetBatteryFor: BleSessionState? = null
    private val legacyAliases by lazy { DashboardCatalogue.aliases(application) }
    val uiState = combine(
        bleViewModel.sessionState, appConfig.dashboardPreferences(), refresh
    ) { session, _, _ -> present(session) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), present(bleViewModel.sessionState.value))

    fun toggleDisplayMode() {
        appConfig.swapSpeedPwm = !appConfig.swapSpeedPwm
        refresh.value++
    }

    fun resetBatteryLowest() {
        resetBatteryFor = bleViewModel.sessionState.value
        refresh.value++
    }

    fun present(session: BleSessionState): DashboardUiState {
        val mapped = DashboardMapper.map(session, null, appConfig)
        val base = if (resetBatteryFor === session)
            mapped.copy(batteryLowest = 101, batteryLowestFraction = 0f) else mapped
        val context = getApplication<Application>()
        val mph = appConfig.useMph
        fun unit(id: Int) = context.getString(id)
        fun number(value: Double, decimals: Int, suffix: Int) =
            DashboardFormatting.format("%.${decimals}f %s", value, unit(suffix))
        fun speed(value: Double) = number(if (mph) MathsUtil.kmToMiles(value) else value, 1,
            if (mph) R.string.mph else R.string.kmh)
        fun distance(value: Double, metricDecimals: Int) = number(
            if (mph) MathsUtil.kmToMiles(value) else value, if (mph) 2 else metricDecimals,
            if (mph) R.string.miles else R.string.km)
        val distanceKm = session.sessionDistance ?: session.wheelDistance ?: 0.0
        val values = mapOf(
            R.string.pwm to DashboardFormatting.format("%.2f%%", session.pwm?.takeIf { it.isFinite() } ?: 0.0),
            R.string.max_pwm to DashboardFormatting.format("%.2f%%", session.sessionMaxPwm ?: 0.0),
            R.string.voltage to number(session.currentVoltage, 2, R.string.volt),
            R.string.average_riding_speed to speed(bleViewModel.averageRidingSpeedDouble),
            R.string.riding_time to base.rideTimeFormatted,
            R.string.speed to speed((session.currentSpeed * 10).toInt() / 10.0),
            R.string.top_speed to speed(session.sessionTopSpeed ?: session.topSpeed ?: 0.0),
            R.string.distance to if (!mph && distanceKm < 1) number(distanceKm * 1000, 0, R.string.metre)
                else distance(distanceKm, 2),
            R.string.total to number(if (mph) MathsUtil.kmToMiles(session.totalDistance ?: 0.0)
                else session.totalDistance ?: 0.0, 0, if (mph) R.string.miles else R.string.km),
            R.string.battery to DashboardFormatting.format("%d %%", base.battery.coerceIn(0, 100)),
            R.string.current to number(session.currentCurrent, 1, R.string.amp),
            R.string.phase_current to number(bleViewModel.phaseCurrentDouble, 1, R.string.amp),
            R.string.maxcurrent to number(bleViewModel.maxCurrentDouble, 1, R.string.amp),
            R.string.maxphasecurrent to number(bleViewModel.maxPhaseCurrentDouble, 1, R.string.amp),
            R.string.power to number(bleViewModel.powerDouble, 0, R.string.watt),
            R.string.maxpower to number(bleViewModel.maxPowerDouble, 0, R.string.watt),
            R.string.temperature to base.temperatureDisplay,
            R.string.temperature2 to DashboardFormatting.temperature(
                bleViewModel.motorTemperatureDouble.toInt(), appConfig.useFahrenheit),
            R.string.maxtemperature to base.maxTemperatureDisplay,
            R.string.average_speed to speed(bleViewModel.averageSpeedDouble),
            R.string.ride_time to bleViewModel.rideTimeString,
            R.string.wheel_distance to distance(bleViewModel.wheelDistanceDouble, 3),
            R.string.remaining_distance to distance(bleViewModel.remainingDistance, 3),
            R.string.battery_per_km to DashboardFormatting.format("%.2f %%", bleViewModel.batteryPerKm),
            R.string.avg_cell_volt to number(bleViewModel.avgVoltagePerCell, 2, R.string.volt),
            // The original user-distance block deliberately remains in km.
            R.string.user_distance to number(bleViewModel.userDistanceDouble, 3, R.string.km),
            R.string.consumption to number(if (mph) Calculator.whByKm / MathsUtil.kmToMilesMultiplier
                else Calculator.whByKm, 1, if (mph) R.string.whmi else R.string.whkm)
        )
        val catalogue = DashboardCatalogue.labels.map { DashboardBlock(unit(it), values.getValue(it), metric = it) }
        val titles = appConfig.viewBlocks.toList()
        val aliases = if (titles.any { title -> catalogue.none { it.label == title } }) legacyAliases else emptyMap()
        return base.copy(
            speedUnit = unit(if (mph) R.string.mph else R.string.kmh),
            catalogue = catalogue,
            infoBlocks = DashboardCatalogue.select(titles, catalogue, aliases),
            batteryCalculation = if (appConfig.useBetterPercents || appConfig.customPercents) {
                if (appConfig.customPercents && !bleViewModel.isVoltageTiltbackUnsupported) "custom" else "true"
            } else ""
        )
    }
}
