package com.cooper.wheellog.trips

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.R
import com.cooper.wheellog.data.TripDataDbEntry
import com.cooper.wheellog.data.TripPresentation
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import android.text.format.DateFormat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "en")
class TripPresentationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val oldLocale = Locale.getDefault()
    private val oldZone = TimeZone.getDefault()

    @Before fun setup() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun cleanup() {
        Locale.setDefault(oldLocale)
        TimeZone.setDefault(oldZone)
    }

    @Test fun `stat descriptions preserve exact dual column metric and imperial formatting`() {
        val trip = TripDataDbEntry(
            fileName = "fixture.csv", duration = 42, maxSpeed = 40f, avgSpeed = 20f,
            maxPwm = 85.5f, distance = 10000, consumptionTotal = 200f,
            consumptionByKm = 20f, ecId = 1
        )
        val metric = TripPresentation.descriptions(context, trip, false, "unused")
        assertThat(metric.first).isEqualTo("🚀 40.00 km/h\n♿ 20.00 km/h\n😱 85.5%\n⚡ electro.club")
        assertThat(metric.second).isEqualTo("⌚ 42 min\n📏 10.00 km\n⚡ 200.00 Wh\n🔋 20.00 Wh/km")
        val imperial = TripPresentation.descriptions(context, trip, true, "unused")
        assertThat(imperial.first).isEqualTo("🚀 24.85 mph\n♿ 12.43 mph\n😱 85.5%\n⚡ electro.club")
        assertThat(imperial.second).isEqualTo("⌚ 42 min\n📏 6.21 mi\n⚡ 200.00 Wh\n🔋 32.19 Wh/mi")
        trip.duration = 0
        assertThat(TripPresentation.descriptions(context, trip, true, "2.00 Kb")).isEqualTo("2.00 Kb" to "")
        assertThat(TripPresentation.descriptions(context, null, false, "empty")).isEqualTo("empty" to "")
    }

    @Test fun `friendly names cover today week current year prior year and arbitrary CSV names`() {
        mockkStatic(DateFormat::class)
        every { DateFormat.getBestDateTimePattern(Locale.US, "MMMM dd, HH:mm") } returns "MMMM dd, HH:mm"
        every { DateFormat.getBestDateTimePattern(Locale.US, "yyyy MMMM dd, HH:mm") } returns "MMMM dd, yyyy, HH:mm"
        try {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("2026-10-07 12:00")!!.time
            assertThat(TripPresentation.friendlyName(context, "2026_10_07_10_15_00.csv", now))
                .isEqualTo(context.getString(R.string.today) + ", Wed, 10:15")
            assertThat(TripPresentation.friendlyName(context, "2026_10_05_10_15_00.csv", now))
                .isEqualTo("Monday, 10:15")
            assertThat(TripPresentation.friendlyName(context, "2026_09_01_10_15_00.csv", now))
                .isEqualTo("September 01, 10:15")
            assertThat(TripPresentation.friendlyName(context, "2025_09_01_10_15_00.csv", now))
                .isEqualTo("September 01, 2025, 10:15")
            assertThat(TripPresentation.friendlyName(context, "manual.csv", now)).isEqualTo("manual.csv")
        } finally { unmockkStatic(DateFormat::class) }
    }
}
