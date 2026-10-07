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
import com.cooper.wheellog.compose.EventsScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class EventsPageRenderer(
    page: View,
    private val state: EventsState,
    scroll: ScrollState,
    appTheme: Int
) {
    private val compose = page.findViewById<ComposeView>(R.id.eventsComposeView)
    private val text = mutableStateOf(state.text.value)
    private val theme = mutableStateOf(appTheme)
    private var job: Job? = null
    private var disposed = false

    init {
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { EventsScreen(text.value, theme.value, scroll) }
    }

    fun preferences(appTheme: Int) {
        if (!disposed) theme.value = appTheme
    }

    fun start(owner: LifecycleOwner) {
        if (disposed) return
        stop()
        job = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.text.collect {
                    text.value = it
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun dispose() {
        if (disposed) return
        stop()
        disposed = true
        compose.disposeComposition()
    }
}
