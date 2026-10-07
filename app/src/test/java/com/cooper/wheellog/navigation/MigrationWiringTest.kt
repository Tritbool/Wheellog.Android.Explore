package com.cooper.wheellog.navigation

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.MainActivity
import com.cooper.wheellog.ScanActivity
import com.cooper.wheellog.SplashActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class MigrationWiringTest {
    @Test fun `production activities remain registered and the prototype is absent`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val manager = application.packageManager
        assertThat(manager.getActivityInfo(ComponentName(application, SplashActivity::class.java), 0).exported).isTrue()
        val main = manager.getActivityInfo(ComponentName(application, MainActivity::class.java), 0)
        assertThat(main.launchMode).isEqualTo(ActivityInfo.LAUNCH_SINGLE_TASK)
        assertThat(main.configChanges and ActivityInfo.CONFIG_ORIENTATION).isNotEqualTo(0)
        assertThat(manager.getActivityInfo(ComponentName(application, ScanActivity::class.java), 0).theme)
            .isEqualTo(com.cooper.wheellog.R.style.OriginalTheme_Transparent)
        val prototype = runCatching {
            Class.forName("com.cooper.wheellog.compose.MainActivityCompose")
        }.exceptionOrNull()
        assertThat(prototype).isInstanceOf(ClassNotFoundException::class.java)
    }

    @Test fun `all migrated renderers default on and every fallback remains independently persistent`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear()
            .putInt("versionSettings", 1).commit()
        val config = AppConfig(application)
        val read = listOf<(AppConfig) -> Boolean>(
            { it.useComposeContainer }, { it.useComposeUI }, { it.useComposeTelemetry },
            { it.useComposeEvents }, { it.useComposeTrips }, { it.useComposeBms }, { it.useComposeScan }
        )
        val write = listOf<(AppConfig, Boolean) -> Unit>(
            { c, v -> c.useComposeContainer = v }, { c, v -> c.useComposeUI = v },
            { c, v -> c.useComposeTelemetry = v }, { c, v -> c.useComposeEvents = v },
            { c, v -> c.useComposeTrips = v }, { c, v -> c.useComposeBms = v },
            { c, v -> c.useComposeScan = v }
        )
        assertThat(read.map { it(config) }).containsExactly(true, true, true, true, true, true, true)
        write.forEachIndexed { selected, set ->
            set(config, false)
            val persisted = AppConfig(application)
            read.forEachIndexed { index, get ->
                assertThat(get(persisted)).isEqualTo(index != selected)
            }
            set(config, true)
        }
    }
}
