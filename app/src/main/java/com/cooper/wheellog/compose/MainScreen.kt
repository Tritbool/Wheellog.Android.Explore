package com.cooper.wheellog.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.views.WheelView
import com.cooper.wheellog.feature.dashboard.DashboardActions
import com.cooper.wheellog.feature.dashboard.DashboardViewModel
import kotlinx.coroutines.flow.collect
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

enum class Page { Main, Params, Trips, Events, BMS }

@Composable
fun MainScreen(appConfig: AppConfig = koinInject()) {
    val pages = listOf( Page.Main, Page.Params, Page.Trips, Page.Events, Page.BMS )
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = pages.size // все страницы кешируются и не перерендериваются
            ) { index ->
                when (pages[index]) {
                    Page.Main -> if (appConfig.useComposeUI) MainPageScreen() else LegacyMainView()
                    Page.Params -> ParamsListScreen()
                    Page.Trips -> TripsScreen()
                    Page.Events -> EventsScreen()
                    Page.BMS -> SmartBmsScreen()
                }
            }
        }
    }
}


@Composable
fun LegacyMainView(viewModel: BleSessionViewModel = koinInject(),
                   dashboard: DashboardViewModel = koinViewModel { parametersOf(viewModel) }) {
    val appConfig: AppConfig = koinInject()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val actions = remember(context, viewModel, appConfig) { DashboardActions(context, viewModel, appConfig) }
    DisposableEffect(actions) { onDispose { actions.dispose() } }
    val state by produceState(dashboard.uiState.value, owner, dashboard) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            actions.start()
            try { dashboard.uiState.collect { value = it } }
            finally { actions.dispose() }
        }
    }

    AndroidView(
        factory = { ctx -> WheelView(ctx, null) },
        update = { view ->
            view.render(state, actions)
        }
    )
}