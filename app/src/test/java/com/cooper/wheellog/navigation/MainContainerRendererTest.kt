package com.cooper.wheellog.navigation

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.*
import com.cooper.wheellog.ble.BleSessionState
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.data.TripDao
import com.cooper.wheellog.databinding.ActivityMainBinding
import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.protocols.CommandType
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class MainContainerRendererTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun `restored dynamic page stays pending until insertion and survives another save`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val host = controller.get()
        host.setTheme(R.style.OriginalTheme)
        controller.create()
        val binding = ActivityMainBinding.inflate(host.layoutInflater)
        val ids = mutableListOf(R.layout.main_view_graph)
        val observers = mutableListOf<RecyclerView.AdapterDataObserver>()
        val adapter = mockk<MainPageAdapter>(relaxed = true)
        every { adapter.pageIds() } answers { ids.toList() }
        every { adapter.itemCount } answers { ids.size }
        every { adapter.registerAdapterDataObserver(any()) } answers { observers.add(firstArg()); Unit }
        every { adapter.unregisterAdapterDataObserver(any()) } answers { observers.remove(firstArg()); Unit }
        val renderer = MainContainerRenderer(binding, adapter, R.layout.main_view_events)
        try {
            renderer.render()
            assertThat(renderer.selectedId).isEqualTo(R.layout.main_view_graph)
            assertThat(renderer.pageToSave).isEqualTo(R.layout.main_view_events)
            ids.add(R.layout.main_view_events)
            observers.toList().forEach { it.onItemRangeInserted(1, 1) }
            assertThat(renderer.selectedId).isEqualTo(R.layout.main_view_events)
            assertThat(renderer.pageToSave).isEqualTo(R.layout.main_view_events)
            assertThat(adapter.position).isEqualTo(1)
            assertThat(binding.mainComposeContainer.visibility).isEqualTo(View.VISIBLE)
        } finally {
            renderer.dispose()
            controller.destroy()
        }
    }

    @Test fun `Compose keeps header instances selection and lifecycle observers without BLE commands`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear()
            .putInt("versionSettings", 1).commit()
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val host = controller.get()
        host.setTheme(R.style.OriginalTheme)
        controller.create().start().resume()
        val activity = mockk<MainActivity>(relaxed = true)
        every { activity.application } returns application
        every { activity.lifecycle } returns host.lifecycle
        val config = AppConfig(application)
        val session = mockk<BleSessionViewModel>(relaxed = true)
        every { session.sessionState } returns MutableStateFlow(BleSessionState.EMPTY)
        startKoin { modules(module {
            single { config }; single { session }; single { mockk<TripDao>(relaxed = true) }
        }) }
        val binding = ActivityMainBinding.inflate(host.layoutInflater)
        host.setContentView(binding.root)
        binding.settingsView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        var settingsToken: Any? = null
        binding.settingsView.visibility = View.VISIBLE
        binding.settingsView.setContent {
            val token = remember { Any() }
            SideEffect { settingsToken = token }
            Text("Settings state")
        }
        controller.visible()
        val adapter = spyk(MainPageAdapter(
            mutableListOf(R.layout.main_view_graph, R.layout.main_view_events), activity))
        val renderer = MainContainerRenderer(binding, adapter, R.layout.main_view_events)
        try {
            renderer.render()
            idle()
            assertThat(adapter.position).isEqualTo(1)
            val token = settingsToken
            assertThat(token).isNotNull()
            val toolbar = binding.toolbar
            renderer.render()
            idle()
            assertThat(binding.mainComposeContainer.visibility).isEqualTo(View.VISIBLE)
            assertThat(renderer.selectedId).isEqualTo(R.layout.main_view_events)
            assertThat(binding.toolbar).isSameInstanceAs(toolbar)
            renderer.render()
            idle()
            assertThat(binding.mainHeader.parent).isNotSameInstanceAs(binding.root)
            assertThat(settingsToken).isSameInstanceAs(token)
            adapter.removePage(R.layout.main_view_graph)
            idle()
            assertThat(renderer.selectedId).isEqualTo(R.layout.main_view_events)
            assertThat(adapter.position).isEqualTo(0)
            renderer.render()
            idle()
            renderer.dispose()
            renderer.dispose()
            assertThat(binding.mainComposeContainer.hasComposition).isFalse()
            verify(exactly = 1) { adapter.unregisterAdapterDataObserver(any()) }
            verify(exactly = 0) { session.sendCommand(any<CommandType>()) }
            verify(exactly = 0) { session.startScan() }
            verify(exactly = 0) { session.connect(any()) }
        } finally {
            renderer.dispose()
            (binding.mainHeader.parent as? ViewGroup)?.removeView(binding.mainHeader)
            controller.pause().stop().destroy()
            idle()
            stopKoin()
        }
    }
}
