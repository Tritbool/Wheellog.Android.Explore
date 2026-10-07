package com.cooper.wheellog.telemetry

import android.app.Application
import android.content.SharedPreferences
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.utils.ThemeEnum
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TelemetryPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `telemetry defaults off persists reversibly and is independent of dashboard`() {
        val config = AppConfig(context)
        assertThat(config.useComposeTelemetry).isFalse()
        config.useComposeUI = true
        assertThat(config.useComposeTelemetry).isFalse()
        config.useComposeTelemetry = true
        assertThat(AppConfig(context).useComposeTelemetry).isTrue()
        config.useComposeTelemetry = false
        assertThat(AppConfig(context).useComposeTelemetry).isFalse()
        assertThat(config.useComposeUI).isTrue()
    }

    @Test
    fun `cold observation emits preferences without BLE and releases listeners on cancellation`() = runTest {
        val preferences = spyk(PreferenceManager.getDefaultSharedPreferences(context))
        preferences.edit().putInt("versionSettings", 1).commit()
        mockkStatic(PreferenceManager::class)
        try {
            every { PreferenceManager.getDefaultSharedPreferences(context) } returns preferences
            val config = AppConfig(context)
            val flow = config.telemetryPreferences()
            verify(exactly = 0) {
                preferences.registerOnSharedPreferenceChangeListener(any())
            }
            val emissions = mutableListOf<AppConfig.TelemetryPreferences>()
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                flow.collect { emissions.add(it) }
            }
            runCurrent()
            assertThat(emissions.single().useCompose).isFalse()
            config.useComposeTelemetry = true
            config.useMph = true
            config.usePsi = true
            config.useFahrenheit = true
            config.appThemeInt = ThemeEnum.AJDM.value
            config.viewBlocks = arrayOf("Voltage", "Distance")
            config.pageGraph = false
            config.pageEvents = true
            config.pageTrips = false
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            val latest = emissions.last()
            assertThat(latest.useCompose).isTrue()
            assertThat(latest.useMph).isTrue()
            assertThat(latest.usePsi).isTrue()
            assertThat(latest.useFahrenheit).isTrue()
            assertThat(latest.appTheme).isEqualTo(config.appTheme)
            assertThat(latest.viewBlocks).containsExactly("Voltage", "Distance").inOrder()
            assertThat(latest.pageGraph).isFalse()
            assertThat(latest.pageEvents).isTrue()
            assertThat(latest.pageTrips).isFalse()
            config.useComposeTelemetry = false
            config.viewBlocks = arrayOf("Battery")
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertThat(emissions.last().useCompose).isFalse()
            assertThat(emissions.last().viewBlocks).containsExactly("Battery")
            assertThat(latest.viewBlocks).containsExactly("Voltage", "Distance").inOrder()
            val count = emissions.size
            config.setValue("unrelated_setting", true)
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertThat(emissions).hasSize(count)
            collection.cancel()
            runCurrent()
            verify(exactly = 1) {
                preferences.unregisterOnSharedPreferenceChangeListener(
                    any<SharedPreferences.OnSharedPreferenceChangeListener>()
                )
            }
            config.useComposeTelemetry = true
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertThat(emissions).hasSize(count)
        } finally {
            unmockkStatic(PreferenceManager::class)
        }
    }
}
