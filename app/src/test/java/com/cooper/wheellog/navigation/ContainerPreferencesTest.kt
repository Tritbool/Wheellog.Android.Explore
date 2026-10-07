package com.cooper.wheellog.navigation

import android.app.Application
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ContainerPreferencesTest {
    @Test fun `container defaults on persists independently and releases its scoped listener`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val preferences = spyk(PreferenceManager.getDefaultSharedPreferences(application))
        preferences.edit().clear().putInt("versionSettings", 1).commit()
        mockkStatic(PreferenceManager::class)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            every { PreferenceManager.getDefaultSharedPreferences(application) } returns preferences
            val config = AppConfig(application)
            assertThat(config.useComposeContainer).isTrue()
            val values = mutableListOf<Boolean>()
            val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                config.containerPreferences().collect { values += it }
            }
            shadowOf(Looper.getMainLooper()).idle()
            config.useComposeContainer = false
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(values).containsExactly(true, false).inOrder()
            assertThat(AppConfig(application).useComposeContainer).isFalse()
            assertThat(config.useComposeUI).isTrue()
            assertThat(config.useComposeTelemetry).isTrue()
            assertThat(config.useComposeScan).isTrue()
            job.cancel()
            shadowOf(Looper.getMainLooper()).idle()
            verify(exactly = 1) { preferences.unregisterOnSharedPreferenceChangeListener(any()) }
        } finally {
            scope.cancel()
            unmockkStatic(PreferenceManager::class)
        }
    }
}
