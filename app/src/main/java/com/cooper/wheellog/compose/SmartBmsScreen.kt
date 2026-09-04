package com.cooper.wheellog.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooper.wheellog.ble.BleSessionViewModel
import org.koin.compose.koinInject
import java.util.Locale

@Composable
fun SmartBmsScreen() {
    val viewModel: BleSessionViewModel = koinInject()
    val state by viewModel.sessionState.collectAsState()

    val bms1 = viewModel.bms1
    val bms2 = viewModel.bms2
    val hasSmartBms = bms1.cellNum > 0 || bms2.cellNum > 0 || !state.cellVoltages.isNullOrEmpty()
    val cellVoltages = state.cellVoltages.orEmpty().filter { it > 0.0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!hasSmartBms) {
            Text("Battery", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Level: ${state.batteryLevel}%")
            Text("Voltage: ${String.format("%.2f V", state.currentVoltage)}")
            Text("Current: ${String.format("%.2f A", state.currentCurrent)}")
            Text("Temperature: ${String.format("%.1f°C", state.currentTemperature)}")
        } else {
            if (bms1.cellNum > 0 || cellVoltages.isNotEmpty()) {
                Text("BMS 1", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                BmsBlock(
                    bms = bms1,
                    fallbackCells = cellVoltages
                )
            }

            if (bms2.cellNum > 0) {
                Spacer(Modifier.height(12.dp))

                Text("BMS 2", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                BmsBlock(bms2)
            }
        }
    }
}

@Composable
private fun BmsBlock(
    bms: com.cooper.wheellog.utils.SmartBms,
    fallbackCells: List<Double> = emptyList()
) {
    val cells = when {
        bms.cellNum > 0 -> bms.cells.take(bms.cellNum)
        fallbackCells.isNotEmpty() -> fallbackCells
        else -> emptyList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Voltage: ${String.format("%.2f V", bms.voltage)}")
        Text("Current: ${String.format("%.2f A", bms.current)}")
        Text("Level: ${bms.remPerc}%")
        Text("Charging: ${bms.status}%")
        Text("Temp 1: ${String.format("%.1f°C", bms.temp1)}")
        Text("Temp 2: ${String.format("%.1f°C", bms.temp2)}")
        Text("Cells: ${cells.size}")
        Text("Avg Cell: ${String.format("%.3f V", bms.avgCell)}")
        Text("Max Cell: ${String.format("%.3f V", bms.maxCell)}")
        Text("Min Cell: ${String.format("%.3f V", bms.minCell)}")
        Text("Cell Diff: ${String.format("%.3f V", bms.cellDiff)}")

        cells.forEachIndexed { index, value ->
            Text("Cell ${index + 1}: ${String.format(Locale.US, "%.3f V", value)}")
        }
    }
}