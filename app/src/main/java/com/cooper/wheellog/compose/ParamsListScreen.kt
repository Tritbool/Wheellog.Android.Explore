package com.cooper.wheellog.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooper.wheellog.utils.MathsUtil
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionViewModel
import org.koin.compose.koinInject
import java.util.Locale

@Composable
fun ParamsListScreen(viewModel: BleSessionViewModel = koinInject()) {
    val appConfig: AppConfig = koinInject()
    val useMph = appConfig.useMph
    val state by viewModel.sessionState.collectAsState()
    val sessionDistance = state.sessionDistance ?: state.wheelDistance ?: 0.0

    val items = listOf(
        "Speed" to formatSpeed(state.currentSpeed, useMph),
        "Top Speed" to formatSpeed(state.sessionTopSpeed ?: viewModel.topSpeedDouble, useMph),
        "Average Speed" to formatSpeed(viewModel.averageSpeedDouble, useMph),
        "Average Riding Speed" to formatSpeed(viewModel.averageRidingSpeedDouble, useMph),
        "Distance" to formatDistance(sessionDistance, useMph),
        "Wheel Distance" to formatDistance(viewModel.wheelDistanceDouble, useMph),
        "User Distance" to formatDistance(viewModel.userDistanceDouble, useMph),
        "Total Distance" to formatDistance(state.totalDistance ?: 0.0, useMph),
        "Voltage" to String.format(Locale.US, "%.2f V", state.currentVoltage),
        "Voltage Sag" to String.format(Locale.US, "%.2f V", viewModel.voltageSagDouble),
        "Current" to String.format(Locale.US, "%.2f A", state.currentCurrent),
        "Power" to String.format(Locale.US, "%.2f W", state.currentPower),
        "Motor Power" to String.format(Locale.US, "%.2f W", viewModel.motorPower),
        "Battery" to "${state.batteryLevel}%",
        "Temperature" to "${state.currentTemperature.toInt()}°C",
        "Temperature 2" to "${viewModel.motorTemperature}°C",
        "CPU Temp" to "${viewModel.cpuTemp}°C",
        "IMU Temp" to "${viewModel.imuTemp}°C",
        "Output" to "${viewModel.output}%",
        "Angle" to String.format(Locale.US, "%.2f°", state.angle ?: 0.0),
        "Roll" to String.format(Locale.US, "%.2f°", state.lastData?.roll ?: 0.0),
        "Ride Time" to viewModel.rideTimeString,
        "Riding Time" to viewModel.ridingTimeString,
        "Sleep Timer" to viewModel.sleepTimerString,
        "Mode" to (state.lastData?.mode ?: ""),
        "Manufacturer" to state.deviceManufacturer,
        "Model" to state.deviceModel,
        "Version" to (state.firmwareVersion ?: "Unknown"),
        "Serial" to (state.serialNumber ?: "Unknown")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, fontSize = 16.sp)
                Text(value, fontSize = 18.sp)
            }
        }
    }
}

private fun formatSpeed(kmh: Double, useMph: Boolean): String =
    if (useMph) String.format(Locale.US, "%.1f mph", MathsUtil.kmToMiles(kmh))
    else String.format(Locale.US, "%.1f km/h", kmh)

private fun formatDistance(km: Double, useMph: Boolean): String =
    if (useMph) String.format(Locale.US, "%.2f mi", MathsUtil.kmToMiles(km))
    else String.format(Locale.US, "%.3f km", km)