package com.cooper.wheellog.bms

import android.app.Application
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.gridlayout.widget.GridLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.*
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.data.TripDao
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import com.cooper.wheellog.utils.SmartBms
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
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
class BmsRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun snapshot(cells: Int = 0, second: Int = 0, voltage: Double = 100.0) = BmsSnapshot(
        WHEEL_TYPE.GOTWAY, "Rocket", "", BmsMapper.pack(SmartBms().apply { cellNum = cells; this.voltage = voltage }),
        BmsMapper.pack(SmartBms().apply { cellNum = second }), 80, voltage, -2.0, 25.0, 35.0
    )

    @Before fun setup() {
        context.setTheme(R.style.OriginalTheme)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putInt("versionSettings", 1).commit()
    }

    @Test fun `actual pager defaults on independent persisted fallback retains page and recycles composition`() {
        val config = AppConfig(context)
        val model = mockk<BleSessionViewModel>(relaxed = true)
        val state = MutableStateFlow(snapshot(50, 50))
        every { model.bmsDisplay } returns state
        val activity = mockk<MainActivity>(relaxed = true)
        val lifecycle = LifecycleRegistry(activity)
        every { activity.lifecycle } returns lifecycle
        lifecycle.currentState = Lifecycle.State.CREATED
        startKoin { modules(module {
            single { config }; single { model }; single { mockk<TripDao>(relaxed = true) }
        }) }
        val recycler = RecyclerView(context)
        val adapter = MainPageAdapter(mutableListOf(R.layout.main_view_main, R.layout.main_view_params_list), activity)
        try {
            adapter.configureSmartBmsDisplay()
            assertThat(adapter.itemCount).isEqualTo(3)
            assertThat(adapter.getItemViewType(2)).isEqualTo(R.layout.main_view_smart_bms)
            val holder = adapter.createViewHolder(recycler, R.layout.main_view_smart_bms)
            adapter.bindViewHolder(holder, 2)
            val page = holder.itemView
            val compose = page.findViewById<ComposeView>(R.id.bmsComposeView)
            val views = page.findViewById<ScrollView>(R.id.bms_views_scroll)
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            assertThat(views.visibility).isEqualTo(View.GONE)
            config.useComposeTelemetry = false; config.useComposeEvents = false; config.useComposeTrips = false
            holder.bmsRenderer!!.preferences(config.useComposeBms, config.appTheme)
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            config.useComposeBms = false
            holder.bmsRenderer!!.preferences(config.useComposeBms, config.appTheme)
            assertThat(AppConfig(context).useComposeBms).isFalse()
            assertThat(views.visibility).isEqualTo(View.VISIBLE)
            val grid = page.findViewById<GridLayout>(R.id.page_smart_bms_grid)
            assertThat(grid.columnCount).isEqualTo(4)
            assertThat(grid.childCount).isEqualTo(2 + 62 * 4)
            config.useComposeBms = true
            holder.bmsRenderer!!.preferences(true, config.appTheme)
            assertThat(holder.itemView).isSameInstanceAs(page)
            adapter.position = 2
            verify { model.bmsView = true }
            adapter.position = 1
            verify { model.bmsView = false }
            clearMocks(model, answers = false, recordedCalls = true)
            state.value = snapshot()
            holder.bmsRenderer!!.refresh()
            assertThat(grid.columnCount).isEqualTo(2)
            assertThat(grid.childCount).isEqualTo(11)
            verify(exactly = 0) { model.bmsView = any() }
            adapter.onViewRecycled(holder)
            assertThat(holder.bmsRenderer).isNull()
        } finally {
            adapter.onDetachedFromRecyclerView(recycler)
            lifecycle.currentState = Lifecycle.State.DESTROYED
            idle()
            stopKoin()
        }
    }

    @Test fun `lifecycle collection updates values stops on detach and releases preference listeners`() {
        val preferences = spyk(PreferenceManager.getDefaultSharedPreferences(context))
        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(context) } returns preferences
        val config = AppConfig(context)
        val model = mockk<BleSessionViewModel>()
        val state = MutableStateFlow(snapshot())
        every { model.bmsDisplay } returns state
        val owner = mockk<LifecycleOwner>()
        val lifecycle = LifecycleRegistry(owner)
        every { owner.lifecycle } returns lifecycle
        lifecycle.currentState = Lifecycle.State.CREATED
        val page = LayoutInflater.from(context).inflate(R.layout.main_view_smart_bms, null)
        val renderer = BmsPageRenderer(page, model, config, BmsScroll(), owner)
        val grid = page.findViewById<GridLayout>(R.id.page_smart_bms_grid)
        fun voltage() = (grid.getChildAt(4) as TextView).text.toString()
        try {
            renderer.start()
            idle()
            assertThat(voltage()).isEqualTo("100.00 V")
            state.value = snapshot(voltage = 101.0)
            idle()
            assertThat(voltage()).isEqualTo("100.00 V")
            lifecycle.currentState = Lifecycle.State.STARTED
            idle()
            assertThat(voltage()).isEqualTo("101.00 V")
            config.useComposeBms = false
            config.useFahrenheit = true
            idle()
            assertThat(page.findViewById<ScrollView>(R.id.bms_views_scroll).visibility).isEqualTo(View.VISIBLE)
            assertThat((grid.getChildAt(8) as TextView).text.toString()).isEqualTo("25.0°C")
            renderer.stop()
            idle()
            state.value = snapshot(voltage = 102.0)
            idle()
            assertThat(voltage()).isEqualTo("101.00 V")
            verify(exactly = 1) { preferences.unregisterOnSharedPreferenceChangeListener(any()) }
            renderer.start()
            idle()
            assertThat(voltage()).isEqualTo("102.00 V")
            renderer.dispose()
            idle()
            verify(exactly = 2) { preferences.unregisterOnSharedPreferenceChangeListener(any()) }
            state.value = snapshot(voltage = 103.0)
            renderer.refresh()
            assertThat(voltage()).isEqualTo("102.00 V")
        } finally {
            renderer.dispose()
            lifecycle.currentState = Lifecycle.State.DESTROYED
            idle()
            unmockkStatic(PreferenceManager::class)
        }
    }

    @Test fun `proto only changes rebuild Ninebot fields without activity configuration or cell count changes`() {
        val config = AppConfig(context)
        val model = mockk<BleSessionViewModel>()
        val state = MutableStateFlow(snapshot(16).copy(wheelType = WHEEL_TYPE.NINEBOT_Z, model = "Z10"))
        every { model.bmsDisplay } returns state
        val owner = mockk<LifecycleOwner>()
        val lifecycle = LifecycleRegistry(owner)
        every { owner.lifecycle } returns lifecycle
        lifecycle.currentState = Lifecycle.State.STARTED
        val page = LayoutInflater.from(context).inflate(R.layout.main_view_smart_bms, null)
        val renderer = BmsPageRenderer(page, model, config, BmsScroll(), owner)
        val grid = page.findViewById<GridLayout>(R.id.page_smart_bms_grid)
        try {
            renderer.start()
            idle()
            assertThat(grid.childCount).isEqualTo(71)
            assertThat((grid.getChildAt(1) as TextView).text.toString()).isEqualTo(context.getString(R.string.bmsSn))
            state.value = state.value.copy(protoVer = "S2")
            idle()
            assertThat(grid.childCount).isEqualTo(11)
            assertThat((grid.getChildAt(1) as TextView).text.toString()).isEqualTo(context.getString(R.string.bmsRemPerc))
            state.value = state.value.copy(protoVer = "")
            idle()
            assertThat(grid.childCount).isEqualTo(71)
        } finally {
            renderer.dispose()
            lifecycle.currentState = Lifecycle.State.DESTROYED
            idle()
        }
    }
}
