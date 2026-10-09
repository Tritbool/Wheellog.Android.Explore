package com.cooper.wheellog.feature.dashboard

import android.app.Application
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionState
import com.cooper.wheellog.ble.BleSessionViewModel
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.github.tritbool.euc.ble.core.BLEConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var appConfig: AppConfig
    private lateinit var application: Application
    private lateinit var bleViewModel: BleSessionViewModel
    private lateinit var sessionStateFlow: MutableStateFlow<BleSessionState>
    private lateinit var dashboardViewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        stubString(R.string.pwm, "PWM")
        stubString(R.string.max_pwm, "Max PWM")
        stubString(R.string.voltage, "Voltage")
        stubString(R.string.average_speed, "Average Speed")
        stubString(R.string.average_riding_speed, "Avg Riding")
        stubString(R.string.riding_time, "Ride Time")
        stubString(R.string.speed, "Speed")
        stubString(R.string.top_speed, "Top Speed")
        stubString(R.string.distance, "Distance")
        stubString(R.string.total, "Total")
        stubString(R.string.battery, "Battery")
        stubString(R.string.current, "Current")
        stubString(R.string.phase_current, "Phase Current")
        stubString(R.string.temperature, "Temp")
        stubString(R.string.wheel_distance, "Wheel Distance")
        stubString(R.string.user_distance, "User Distance")
        stubString(R.string.remaining_distance, "Remaining Distance")
        stubString(R.string.battery_per_km, "Battery / km")
        stubString(R.string.avg_cell_volt, "Avg cell volt")

        appConfig = mockk(relaxed = true) {
            every { useMph } returns false
            every { useFahrenheit } returns false
            every { maxSpeed } returns 50
            every { profileName } returns ""
            every { swapSpeedPwm } returns false
            every { useShortPwm } returns false
            every { alarmsEnabled } returns false
            every { colorPwmStart } returns 60
            every { colorPwmEnd } returns 90
            every { viewBlocks } returns arrayOf("Battery", "Temp")
        }

        sessionStateFlow = MutableStateFlow(BleSessionState.EMPTY)

        bleViewModel = mockk(relaxed = true) {
            every { sessionState } returns sessionStateFlow
        }

        dashboardViewModel = DashboardViewModel(application, bleViewModel, appConfig)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial uiState is EMPTY when disconnected`() = runTest {
        val state = dashboardViewModel.uiState.value
        assertThat(state.isConnected).isFalse()
        assertThat(state.speed).isEqualTo(0f)
    }

    @Test
    fun `uiState updates when BLE session state changes`() = runTest {
        sessionStateFlow.value = connectedState(speed = 30.0, battery = 75)
        advanceUntilIdle()

        val state = dashboardViewModel.uiState.value
        assertThat(state.isConnected).isTrue()
        assertThat(state.speed).isEqualTo(30f)
        assertThat(state.battery).isEqualTo(75)
    }

    @Test
    fun `toggleDisplayMode switches from SPEED to PWM`() = runTest {
        sessionStateFlow.value = connectedState()
        advanceUntilIdle()

        assertThat(dashboardViewModel.uiState.value.displayMode).isEqualTo(DisplayMode.SPEED)

        dashboardViewModel.toggleDisplayMode()
        advanceUntilIdle()

        assertThat(dashboardViewModel.uiState.value.displayMode).isEqualTo(DisplayMode.PWM)
    }

    @Test
    fun `toggleDisplayMode twice returns to SPEED`() = runTest {
        sessionStateFlow.value = connectedState()
        advanceUntilIdle()

        dashboardViewModel.toggleDisplayMode()
        advanceUntilIdle()
        dashboardViewModel.toggleDisplayMode()
        advanceUntilIdle()

        assertThat(dashboardViewModel.uiState.value.displayMode).isEqualTo(DisplayMode.SPEED)
    }

    @Test
    fun `uiState includes legacy battery and temperature blocks`() = runTest {
        sessionStateFlow.value = connectedState()
        advanceUntilIdle()

        val labels = dashboardViewModel.uiState.value.infoBlocks.map { it.label }
        assertThat(labels).contains("Battery")
        assertThat(labels).contains("Temp")
    }

    @Test
    fun `uiState follows configured info block order`() = runTest {
        every { appConfig.viewBlocks } returns arrayOf("Distance", "Avg Riding", "Voltage")

        sessionStateFlow.value = connectedState(speed = 30.0, battery = 75)
        advanceUntilIdle()

        assertThat(dashboardViewModel.uiState.value.infoBlocks.map { it.label })
            .containsExactly("Distance", "Avg Riding", "Voltage")
            .inOrder()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun connectedState(speed: Double = 20.0, battery: Int = 80) = BleSessionState(
        connectionState = BLEConstants.ConnectionState.CONNECTED,
        sessionDistance = 4.5,
        sessionRideTime = 900L,
        sessionRidingTimeSec = 600L,
        lastData = mockk(relaxed = true) {
            every { this@mockk.speed } returns speed
            every { this@mockk.pwm } returns 30.0
            every { this@mockk.batteryLevel } returns battery
            every { this@mockk.temperature } returns 25.0
            every { this@mockk.voltage } returns 67.0
            every { this@mockk.current } returns 10.0
            every { this@mockk.wheelDistance } returns 5.0
            every { this@mockk.totalDistance } returns 120.0
            every { this@mockk.manufacturer } returns "Kingsong"
            every { this@mockk.model } returns "KS-S22"
        }
    )

    private fun stubString(id: Int, value: String) {
        every { application.getString(id) } returns value
    }
}
