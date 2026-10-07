package com.cooper.wheellog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.cooper.wheellog.compose.ScanScreen
import com.cooper.wheellog.databinding.ActivityScanBinding
import com.cooper.wheellog.scan.ScanUiState
import com.cooper.wheellog.ui.theme.AppTheme

internal class ScanPageRenderer(
    private val binding: ActivityScanBinding,
    initial: ScanUiState,
    private val onSelect: (String) -> Unit,
    private val onForceProtocol: (String) -> Unit,
    private val onAddressChanged: (String) -> Unit,
    private val onManualSelect: () -> Unit
) {
    private val state = mutableStateOf(initial)
    private val scroll = LazyListState()
    private var disposed = false
    private var running = true

    init {
        binding.root.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        render(initial)
    }

    fun render(data: ScanUiState) {
        if (disposed) return
        state.value = data
        if (running && !binding.root.hasComposition) {
            binding.root.setContent {
                AppTheme(useDarkTheme = false) {
                    ScanScreen(state.value, scroll, onSelect, onForceProtocol, onAddressChanged, onManualSelect)
                }
            }
        }
    }

    fun stop() {
        running = false
        binding.root.disposeComposition()
    }

    fun start(data: ScanUiState) {
        if (disposed) return
        running = true
        render(data)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        stop()
    }
}
