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
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TelemetryPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun resetPreferences() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .clear().putInt("versionSettings", 1).commit()
    }

    @Test
    fun `telemetry preferences no longer expose renderer switches`() {
        assertThat(AppConfig.TelemetryPreferences::class.java.declaredFields.map { it.name }
            .filter { it.startsWith("useCompose") }).isEmpty()
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
            assertThat(emissions.single().useMph).isFalse()
            listOf("use_compose_telemetry", "use_compose_events", "use_compose_trips", "use_compose_bms")
                .forEach { config.setValue(it, false) }
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertThat(emissions).hasSize(1)
            config.autoUploadEc = true
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
            assertThat(latest.autoUploadEc).isTrue()
            assertThat(latest.useMph).isTrue()
            assertThat(latest.usePsi).isTrue()
            assertThat(latest.useFahrenheit).isTrue()
            assertThat(latest.appTheme).isEqualTo(config.appTheme)
            assertThat(latest.viewBlocks).containsExactly("Voltage", "Distance").inOrder()
            assertThat(latest.pageGraph).isFalse()
            assertThat(latest.pageEvents).isTrue()
            assertThat(latest.pageTrips).isFalse()
            config.viewBlocks = arrayOf("Battery")
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
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
            config.useMph = false
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertThat(emissions).hasSize(count)
        } finally {
            unmockkStatic(PreferenceManager::class)
        }
    }
}
