package com.cooper.wheellog.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.views.WheelView
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

enum class Page { Main, Params, Trips, Events, BMS }

@Composable
fun MainScreen() {
    val pages = listOf( Page.Main, Page.Params, Page.Trips, Page.Events, Page.BMS )
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = pages.size // все страницы кешируются и не перерендериваются
            ) { index ->
                // Our page content
                when (pages[index]) {
                    Page.Main -> LegacyMainView()
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
fun LegacyMainView(viewModel: BleSessionViewModel = koinViewModel()) {
    val state by viewModel.sessionState.collectAsState()
    val appConfig: AppConfig = koinInject()

    AndroidView(
        factory = { ctx -> WheelView(ctx, null) },
        update = { view ->
            // Bind the update block to session state emissions so telemetry refreshes the view.
            state.lastDataTimestamp

            view.apply {
                setSpeed((viewModel.speedDouble * 10).toInt())
                setBattery(viewModel.batteryLevel)
                setBatteryLowest(viewModel.batteryLowestLevel)
                setTemperature(viewModel.temperatureDouble.toInt())
                setRideTime(viewModel.ridingTimeString)
                setTopSpeed(viewModel.topSpeedDouble)
                setDistance(viewModel.distanceDouble)
                setTotalDistance(viewModel.totalDistanceDouble)
                setVoltage(viewModel.voltageDouble)
                setCurrent(viewModel.currentDouble)
                setPhaseCurrent(viewModel.phaseCurrentDouble)
                setAverageSpeed(viewModel.averageRidingSpeedDouble)
                setMaxPwm(viewModel.maxPwm)
                setMaxTemperature(viewModel.maxTemp.toInt())
                setPwm(viewModel.calculatedPwm)
                updateViewBlocksVisibility()
                redrawTextBoxes()
                invalidate()

                var profileName = appConfig.profileName
                if (profileName.trim { it <= ' ' } == "") {
                    profileName = if (viewModel.model == "") viewModel.name else viewModel.model
                }
                setWheelModel(profileName)
            }
        }
    )
}