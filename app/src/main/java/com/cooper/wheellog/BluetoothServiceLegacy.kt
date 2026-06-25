package com.cooper.wheellog

import android.content.Context
import com.cooper.wheellog.ble.BleSessionViewModel
import org.koin.java.KoinJavaComponent

/**
 * LEGACY COMPATIBILITY LAYER - TO BE REMOVED AFTER MIGRATION
 * 
 * This file provides temporary compatibility with the old BluetoothService
 * by delegating to BleSessionViewModel. All BLE operations should now
 * go through BleSessionViewModel or EucBleClient directly.
 * 
 * THIS FILE SHOULD BE DELETED ONCE MIGRATION IS COMPLETE.
 */
@Deprecated("Use BleSessionViewModel directly. This compatibility layer will be removed.")
object BluetoothServiceLegacy {
    
    private val viewModel: BleSessionViewModel by lazy {
        val app = KoinJavaComponent.get<Context>(Context::class.java).applicationContext
        BleSessionViewModel(app)
    }
    
    var wheelAddress: String = ""
        set(value) {
            if (value.isNotEmpty()) {
                field = value
            }
        }
    
    // ========== CONNECTION STATE ==========
    
    val connectionState: Any // ConnectionState
        get() = viewModel.sessionState.value.connectionState
    
    fun connect(address: String) {
        // In the new architecture, connection is handled by BleSessionViewModel
        // This is a no-op for compatibility
    }
    
    fun disconnect() {
        viewModel.disconnect()
    }
    
    fun startScan() {
        viewModel.startScan()
    }
    
    fun stopScan() {
        viewModel.stopScan()
    }
    
    // ========== SERVICE LIFECYCLE ==========
    
    class LocalBinder {
        fun getService(): Any {
            // Return the viewModel as a compatibility layer
            return viewModel
        }
    }
    
    // ========== COMPATIBILITY PROPERTIES ==========
    
    fun getApplicationContext(): Context {
        return KoinJavaComponent.get<Context>(Context::class.java).applicationContext
    }
}
