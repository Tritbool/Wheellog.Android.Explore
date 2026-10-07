package com.cooper.wheellog

import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.compose.MainPageScreen
import com.cooper.wheellog.feature.dashboard.DashboardActions
import com.cooper.wheellog.feature.dashboard.DashboardViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class DashboardPageRenderer(
    page: View,
    session: BleSessionViewModel,
    config: AppConfig,
    private val owner: MainActivity
) {
    private val compose = page.findViewById<ComposeView>(R.id.mainPageComposeView)
    private val actions = DashboardActions(page.context, session, config)
    private val model = ViewModelProvider(owner, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(owner.application, session, config) as T
    })[DashboardViewModel::class.java]
    private val presentation = mutableStateOf(model.uiState.value)
    private var collecting: Job? = null
    private var disposed = false

    init {
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        render(presentation.value)
    }

    private fun render(data: com.cooper.wheellog.feature.dashboard.DashboardUiState) {
        presentation.value = data
        if (!compose.hasComposition) {
            compose.setContent { MainPageScreen(presentation.value, actions) }
        }
    }

    fun start() {
        if (disposed || collecting?.isActive == true) return
        collecting = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                actions.start()
                try {
                    model.uiState.collect { render(it) }
                } finally {
                    actions.dispose()
                    compose.disposeComposition()
                }
            }
        }
    }

    fun stop() {
        collecting?.cancel()
        collecting = null
        actions.dispose()
        compose.disposeComposition()
    }

    fun resetBatteryLowest() { model.resetBatteryLowest() }

    fun dispose() { if (!disposed) { stop(); disposed = true } }
}
