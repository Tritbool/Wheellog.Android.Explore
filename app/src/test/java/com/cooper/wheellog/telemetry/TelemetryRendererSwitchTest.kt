package com.cooper.wheellog.telemetry

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.MainActivity
import com.cooper.wheellog.MainPageAdapter
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionViewModel
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
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
class TelemetryRendererSwitchTest {
    @Test
    fun `bound params page switches without BLE and catches up after lifecycle restart`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.setTheme(R.style.OriginalTheme)
        val config = AppConfig(context)
        config.useComposeTelemetry = false
        val model = mockk<BleSessionViewModel>(relaxed = true)
        val activity = mockk<MainActivity>(relaxed = true)
        val lifecycle = LifecycleRegistry(activity)
        every { activity.lifecycle } returns lifecycle
        startKoin { modules(module {
            single { config }
            single { model }
        }) }
        val recycler = RecyclerView(context)
        val adapter = MainPageAdapter(mutableListOf(R.layout.main_view_params_list), activity)
        try {
            lifecycle.currentState = Lifecycle.State.CREATED
            val holder = adapter.createViewHolder(recycler, R.layout.main_view_params_list)
            adapter.bindViewHolder(holder, 0)
            val page = holder.itemView
            val views = page.findViewById<View>(R.id.params_views_scroll)
            val compose = page.findViewById<ComposeView>(R.id.paramsComposeView)
            assertThat(views.visibility).isEqualTo(View.VISIBLE)
            assertThat(compose.visibility).isEqualTo(View.GONE)
            // Legacy binding removes the XML waiting message even before a wheel is selected.
            assertThat(page.findViewById<View>(R.id.tvWaitText)).isNull()

            adapter.onAttachedToRecyclerView(recycler)
            lifecycle.currentState = Lifecycle.State.STARTED
            shadowOf(Looper.getMainLooper()).idle()
            config.useComposeTelemetry = true
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(views.visibility).isEqualTo(View.GONE)
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            assertThat(holder.itemView).isSameInstanceAs(page)
            assertThat(adapter.itemCount).isEqualTo(1)

            lifecycle.currentState = Lifecycle.State.CREATED
            shadowOf(Looper.getMainLooper()).idle()
            config.useComposeTelemetry = false
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            lifecycle.currentState = Lifecycle.State.STARTED
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(views.visibility).isEqualTo(View.VISIBLE)
            assertThat(compose.visibility).isEqualTo(View.GONE)

            adapter.onDetachedFromRecyclerView(recycler)
            shadowOf(Looper.getMainLooper()).idle()
            config.useComposeTelemetry = true
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(compose.visibility).isEqualTo(View.GONE)
        } finally {
            adapter.onDetachedFromRecyclerView(recycler)
            lifecycle.currentState = Lifecycle.State.DESTROYED
            shadowOf(Looper.getMainLooper()).idle()
            stopKoin()
        }
    }
}
