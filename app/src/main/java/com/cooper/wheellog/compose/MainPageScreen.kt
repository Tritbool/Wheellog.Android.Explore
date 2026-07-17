package com.cooper.wheellog.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.feature.dashboard.DashboardViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Dashboard screen driven by [DashboardViewModel].
 *
 * All data comes through [DashboardViewModel.uiState] — no singletons or
 * SharedPreferences are accessed here.
 */
@Composable
fun MainPageScreen(
    bleViewModel: BleSessionViewModel = koinViewModel(),
    dashboardViewModel: DashboardViewModel = koinViewModel { parametersOf(bleViewModel) }
) {
    val state by dashboardViewModel.uiState.collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                com.cooper.wheellog.feature.dashboard.DashboardGauge(
                    state = state,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                    onToggleDisplayMode = dashboardViewModel::toggleDisplayMode
                )
                InfoBlockGrid(
                    blocks = state.infoBlocks,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                com.cooper.wheellog.feature.dashboard.DashboardGauge(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    onToggleDisplayMode = dashboardViewModel::toggleDisplayMode
                )
                InfoBlockGrid(
                    blocks = state.infoBlocks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoBlockGrid(
    blocks: List<com.cooper.wheellog.feature.dashboard.DashboardBlock>,
    modifier: Modifier = Modifier
) {
    if (blocks.isEmpty()) return

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(blocks) { block ->
            InfoBlock(label = block.label, value = block.value)
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x20FFFFFF), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            color = Color(0xAAFFFFFF),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}