package com.cooper.wheellog.feature.dashboard

import android.app.Application
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.MotionEvent
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.*
import com.cooper.wheellog.ble.BleSessionState
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.data.TripDao
import com.cooper.wheellog.utils.ThemeEnum
import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.protocols.CommandType
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DashboardRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun session() = mockk<BleSessionViewModel>(relaxed = true).also {
        every { it.sessionState } returns MutableStateFlow(BleSessionState.EMPTY)
    }

    @Before fun setup() {
        context.setTheme(R.style.OriginalTheme)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putInt("versionSettings", 1).commit()
    }

    private fun activity(store: ViewModelStore): Pair<MainActivity, LifecycleRegistry> {
        val activity = mockk<MainActivity>(relaxed = true)
        val lifecycle = LifecycleRegistry(activity)
        every { activity.lifecycle } returns lifecycle
        every { activity.viewModelStore } returns store
        every { activity.application } returns context
        every { activity.defaultViewModelCreationExtras } returns CreationExtras.Empty
        lifecycle.currentState = Lifecycle.State.CREATED
        return activity to lifecycle
    }

    @Test fun `saved false dashboard remains Compose reuses holder and disposes`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("useComposeUI", false).commit()
        val config = AppConfig(context)
        val session = session()
        val store = ViewModelStore()
        val (activity, lifecycle) = activity(store)
        startKoin { modules(module {
            single { config }; single { session }; single { mockk<TripDao>(relaxed = true) }
        }) }
        val recycler = RecyclerView(context)
        val adapter = MainPageAdapter(mutableListOf(R.layout.main_view_main), activity)
        try {
            val holder = adapter.createViewHolder(recycler, R.layout.main_view_main)
            adapter.bindViewHolder(holder, 0)
            val page = holder.itemView
            val compose = page.findViewById<ComposeView>(R.id.mainPageComposeView)
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            assertThat((page as android.view.ViewGroup).childCount).isEqualTo(1)
            adapter.onViewAttachedToWindow(holder)
            lifecycle.currentState = Lifecycle.State.STARTED
            idle()
            config.setValue("useComposeUI", true)
            idle()
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            config.setValue("useComposeUI", false)
            idle()
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            assertThat(holder.itemView).isSameInstanceAs(page)
            adapter.onViewDetachedFromWindow(holder)
            assertThat(compose.hasComposition).isFalse()
            adapter.onViewAttachedToWindow(holder)
            idle()
            adapter.onViewRecycled(holder)
            assertThat(holder.dashboardRenderer).isNull()
            assertThat(compose.hasComposition).isFalse()
            verify(exactly = 0) { session.sendCommand(any<CommandType>()) }
        } finally {
            adapter.onDetachedFromRecyclerView(recycler)
            lifecycle.currentState = Lifecycle.State.DESTROYED
            store.clear()
            idle()
            stopKoin()
        }
    }

    @Test fun `live preferences without BLE stop and restart release listener`() {
        val preferences = spyk(PreferenceManager.getDefaultSharedPreferences(context))
        mockkStatic(PreferenceManager::class)
        val store = ViewModelStore()
        val (activity, lifecycle) = activity(store)
        try {
            every { PreferenceManager.getDefaultSharedPreferences(context) } returns preferences
            val config = AppConfig(context)
            val session = session()
            val page = LayoutInflater.from(context).inflate(R.layout.main_view_main, null)
            val renderer = DashboardPageRenderer(page, session, config, activity)
            renderer.start()
            idle()
            verify(exactly = 0) { preferences.registerOnSharedPreferenceChangeListener(any()) }
            lifecycle.currentState = Lifecycle.State.STARTED
            idle()
            verify(exactly = 1) { preferences.registerOnSharedPreferenceChangeListener(any()) }
            config.setValue("useComposeUI", false)
            config.swapSpeedPwm = true
            config.useMph = true
            config.useFahrenheit = true
            config.maxSpeed = 75
            config.appThemeInt = ThemeEnum.AJDM.value
            config.viewBlocks = arrayOf(context.getString(R.string.temperature), context.getString(R.string.temperature))
            idle()
            val vm = androidx.lifecycle.ViewModelProvider(activity)[DashboardViewModel::class.java]
            val state = vm.uiState.value
            assertThat(state.displayMode).isEqualTo(DisplayMode.PWM)
            assertThat(state.useMph).isTrue()
            assertThat(state.temperatureDisplay).isEqualTo("32℉")
            assertThat(state.maxSpeed).isEqualTo(75)
            assertThat(state.appTheme).isEqualTo(R.style.AJDMTheme)
            assertThat(state.infoBlocks.map { it.slot }).containsExactly(0, 1).inOrder()
            renderer.stop()
            idle()
            verify(exactly = 1) { preferences.unregisterOnSharedPreferenceChangeListener(any()) }
            config.maxSpeed = 90
            idle()
            assertThat(vm.uiState.value.maxSpeed).isEqualTo(75)
            renderer.start()
            idle()
            assertThat(vm.uiState.value.maxSpeed).isEqualTo(90)
            lifecycle.currentState = Lifecycle.State.CREATED
            idle()
            verify(exactly = 2) { preferences.unregisterOnSharedPreferenceChangeListener(any()) }
            renderer.dispose()
            renderer.start()
            idle()
            verify(exactly = 2) { preferences.registerOnSharedPreferenceChangeListener(any()) }
        } finally {
            lifecycle.currentState = Lifecycle.State.DESTROYED
            store.clear()
            idle()
            unmockkStatic(PreferenceManager::class)
        }
    }

    @Test fun `single tap never swaps speed battery or temperature and double tap anywhere sends light once`() {
        val config = AppConfig(context)
        val session = session()
        every { session.isCommandSupported(CommandType.LIGHT_ON) } returns true
        every { session.isCommandSupported(CommandType.LIGHT_OFF) } returns true
        val actions = DashboardActions(context, session, config)
        val state = DashboardUiState.EMPTY
        val geometry = DashboardGeometry.calculate(400f, 800f, 1f, false, 0)
        val gestures = DashboardGestures(context, { state }, { geometry }, actions::singleTap,
            actions::doubleTap, actions::replaceBlock)
        fun tap(time: Long, x: Float, y: Float) {
            MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0).also {
                gestures.touch(it); it.recycle()
            }
            MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP, x, y, 0).also {
                gestures.touch(it); it.recycle()
            }
        }
        tap(0, geometry.speed.cx, geometry.speed.cy)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertThat(AppConfig(context).swapSpeedPwm).isFalse()
        config.swapSpeedPwm = true
        assertThat(AppConfig(context).swapSpeedPwm).isTrue()
        tap(1000, geometry.inner.left, geometry.inner.cy)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        tap(2000, geometry.inner.right, geometry.inner.cy)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertThat(config.swapSpeedPwm).isTrue()
        tap(3000, geometry.speed.cx, geometry.speed.cy)
        tap(3100, geometry.speed.cx, geometry.speed.cy)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertThat(config.swapSpeedPwm).isTrue()
        verify(exactly = 1) { session.sendCommand(CommandType.LIGHT_ON) }
        tap(4000, geometry.inner.left, geometry.inner.cy)
        tap(4100, geometry.inner.left, geometry.inner.cy)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertThat(config.swapSpeedPwm).isTrue()
        verify(exactly = 1) { session.sendCommand(CommandType.LIGHT_OFF) }
        tap(5000, geometry.inner.right, geometry.inner.cy)
        tap(5100, geometry.inner.right, geometry.inner.cy)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertThat(config.swapSpeedPwm).isTrue()
        verify(exactly = 2) { session.sendCommand(CommandType.LIGHT_ON) }
        actions.dispose()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `both themes draw retained disconnected blocks with native pixel sizing`() {
        val data = DashboardViewModel(context, session(), AppConfig(context)).uiState.value
        assertThat(data.isConnected).isFalse()
        assertThat(data.infoBlocks).hasSize(8)
        val renderer = DashboardCanvasRenderer(context)
        listOf(R.style.OriginalTheme, R.style.AJDMTheme).forEach { theme ->
            listOf(400 to 800, 800 to 400, 400 to 400, 500 to 400).forEach { (w, h) ->
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                renderer.draw(Canvas(bitmap), w.toFloat(), h.toFloat(), data.copy(appTheme = theme))
                assertThat(bitmap.getPixel(w / 2, 10)).isNotEqualTo(0)
                assertThat(renderer.geometry(w.toFloat(), h.toFloat(), data).blocks).hasSize(8)
                bitmap.recycle()
            }
        }
    }

    @Test fun `long press replaces only its repeated slot using localized catalogue and persists`() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.OriginalTheme)
        controller.create().start().resume()
        val config = AppConfig(activity)
        val battery = activity.getString(R.string.battery)
        val voltage = activity.getString(R.string.voltage)
        config.viewBlocks = arrayOf(battery, battery)
        val catalogue = listOf(DashboardBlock(battery, "80 %"), DashboardBlock(voltage, "67.20 V"))
        val state = DashboardUiState(catalogue = catalogue,
            infoBlocks = DashboardCatalogue.select(config.viewBlocks.toList(), catalogue))
        val geometry = DashboardGeometry.calculate(400f, 800f, 1f, false, 2)
        val session = session()
        val actions = DashboardActions(activity, session, config)
        val gestures = DashboardGestures(activity, { state }, { geometry }, actions::singleTap,
            actions::doubleTap, actions::replaceBlock)
        try {
            val block = geometry.blocks[1]
            val now = android.os.SystemClock.uptimeMillis()
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, block.cx, block.cy, 0).also {
                gestures.touch(it); it.recycle()
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
            val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            dialog.listView.performItemClick(null, 0, 0)
            assertThat(AppConfig(activity).viewBlocks.toList()).containsExactly(battery, voltage).inOrder()
            assertThat(config.swapSpeedPwm).isFalse()
            verify(exactly = 0) { session.sendCommand(any<CommandType>()) }
        } finally {
            actions.dispose()
            controller.pause().stop().destroy()
        }
    }

    @Test fun `unsupported light and stopped callbacks cannot send duplicate commands`() {
        val config = AppConfig(context)
        val session = session()
        val actions = DashboardActions(context, session, config)
        actions.doubleTap()
        verify(exactly = 0) { session.sendCommand(any<CommandType>()) }
        every { session.isCommandSupported(CommandType.LIGHT_OFF) } returns true
        actions.doubleTap()
        verify(exactly = 1) { session.sendCommand(CommandType.LIGHT_OFF) }
        actions.dispose()
        actions.doubleTap()
        actions.singleTap()
        verify(exactly = 1) { session.sendCommand(any<CommandType>()) }
        assertThat(config.swapSpeedPwm).isFalse()
    }

    @Test fun `existing stored false does not affect dashboard mapping or expose a switch`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("useComposeUI", false).commit()
        val config = AppConfig(context)
        val state = DashboardViewModel(context, session(), config).uiState.value
        assertThat(state.infoBlocks).hasSize(8)
        assertThat(DashboardUiState::class.java.declaredFields.map { it.name }).doesNotContain("useCompose")
    }

    @Test fun `packaged English French and Russian legacy titles alias back to resource metrics`() {
        val aliases = DashboardCatalogue.aliases(context)
        listOf("en", "fr", "ru").forEach { language ->
            val localized = context.createConfigurationContext(android.content.res.Configuration().apply {
                setLocale(java.util.Locale.forLanguageTag(language))
            })
            listOf(R.string.voltage, R.string.battery, R.string.user_distance).forEach { metric ->
                assertThat(aliases[localized.getString(metric)]).isEqualTo(metric)
            }
        }
    }

    @Test fun `confirmed single tap only honors beep preference and never changes persisted swap`() {
        val config = AppConfig(context)
        val session = session()
        val actions = DashboardActions(context, session, config)
        mockkObject(com.cooper.wheellog.utils.SomeUtil)
        try {
            every { com.cooper.wheellog.utils.SomeUtil.playBeep() } just Runs
            actions.singleTap()
            verify(exactly = 0) { com.cooper.wheellog.utils.SomeUtil.playBeep() }
            config.swapSpeedPwm = true
            config.useBeepOnSingleTap = true
            actions.singleTap()
            verify(exactly = 1) { com.cooper.wheellog.utils.SomeUtil.playBeep() }
            assertThat(AppConfig(context).swapSpeedPwm).isTrue()
            actions.dispose()
            actions.singleTap()
            verify(exactly = 1) { com.cooper.wheellog.utils.SomeUtil.playBeep() }
            verify(exactly = 0) { session.sendCommand(any<CommandType>()) }
        } finally {
            actions.dispose()
            unmockkObject(com.cooper.wheellog.utils.SomeUtil)
        }
    }
}
