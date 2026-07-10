package com.cooper.wheellog.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooper.wheellog.utils.MathsUtil
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.Locale

@Composable
fun ParamsListScreen(viewModel: BleSessionViewModel = koinViewModel()) {
    val appConfig: AppConfig = koinInject()
    val useMph = appConfig.useMph

    val items = listOf(
        "Speed" to formatSpeed(viewModel.speedDouble, useMph),
        "Top Speed" to formatSpeed(viewModel.topSpeedDouble, useMph),
        "Average Speed" to formatSpeed(viewModel.averageSpeedDouble, useMph),
        "Average Riding Speed" to formatSpeed(viewModel.averageRidingSpeedDouble, useMph),
        "Distance" to formatDistance(viewModel.distanceDouble, useMph),
        "Wheel Distance" to formatDistance(viewModel.wheelDistanceDouble, useMph),
        "User Distance" to formatDistance(viewModel.userDistanceDouble, useMph),
        "Total Distance" to formatDistance(viewModel.totalDistanceDouble, useMph),
        "Voltage" to String.format(Locale.US, "%.2f V", viewModel.voltageDouble),
        "Voltage Sag" to String.format(Locale.US, "%.2f V", viewModel.voltageSagDouble),
        "Current" to String.format(Locale.US, "%.2f A", viewModel.currentDouble),
        "Power" to String.format(Locale.US, "%.2f W", viewModel.powerDouble),
        "Motor Power" to String.format(Locale.US, "%.2f W", viewModel.motorPower),
        "Battery" to "${viewModel.batteryLevel}%",
        "Temperature" to "${viewModel.temperature}°C",
        "Temperature 2" to "${viewModel.temperature2}°C",
        "CPU Temp" to "${viewModel.cpuTemp}°C",
        "IMU Temp" to "${viewModel.imuTemp}°C",
        "Angle" to String.format(Locale.US, "%.2f°", viewModel.angle),
        "Roll" to String.format(Locale.US, "%.2f°", viewModel.roll),
        "Ride Time" to viewModel.rideTimeString,
        "Riding Time" to viewModel.ridingTimeString,
        "Mode" to viewModel.modeStr,
        "Model" to viewModel.model,
        "Version" to viewModel.version,
        "Serial" to viewModel.serial
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
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