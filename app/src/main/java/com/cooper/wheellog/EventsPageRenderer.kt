package com.cooper.wheellog

import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.res.ResourcesCompat
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
    private val views = page.findViewById<ScrollView>(R.id.events_views_scroll)
    private val textView = page.findViewById<TextView>(R.id.events_textbox)
    private val compose = page.findViewById<ComposeView>(R.id.eventsComposeView)
    private val text = mutableStateOf(state.text.value)
    private val theme = mutableStateOf(appTheme)
    private var job: Job? = null

    init {
        textView.text = text.value
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { EventsScreen(text.value, theme.value, scroll) }
    }

    fun preferences(useCompose: Boolean, appTheme: Int) {
        theme.value = appTheme
        textView.typeface = ResourcesCompat.getFont(
            textView.context, if (appTheme == R.style.AJDMTheme) R.font.ajdm else R.font.prime
        )
        views.visibility = if (useCompose) View.GONE else View.VISIBLE
        compose.visibility = if (useCompose) View.VISIBLE else View.GONE
    }

    fun start(owner: LifecycleOwner) {
        stop()
        job = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.text.collect {
                    text.value = it
                    textView.text = it
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun dispose() {
        stop()
        compose.disposeComposition()
    }
}
