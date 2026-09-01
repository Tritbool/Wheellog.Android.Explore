package com.cooper.wheellog.di

import com.cooper.wheellog.ble.BleSessionViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

/**
 * Koin module for BLE-related dependencies.
 * This module provides the BleSessionViewModel which encapsulates
 * the EucBleClient and manages the BLE session state.
 *
 * IMPORTANT: BleSessionViewModel MUST be registered as a Koin `single`
 * (not `viewModel`). It replaces the legacy WheelData singleton and is
 * shared across Activities, the LoggingService, Compose screens, and
 * plain Koin components (MainPageAdapter, AppConfig, DialogHelper).
 * Registering it as a Koin `viewModel` definition makes plain `get()`/
 * `by inject()` resolutions (used outside of ViewModelStoreOwners)
 * create a brand-new, disconnected instance on every call, since Koin's
 * ViewModel definitions are factories under the hood and only share a
 * single instance when resolved through `by viewModel()`/`koinViewModel()`
 * with the Android ViewModelStore. That disconnected instance never
 * receives BLE telemetry, which breaks UI updates after connecting.
 */
val bleModule = module {

    // Shared BLE session singleton for the app.
    single<BleSessionViewModel> {
        BleSessionViewModel(androidApplication())
    }
    
    // Provide EucBleClient directly if needed
    single { 
        val viewModel: BleSessionViewModel = get()
        viewModel.getEucBleClient()
    }
    
    // Provide BleSessionState flow for components that need to observe it
    single { 
        val viewModel: BleSessionViewModel = get()
        viewModel.sessionState
    }
}
