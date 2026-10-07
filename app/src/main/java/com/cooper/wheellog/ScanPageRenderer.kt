package com.cooper.wheellog

import android.view.View
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.widget.doAfterTextChanged
import com.cooper.wheellog.compose.ScanScreen
import com.cooper.wheellog.databinding.ActivityScanBinding
import com.cooper.wheellog.scan.ScanUiState

internal class ScanPageRenderer(
    private val binding: ActivityScanBinding,
    private val adapter: DeviceListAdapter,
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
    private var composeActive = false
    private var renderedDevices = emptyList<com.cooper.wheellog.scan.ScanDeviceItem>()
    private val input = binding.manualAddress.root

    init {
        binding.list.adapter = adapter
        binding.list.setOnItemClickListener { _, _, index, _ ->
            state.value.devices.getOrNull(index)?.let { onSelect(it.address) }
        }
        binding.list.setOnItemLongClickListener { _, _, index, _ ->
            state.value.devices.getOrNull(index)?.let { onForceProtocol(it.address) }
            true
        }
        input.editText!!.setText(initial.manualAddress)
        input.editText!!.doAfterTextChanged { onAddressChanged(it.toString()) }
        input.setEndIconOnClickListener { onManualSelect() }
        binding.scanComposeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        render(initial, false)
    }

    fun render(data: ScanUiState, useCompose: Boolean) {
        if (disposed) return
        state.value = data
        if (renderedDevices != data.devices) {
            renderedDevices = data.devices
            if (adapter.setDevices(data.devices.map { it.device })) adapter.notifyDataSetChanged()
        }
        binding.scanProgress.visibility = if (data.scanning) View.VISIBLE else View.GONE
        binding.scanTitle.setText(if (data.scanning) R.string.scanning else R.string.devices)
        input.visibility = if (data.scanning) View.GONE else View.VISIBLE
        if (input.editText!!.text.toString() != data.manualAddress) input.editText!!.setText(data.manualAddress)
        input.error = if (data.invalidAddress) "incorrect MAC" else null
        input.errorIconDrawable = null
        binding.scanViewsContent.visibility = if (useCompose) View.GONE else View.VISIBLE
        binding.scanComposeView.visibility = if (useCompose) View.VISIBLE else View.GONE
        if (running && useCompose && !binding.scanComposeView.hasComposition) {
            binding.scanComposeView.setContent {
                ScanScreen(state.value, scroll, onSelect, onForceProtocol, onAddressChanged, onManualSelect)
            }
        } else if (!useCompose && composeActive) {
            binding.scanComposeView.disposeComposition()
        }
        composeActive = useCompose
    }

    fun stop() {
        running = false
        binding.scanComposeView.disposeComposition()
    }

    fun start(data: ScanUiState, useCompose: Boolean) {
        if (disposed) return
        running = true
        render(data, useCompose)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        stop()
        binding.list.onItemClickListener = null
        binding.list.onItemLongClickListener = null
        input.setEndIconOnClickListener(null)
    }
}
