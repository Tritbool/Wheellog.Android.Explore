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
fun SmartBmsScreen() {
    val viewModel: BleSessionViewModel = koinViewModel()
    val state by viewModel.sessionState.collectAsState()

    val bms1 = viewModel.bms1
    val bms2 = viewModel.bms2
    val hasSmartBms = bms1.cellNum > 0 || bms2.cellNum > 0 || !state.cellVoltages.isNullOrEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!hasSmartBms) {
            Text("Battery", fontSize = 16.sp)
            Text("Level: ${state.batteryLevel}%")
            Text("Voltage: ${String.format("%.2f V", state.currentVoltage)}")
            Text("Current: ${String.format("%.2f A", state.currentCurrent)}")
            Text("Temperature: ${String.format("%.1f°C", state.currentTemperature)}")
        } else {
            Text("BMS 1", fontSize = 16.sp)
            BmsBlock(bms1)

            Spacer(Modifier.height(12.dp))

            Text("BMS 2", fontSize = 16.sp)
            BmsBlock(bms2)
        }
    }
}

@Composable
private fun BmsBlock(bms: com.cooper.wheellog.utils.SmartBms) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Voltage: ${String.format("%.2f V", bms.voltage)}")
        Text("Current: ${String.format("%.2f A", bms.current)}")
        Text("Temp 1: ${String.format("%.1f°C", bms.temp1)}")
        Text("Temp 2: ${String.format("%.1f°C", bms.temp2)}")
        Text("Max Cell: ${String.format("%.3f V", bms.maxCell)}")
        Text("Min Cell: ${String.format("%.3f V", bms.minCell)}")
        Text("Cell Diff: ${String.format("%.3f V", bms.cellDiff)}")
    }
}