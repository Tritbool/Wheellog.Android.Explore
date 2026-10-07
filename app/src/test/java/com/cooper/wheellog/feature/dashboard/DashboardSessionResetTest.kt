package com.cooper.wheellog.feature.dashboard

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionState
import com.cooper.wheellog.ble.BleSessionViewModel
import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.models.EUCData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DashboardSessionResetTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var session: BleSessionViewModel
    private lateinit var dashboard: DashboardViewModel
    private lateinit var states: MutableStateFlow<BleSessionState>
    private lateinit var data: EUCData
    private lateinit var collecting: Job
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun counter(name: String, value: Any) {
        session.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(session, value)
    }

    @Before fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putInt("versionSettings", 1).commit()
        val config = AppConfig(context)
        config.viewBlocks = arrayOf(
            R.string.top_speed, R.string.max_pwm, R.string.maxcurrent, R.string.maxphasecurrent,
            R.string.maxpower, R.string.maxtemperature, R.string.user_distance
        ).map { context.getString(it) }.toTypedArray()
        startKoin { modules(module { single { config } }) }
        session = BleSessionViewModel(context)
        data = mockk(relaxed = true) {
            // A stopped wheel avoids unrelated riding-clock updates during reset assertions.
            every { speed } returns 0.0
            every { topSpeed } returns 88.0
            every { pwm } returns 50.0
            every { voltage } returns 67.0
            every { current } returns 12.0
            every { power } returns 1000.0
            every { temperature } returns 35.0
            every { batteryLevel } returns 80
            every { totalDistance } returns 1005.0
            every { wheelDistance } returns 5.0
            every { manufacturer } returns "Kingsong"
            every { model } returns "KS-S22"
        }
        counter("sessionTopSpeed", 45.0)
        counter("sessionMaxPower", 2500.0)
        counter("sessionMaxCurrent", 60.0)
        counter("sessionMaxPhaseCurrent", 90.0)
        counter("sessionMaxPwm", 95.0)
        counter("sessionMaxTemperature", 80.0)
        counter("sessionStartTotalDistance", 1000.0)
        counter("batteryLowest", 70)
        counter("batteryStart", 85)
        counter("ridingTime", 1800)
        counter("voltageSag", 6700)
        @Suppress("UNCHECKED_CAST")
        val flow = session.javaClass.getDeclaredField("_sessionState").apply { isAccessible = true }
            .get(session) as MutableStateFlow<BleSessionState>
        states = flow
        states.value = BleSessionState(
            connectionState = BLEConstants.ConnectionState.CONNECTED,
            lastData = data, lastDataTimestamp = 1234L,
            sessionTopSpeed = 45.0, sessionMaxPower = 2500.0, sessionMaxCurrent = 60.0,
            sessionMaxPhaseCurrent = 90.0, sessionMaxPwm = 95.0, sessionMaxTemperature = 80.0,
            sessionBatteryLowest = 70, sessionDistance = 5.0, sessionRideTime = 3600L,
            sessionRidingTimeSec = 1800L
        )
        dashboard = DashboardViewModel(context, session, config)
        collecting = dashboard.viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            dashboard.uiState.collect {}
        }
        idle()
    }

    @After fun cleanup() {
        collecting.cancel()
        dashboard.viewModelScope.cancel()
        session.viewModelScope.cancel()
        idle()
        stopKoin()
    }

    private fun value(metric: Int) =
        dashboard.uiState.value.infoBlocks.single { it.metric == metric }.value

    private fun assertUnchangedTelemetry() {
        assertThat(states.value.lastData).isSameInstanceAs(data)
        assertThat(states.value.lastDataTimestamp).isEqualTo(1234L)
        assertThat(states.value.sessionDistance).isEqualTo(5.0)
        assertThat(states.value.sessionBatteryLowest).isEqualTo(70)
        assertThat(states.value.sessionRidingTimeSec).isEqualTo(1800L)
        assertThat(states.value.sessionRideTime).isEqualTo(3600L)
        assertThat(session.batteryLowestLevel).isEqualTo(70)
        val client = session.javaClass.getDeclaredField("_eucBleClient\$delegate").apply { isAccessible = true }
            .get(session) as Lazy<*>
        assertThat(client.isInitialized()).isFalse()
    }

    private fun assertMaxReset() {
        assertThat(dashboard.uiState.value.topSpeed).isEqualTo(45f)
        assertThat(value(R.string.max_pwm)).isEqualTo("95.00%")
        session.resetMaxValues()
        idle()
        assertThat(dashboard.uiState.value.topSpeed).isEqualTo(0f)
        assertThat(value(R.string.top_speed)).isEqualTo("0.0 km/h")
        assertThat(value(R.string.max_pwm)).isEqualTo("0.00%")
        assertThat(value(R.string.maxcurrent)).isEqualTo("0.0 A")
        assertThat(value(R.string.maxphasecurrent)).isEqualTo("0.0 A")
        assertThat(value(R.string.maxpower)).isEqualTo("0 W")
        assertThat(value(R.string.maxtemperature)).isEqualTo("35℃")
        assertThat(states.value.sessionTopSpeed).isNull()
        assertThat(states.value.sessionMaxPwm).isNull()
        assertThat(states.value.sessionMaxCurrent).isNull()
        assertThat(states.value.sessionMaxPhaseCurrent).isNull()
        assertThat(states.value.sessionMaxPower).isNull()
        assertThat(states.value.sessionMaxTemperature).isNull()
        assertThat(session.topSpeedDouble).isEqualTo(0.0)
        assertThat(session.maxPwm).isEqualTo(0.0)
        assertThat(session.maxCurrentDouble).isEqualTo(0.0)
        assertThat(session.maxPhaseCurrentDouble).isEqualTo(0.0)
        assertThat(session.maxPowerDouble).isEqualTo(0.0)
        assertThat(session.maxTemp).isEqualTo(35.0)
        assertUnchangedTelemetry()
    }

    @Test fun `TripScreen max action immediately updates dashboard without another BLE frame`() {
        assertMaxReset()
    }

    @Test fun `TripScreen max action immediately updates retained disconnected dashboard`() {
        states.value = states.value.copy(connectionState = BLEConstants.ConnectionState.DISCONNECTED)
        idle()
        assertThat(dashboard.uiState.value.isConnected).isFalse()
        assertMaxReset()
        assertThat(states.value.isConnected).isFalse()
    }

    @Test fun `repeated max resets still publish revision when nullable statistics are already reset`() {
        val emissions = mutableListOf<BleSessionState>()
        val observing = dashboard.viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.sessionState.collect { emissions += it }
        }
        try {
            session.resetMaxValues()
            idle()
            session.resetMaxValues()
            idle()
            assertThat(emissions.map { it.sessionStatisticsRevision }).containsExactly(0L, 1L, 2L).inOrder()
            assertUnchangedTelemetry()
        } finally { observing.cancel() }
    }

    @Test fun `TripScreen user distance reset immediately refreshes private baseline backed block`() {
        assertThat(value(R.string.user_distance)).isEqualTo("5.000 km")
        session.resetUserDistance()
        idle()
        assertThat(value(R.string.user_distance)).isEqualTo("0.000 km")
        assertThat(session.userDistanceDouble).isEqualTo(0.0)
        assertThat(states.value.sessionStatisticsRevision).isEqualTo(1L)
        session.resetUserDistance()
        idle()
        assertThat(states.value.sessionStatisticsRevision).isEqualTo(2L)
        assertThat(value(R.string.user_distance)).isEqualTo("0.000 km")
        assertThat(states.value.sessionTopSpeed).isEqualTo(45.0)
        assertUnchangedTelemetry()
    }

    @Test fun `TripScreen voltage sag reset publishes without clearing local battery reset on a fake frame`() {
        dashboard.resetBatteryLowest()
        idle()
        assertThat(dashboard.uiState.value.batteryLowest).isEqualTo(101)
        assertThat(session.voltageSagDouble).isEqualTo(67.0)
        session.resetVoltageSag()
        idle()
        assertThat(session.voltageSagDouble).isEqualTo(200.0)
        assertThat(states.value.sessionStatisticsRevision).isEqualTo(1L)
        assertThat(dashboard.uiState.value.batteryLowest).isEqualTo(101)
        assertUnchangedTelemetry()
    }

    @Test fun `private user distance still refreshes after all published maxima are already reset`() {
        session.resetMaxValues()
        idle()
        assertThat(states.value.sessionTopSpeed).isNull()
        assertThat(states.value.sessionMaxPwm).isNull()
        assertThat(value(R.string.user_distance)).isEqualTo("5.000 km")
        session.resetUserDistance()
        idle()
        assertThat(states.value.sessionStatisticsRevision).isEqualTo(2L)
        assertThat(value(R.string.user_distance)).isEqualTo("0.000 km")
        assertUnchangedTelemetry()
    }
}
