package com.cooper.wheellog

import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.bms.BmsMapper
import com.cooper.wheellog.compose.SmartBmsScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class BmsScroll {
    val compose = ScrollState(0)
}

internal class BmsPageRenderer(
    page: View,
    private val session: BleSessionViewModel,
    private val config: AppConfig,
    private val scroll: BmsScroll,
    private val owner: LifecycleOwner
) {
    private val compose = page.findViewById<ComposeView>(R.id.bmsComposeView)
    private val presentation = mutableStateOf(BmsMapper.present(session.bmsDisplay.value))
    private val theme = mutableStateOf(config.appTheme)
    private var collecting: Job? = null
    private var disposed = false

    init {
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { SmartBmsScreen(presentation.value, theme.value, scroll.compose) }
    }

    fun preferences(appTheme: Int) {
        if (!disposed) theme.value = appTheme
    }

    fun refresh() {
        if (!disposed) presentation.value = BmsMapper.present(session.bmsDisplay.value)
    }

    fun start() {
        if (disposed || collecting?.isActive == true) return
        collecting = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(session.bmsDisplay, config.telemetryPreferences()) { snapshot, prefs -> snapshot to prefs }
                    .collect { (snapshot, prefs) ->
                        preferences(prefs.appTheme)
                        presentation.value = BmsMapper.present(snapshot)
                    }
            }
        }
    }

    fun stop() {
        collecting?.cancel()
        collecting = null
    }

    fun dispose() {
        if (disposed) return
        stop()
        disposed = true
        compose.disposeComposition()
    }
}
