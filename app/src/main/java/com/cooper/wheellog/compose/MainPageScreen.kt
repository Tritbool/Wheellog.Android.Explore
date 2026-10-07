package com.cooper.wheellog.compose

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.feature.dashboard.*
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun MainPageScreen(state: DashboardUiState, actions: DashboardActions) {
    DashboardGauge(state, actions = actions)
}

@Composable
fun MainPageScreen(
    bleViewModel: BleSessionViewModel = koinInject(),
    dashboardViewModel: DashboardViewModel = koinViewModel { parametersOf(bleViewModel) },
    config: AppConfig = koinInject()
) {
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val actions = remember(context, bleViewModel, config) { DashboardActions(context, bleViewModel, config) }
    DisposableEffect(actions) { onDispose { actions.dispose() } }
    val state by produceState(dashboardViewModel.uiState.value, owner, dashboardViewModel) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            actions.start()
            try { dashboardViewModel.uiState.collect { value = it } }
            finally { actions.dispose() }
        }
    }
    MainPageScreen(state, actions)
}
