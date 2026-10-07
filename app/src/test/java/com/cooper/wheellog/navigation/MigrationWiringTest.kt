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

    @Test fun `all renderer switches have been removed even with saved false preferences`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val keys = listOf(
            "use_compose_container", "useComposeUI", "use_compose_telemetry",
            "use_compose_events", "use_compose_trips", "use_compose_bms", "use_compose_scan"
        )
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear()
            .putInt("versionSettings", 1).apply { keys.forEach { putBoolean(it, false) } }.commit()
        AppConfig(application)
        assertThat(AppConfig::class.java.methods.filter { it.name.startsWith("getUseCompose") }).isEmpty()
        keys.forEach { key ->
            val resource = if (key == "useComposeUI") "use_compose_dashboard" else key
            assertThat(application.resources.getIdentifier(resource, "string", application.packageName)).isEqualTo(0)
        }
    }
}
