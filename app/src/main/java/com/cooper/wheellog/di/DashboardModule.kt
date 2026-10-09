package com.cooper.wheellog.di

import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.feature.dashboard.DashboardViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for dashboard feature dependencies.
 *
 * [DashboardViewModel] takes the caller's [BleSessionViewModel] instance as a
 * parameter so it shares the same ViewModel (and the same [BleSessionState] flow)
 * as the rest of the screen.  Inject it with:
 *
 * ```kotlin
 * val bleVm: BleSessionViewModel = koinInject()
 * val dashVm: DashboardViewModel = koinViewModel { parametersOf(bleVm) }
 * ```
 */
val dashboardModule = module {
    viewModel { (bleVm: BleSessionViewModel) ->
        DashboardViewModel(androidApplication(), bleVm, get())
    }
}
