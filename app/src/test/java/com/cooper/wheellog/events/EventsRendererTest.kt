package com.cooper.wheellog.events

import android.app.Application
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.EventsLoggingTree
import com.cooper.wheellog.EventsPageRenderer
import com.cooper.wheellog.EventsState
import com.cooper.wheellog.MainActivity
import com.cooper.wheellog.MainPageAdapter
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionViewModel
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class EventsRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `renderer preserves XML appearance fonts and independent scroll while switching`() {
        context.setTheme(R.style.OriginalTheme)
        val page = LayoutInflater.from(context).inflate(R.layout.main_view_events, null)
        val state = EventsState()
        state.append((1..100).joinToString("\n", postfix = "\n") { "event-$it" })
        val scroll = ScrollState(27)
        val renderer = EventsPageRenderer(page, state, scroll, R.style.OriginalTheme)
        try {
            val text = page.findViewById<TextView>(R.id.events_textbox)
            val baseline = TextView(context)
            assertThat(text.text.toString()).isEqualTo(state.text.value)
            assertThat(text.textSize).isEqualTo(baseline.textSize)
            assertThat(text.currentTextColor).isEqualTo(baseline.currentTextColor)
            assertThat(text.paddingLeft).isEqualTo(0)
            assertThat(text.paddingTop).isEqualTo(0)
            renderer.preferences(false, R.style.OriginalTheme)
            assertThat(text.typeface).isEqualTo(ResourcesCompat.getFont(context, R.font.prime))
            page.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
            )
            page.layout(0, 0, 400, 100)
            val views = page.findViewById<ScrollView>(R.id.events_views_scroll)
            views.scrollTo(0, 42)
            renderer.preferences(true, R.style.AJDMTheme)
            assertThat(text.typeface).isEqualTo(ResourcesCompat.getFont(context, R.font.ajdm))
            renderer.preferences(false, R.style.OriginalTheme)
            assertThat(views.scrollY).isEqualTo(42)
            assertThat(scroll.value).isEqualTo(27)
        } finally {
            renderer.dispose()
        }
    }

    @Test
    fun `live snapshot collection stops when lifecycle stops or renderer detaches`() {
        context.setTheme(R.style.OriginalTheme)
        val page = LayoutInflater.from(context).inflate(R.layout.main_view_events, null)
        val state = EventsState()
        val owner = mockk<LifecycleOwner>()
        val lifecycle = LifecycleRegistry(owner)
        every { owner.lifecycle } returns lifecycle
        lifecycle.currentState = Lifecycle.State.CREATED
        val renderer = EventsPageRenderer(page, state, ScrollState(0), R.style.OriginalTheme)
        val text = page.findViewById<TextView>(R.id.events_textbox)
        try {
            renderer.start(owner)
            lifecycle.currentState = Lifecycle.State.STARTED
            idle()
            state.append("first\n")
            idle()
            assertThat(text.text.toString()).isEqualTo("first\n")
            lifecycle.currentState = Lifecycle.State.CREATED
            idle()
            state.append("second\n")
            idle()
            assertThat(text.text.toString()).isEqualTo("first\n")
            lifecycle.currentState = Lifecycle.State.STARTED
            idle()
            assertThat(text.text.toString()).isEqualTo("first\nsecond\n")
            renderer.stop()
            idle()
            state.append("third\n")
            idle()
            assertThat(text.text.toString()).isEqualTo("first\nsecond\n")
            renderer.start(owner)
            idle()
            assertThat(text.text.toString()).isEqualTo(state.text.value)
            renderer.dispose()
            idle()
            state.append("fourth\n")
            idle()
            assertThat(text.text.toString()).isEqualTo("first\nsecond\nthird\n")
        } finally {
            renderer.dispose()
            lifecycle.currentState = Lifecycle.State.DESTROYED
            idle()
        }
    }

    @Test
    fun `real pager events route defaults to Compose with live independent fallback without BLE`() {
        context.setTheme(R.style.OriginalTheme)
        val config = AppConfig(context)
        val model = mockk<BleSessionViewModel>(relaxed = true)
        val activity = mockk<MainActivity>(relaxed = true)
        val lifecycle = LifecycleRegistry(activity)
        every { activity.lifecycle } returns lifecycle
        startKoin { modules(module {
            single { config }
            single { model }
        }) }
        val recycler = RecyclerView(context)
        val adapter = MainPageAdapter(mutableListOf(R.layout.main_view_events), activity)
        EventsLoggingTree.events.replace("history\n------------\n")
        try {
            lifecycle.currentState = Lifecycle.State.CREATED
            val holder = adapter.createViewHolder(recycler, R.layout.main_view_events)
            adapter.bindViewHolder(holder, 0)
            val page = holder.itemView
            val views = page.findViewById<ScrollView>(R.id.events_views_scroll)
            val compose = page.findViewById<ComposeView>(R.id.eventsComposeView)
            val text = page.findViewById<TextView>(R.id.events_textbox)
            assertThat(views.visibility).isEqualTo(View.GONE)
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            assertThat(text.text.toString()).isEqualTo(EventsLoggingTree.events.text.value)
            adapter.onAttachedToRecyclerView(recycler)
            adapter.onViewAttachedToWindow(holder)
            lifecycle.currentState = Lifecycle.State.STARTED
            idle()
            EventsLoggingTree.events.append("live without telemetry\n")
            idle()
            assertThat(text.text.toString()).isEqualTo(EventsLoggingTree.events.text.value)
            config.useComposeTelemetry = false
            idle()
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            config.useComposeEvents = false
            idle()
            assertThat(views.visibility).isEqualTo(View.VISIBLE)
            assertThat(compose.visibility).isEqualTo(View.GONE)
            EventsLoggingTree.events.append("fallback live\n")
            idle()
            assertThat(text.text.toString()).isEqualTo(EventsLoggingTree.events.text.value)
            config.useComposeEvents = true
            idle()
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            assertThat(holder.itemView).isSameInstanceAs(page)
            assertThat(adapter.itemCount).isEqualTo(1)
            assertThat(config.useComposeTelemetry).isFalse()
            adapter.onViewDetachedFromWindow(holder)
            idle()
            val last = text.text.toString()
            EventsLoggingTree.events.append("detached\n")
            idle()
            assertThat(text.text.toString()).isEqualTo(last)
            adapter.onViewAttachedToWindow(holder)
            idle()
            assertThat(text.text.toString()).isEqualTo(EventsLoggingTree.events.text.value)
            adapter.onViewRecycled(holder)
            idle()
            val recycled = text.text.toString()
            EventsLoggingTree.events.append("recycled\n")
            idle()
            assertThat(text.text.toString()).isEqualTo(recycled)
            assertThat(holder.eventsRenderer).isNull()
            val insertions = mutableListOf<Int>()
            adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    insertions.add(positionStart)
                }
            })
            config.pageEvents = true
            idle()
            config.pageEvents = false
            idle()
            assertThat(adapter.itemCount).isEqualTo(0)
            config.pageEvents = true
            idle()
            assertThat(adapter.itemCount).isEqualTo(1)
            assertThat(insertions).containsExactly(0)
            verify { model wasNot Called }
        } finally {
            adapter.onDetachedFromRecyclerView(recycler)
            lifecycle.currentState = Lifecycle.State.DESTROYED
            idle()
            EventsLoggingTree.events.replace("")
            stopKoin()
        }
    }
}
