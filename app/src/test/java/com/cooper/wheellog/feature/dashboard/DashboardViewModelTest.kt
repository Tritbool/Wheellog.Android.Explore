package com.cooper.wheellog.feature.dashboard

import android.app.Application
import com.cooper.wheellog.AppConfig
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
    private lateinit var bleViewModel: BleSessionViewModel
    private lateinit var sessionStateFlow: MutableStateFlow<BleSessionState>
    private lateinit var dashboardViewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        appConfig = mockk(relaxed = true) {
            every { useMph } returns false
            every { maxSpeed } returns 50
            every { swapSpeedPwm } returns false
            every { useShortPwm } returns false
            every { alarmsEnabled } returns false
            every { colorPwmStart } returns 60
            every { colorPwmEnd } returns 90
        }

        sessionStateFlow = MutableStateFlow(BleSessionState.EMPTY)

        bleViewModel = mockk(relaxed = true) {
            every { sessionState } returns sessionStateFlow
        }

        val application: Application = mockk(relaxed = true)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun connectedState(speed: Double = 20.0, battery: Int = 80) = BleSessionState(
        connectionState = BLEConstants.ConnectionState.CONNECTED,
        lastData = mockk(relaxed = true) {
            every { this@mockk.speed } returns speed
            every { this@mockk.pwm } returns 30.0
            every { this@mockk.batteryLevel } returns battery
            every { this@mockk.temperature } returns 25.0
            every { this@mockk.voltage } returns 67.0
            every { this@mockk.current } returns 10.0
            every { this@mockk.manufacturer } returns "Kingsong"
            every { this@mockk.model } returns "KS-S22"
        }
    )
}
