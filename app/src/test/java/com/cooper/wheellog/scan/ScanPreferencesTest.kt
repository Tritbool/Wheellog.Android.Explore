package com.cooper.wheellog.scan

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import androidx.compose.runtime.State
import androidx.compose.foundation.lazy.LazyListState
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ScanPageRenderer
import com.cooper.wheellog.databinding.ActivityScanBinding
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ScanPreferencesTest {
    @Test fun `saved false cannot restore legacy scan and disposed renderer ignores updates`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        application.setTheme(R.style.OriginalTheme)
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear()
            .putInt("versionSettings", 1).putBoolean("use_compose_scan", false).commit()
        AppConfig(application)
        val binding = ActivityScanBinding.inflate(LayoutInflater.from(application))
        val initial = ScanUiState(manualAddress = "draft")
        val renderer = ScanPageRenderer(binding, initial, {}, {}, {}, {})
        val scroll = LazyListState(3, 9)
        ScanPageRenderer::class.java.getDeclaredField("scroll").apply { isAccessible = true }
            .set(renderer, scroll)
        @Suppress("UNCHECKED_CAST")
        val state = ScanPageRenderer::class.java.getDeclaredField("state").apply {
            isAccessible = true
        }.get(renderer) as State<ScanUiState>
        assertThat(binding.root.id).isEqualTo(R.id.scan_compose_view)
        assertThat(binding.root.visibility).isEqualTo(View.VISIBLE)
        renderer.stop()
        renderer.start(initial.copy(scanning = true))
        assertThat(state.value.scanning).isTrue()
        assertThat(scroll.firstVisibleItemIndex).isEqualTo(3)
        assertThat(scroll.firstVisibleItemScrollOffset).isEqualTo(9)
        renderer.dispose()
        renderer.start(initial)
        renderer.render(initial)
        assertThat(state.value.scanning).isTrue()
        assertThat(binding.root.hasComposition).isFalse()
        assertThat(AppConfig::class.java.methods.map { it.name }).doesNotContain("scanPreferences")
    }
}
