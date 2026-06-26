package com.cooper.wheellog.compose

import androidx.compose.runtime.*
import com.cooper.wheellog.ble.EucBleManager
import org.koin.compose.koinInject

@Stable
@Composable
fun eucBleManager(): EucBleManager = koinInject()

// WheelDataComposeBridge is deprecated - use eucBleManager() directly
@Deprecated("Use eucBleManager() directly. This bridge will be removed.")
object WheelDataComposeBridge {
    // This bridge is kept for backward compatibility during migration
    // It should be removed once all Compose screens are migrated
}