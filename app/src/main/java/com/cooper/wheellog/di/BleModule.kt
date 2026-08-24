package com.cooper.wheellog.di

import android.app.Application
import androidx.lifecycle.ViewModel
import com.cooper.wheellog.ble.BleSessionViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for BLE-related dependencies.
 * This module provides the BleSessionViewModel which encapsulates
 * the EucBleClient and manages the BLE session state.
 */
val bleModule = module {
    
    // ViewModel for BLE session management
    viewModel<BleSessionViewModel> { 
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
