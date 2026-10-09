package com.cooper.wheellog.navigation

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.MainContainerRenderer
import com.cooper.wheellog.MainPageAdapter
import com.cooper.wheellog.R
import com.cooper.wheellog.databinding.ActivityMainBinding
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ContainerPreferencesTest {
    @Test fun `retired watch menu choices are ignored without losing reset`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(application)
        preferences.edit().clear().putInt("versionSettings", 1)
            .putString("main_menu_buttons", "watch;reset;miband").commit()
        val config = AppConfig(application)
        assertThat(config.mainMenuButtons.toList()).containsExactly("reset")
        config.mainMenuButtons = emptyArray()
        assertThat(config.mainMenuButtons.toList()).isEmpty()
    }

    @Test fun `retired notification choices are ignored while supported controls remain`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val connection = application.getString(R.string.icon_connection)
        val light = application.getString(R.string.icon_light)
        val miband = application.getString(R.string.icon_miband)
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear()
            .putInt("versionSettings", 1)
            .putString(application.getString(R.string.notification_buttons),
                "$miband;$connection;$light").commit()
        assertThat(AppConfig(application).notificationButtons.toList())
            .containsExactly(connection, light).inOrder()
    }

    @Test fun `saved false cannot enable a native pager or install a switching listener`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.setTheme(R.style.OriginalTheme)
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear()
            .putInt("versionSettings", 1).putBoolean("use_compose_container", false).commit()
        AppConfig(application)
        val binding = ActivityMainBinding.inflate(LayoutInflater.from(application))
        val adapter = mockk<MainPageAdapter>(relaxed = true)
        every { adapter.pageIds() } returns listOf(R.layout.main_view_graph)
        val renderer = MainContainerRenderer(binding, adapter, null)
        try {
            renderer.render()
            renderer.render()
            assertThat(binding.mainComposeContainer.visibility).isEqualTo(View.VISIBLE)
            assertThat(binding.root.childCount).isEqualTo(1)
            assertThat(renderer.selectedId).isEqualTo(R.layout.main_view_graph)
            verify(exactly = 1) { adapter.startObserving() }
            assertThat(AppConfig::class.java.methods.map { it.name }).doesNotContain("containerPreferences")
        } finally {
            renderer.dispose()
            renderer.dispose()
        }
        verify(exactly = 1) { adapter.stopObserving() }
    }
}
