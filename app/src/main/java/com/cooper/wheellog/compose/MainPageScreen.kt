package com.cooper.wheellog.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooper.wheellog.ble.BleSessionViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainPageScreen(viewModel: BleSessionViewModel = koinViewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Speed: ${viewModel.speedDouble} km/h", fontSize = 20.sp)
        Text("Battery: ${viewModel.batteryLevel}%", fontSize = 20.sp)
        Text("Temperature: ${viewModel.temperature}°C", fontSize = 20.sp)
        Text("Distance: ${viewModel.distanceDouble} km", fontSize = 20.sp)
        Text("Voltage: ${viewModel.voltageDouble} V", fontSize = 20.sp)
        Text("Current: ${viewModel.currentDouble} A", fontSize = 20.sp)
    }
}