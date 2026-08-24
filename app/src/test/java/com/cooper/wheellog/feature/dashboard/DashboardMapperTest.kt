package com.cooper.wheellog.feature.dashboard

import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionState
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.github.tritbool.euc.ble.core.BLEConstants
import io.github.tritbool.euc.ble.models.EUCData
import org.junit.Before
import org.junit.Test

class DashboardMapperTest {

    private lateinit var appConfig: AppConfig

    // A minimal connected EUCData
    private fun eucData(
        speed: Double = 20.0,
        pwm: Double? = 30.0,
        battery: Int = 80,
        temperature: Double = 25.0,
        voltage: Double = 67.2,
        current: Double = 10.0,
        totalDistance: Double? = 100.0,
        wheelDistance: Double? = 5.0,
        rideTime: Long? = 3600L,
        manufacturer: String = "Kingsong"
    ): EUCData = mockk(relaxed = true) {
        every { this@mockk.speed } returns speed
        every { this@mockk.pwm } returns pwm
        every { this@mockk.batteryLevel } returns battery
        every { this@mockk.temperature } returns temperature
        every { this@mockk.voltage } returns voltage
        every { this@mockk.current } returns current
        every { this@mockk.totalDistance } returns totalDistance
        every { this@mockk.wheelDistance } returns wheelDistance
        every { this@mockk.rideTime } returns rideTime
        every { this@mockk.manufacturer } returns manufacturer
        every { this@mockk.model } returns "KS-S22"
    }

    private fun connectedState(data: EUCData = eucData()): BleSessionState =
        BleSessionState(
            connectionState = BLEConstants.ConnectionState.CONNECTED,
            lastData = data,
            sessionMaxPwm = 45.0,
            sessionBatteryLowest = 75,
            sessionRidingTimeSec = 1800L
        )

    @Before
    fun setUp() {
        appConfig = mockk(relaxed = true) {
            every { useMph } returns false
            every { useFahrenheit } returns false
            every { maxSpeed } returns 50
            every { profileName } returns ""
            every { swapSpeedPwm } returns false
            every { useShortPwm } returns false
            every { alarmsEnabled } returns false
            every { pwmBasedAlarms } returns false
            every { alarmFactor1 } returns 80
            every { alarmFactor2 } returns 95
            every { alarm1Speed } returns 0
            every { alarm1Battery } returns 0
            every { alarm2Speed } returns 0
            every { alarm2Battery } returns 0
            every { alarm3Speed } returns 0
            every { alarm3Battery } returns 0
            every { colorPwmStart } returns 60
            every { colorPwmEnd } returns 90
        }
    }

    // ── Disconnected state ─────────────────────────────────────────────────

    @Test
    fun `disconnected state returns EMPTY with config values preserved`() {
        val state = BleSessionState()   // default = DISCONNECTED
        val result = DashboardMapper.map(state, null, appConfig)

        assertThat(result.isConnected).isFalse()
        assertThat(result.speed).isEqualTo(0f)
        assertThat(result.battery).isEqualTo(0)
        assertThat(result.alarmLevel).isEqualTo(AlarmLevel.NONE)
        assertThat(result.maxSpeed).isEqualTo(50)
        assertThat(result.colorPwmStart).isEqualTo(60)
    }

    // ── Speed display ──────────────────────────────────────────────────────

    @Test
    fun `speed is formatted to one decimal in km-h`() {
        val result = DashboardMapper.map(connectedState(), null, appConfig)

        assertThat(result.isConnected).isTrue()
        assertThat(result.speed).isEqualTo(20f)
        assertThat(result.speedDisplay).isEqualTo("20.0")
        assertThat(result.speedUnit).isEqualTo("km/h")
        assertThat(result.displayMode).isEqualTo(DisplayMode.SPEED)
    }

    @Test
    fun `speed is converted to mph when useMph is true`() {
        every { appConfig.useMph } returns true

        val result = DashboardMapper.map(connectedState(), null, appConfig)

        assertThat(result.speedUnit).isEqualTo("mph")
        // 20 km/h ≈ 12.4 mph
        assertThat(result.speedDisplay).isEqualTo("12.4")
    }

    @Test
    fun `speed >= 100 displays as integer`() {
        val data = eucData(speed = 102.0)
        val result = DashboardMapper.map(connectedState(data), null, appConfig)

        assertThat(result.speedDisplay).isEqualTo("102")
    }

    // ── Display mode / swap ────────────────────────────────────────────────

    @Test
    fun `swapSpeedPwm false gives SPEED display mode`() {
        every { appConfig.swapSpeedPwm } returns false
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.displayMode).isEqualTo(DisplayMode.SPEED)
    }

    @Test
    fun `swapSpeedPwm true gives PWM display mode`() {
        every { appConfig.swapSpeedPwm } returns true
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.displayMode).isEqualTo(DisplayMode.PWM)
    }

    @Test
    fun `swapOverride non-null overrides appConfig swapSpeedPwm`() {
        every { appConfig.swapSpeedPwm } returns false  // config says SPEED
        val result = DashboardMapper.map(connectedState(), swapOverride = true, appConfig)
        // swapOverride = true → PWM
        assertThat(result.displayMode).isEqualTo(DisplayMode.PWM)
    }

    // ── Dial fraction ──────────────────────────────────────────────────────

    @Test
    fun `mainDialFraction is normalised to maxSpeed for speed mode`() {
        // speed = 20 km/h, maxSpeed = 50 → fraction = 0.4
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.mainDialFraction).isWithin(0.001f).of(0.4f)
    }

    @Test
    fun `mainDialFraction is capped at 1 when speed exceeds maxSpeed`() {
        val data = eucData(speed = 100.0)
        val result = DashboardMapper.map(connectedState(data), null, appConfig)
        assertThat(result.mainDialFraction).isEqualTo(1f)
    }

    @Test
    fun `mainDialFraction uses pwm in PWM mode`() {
        every { appConfig.swapSpeedPwm } returns true
        // pwm = 30, maxSpeed = 50 → fraction = 0.6
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.mainDialFraction).isWithin(0.001f).of(0.6f)
    }

    @Test
    fun `pwm values above 100 are normalized for dashboard display`() {
        val data = eucData(pwm = 426.4)
        val result = DashboardMapper.map(connectedState(data), null, appConfig)

        assertThat(result.pwm).isWithin(0.001f).of(42.64f)
        assertThat(result.mainDialFraction).isWithin(0.001f).of(42.64f / 50f)
    }

    // ── Alarm levels ───────────────────────────────────────────────────────

    @Test
    fun `no alarm when alarmsEnabled is false`() {
        every { appConfig.alarmsEnabled } returns false
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.alarmLevel).isEqualTo(AlarmLevel.NONE)
    }

    @Test
    fun `WARN alarm when PWM exceeds alarmFactor1`() {
        every { appConfig.alarmsEnabled } returns true
        every { appConfig.pwmBasedAlarms } returns true
        every { appConfig.alarmFactor1 } returns 70
        every { appConfig.alarmFactor2 } returns 90
        // pwm = 30 → below factor1=70 (30/100 = 0.30 < 0.70) → NONE
        val data = eucData(pwm = 30.0)
        val result = DashboardMapper.map(connectedState(data), null, appConfig)
        assertThat(result.alarmLevel).isEqualTo(AlarmLevel.NONE)
    }

    @Test
    fun `WARN alarm when PWM fraction crosses alarmFactor1 threshold`() {
        every { appConfig.alarmsEnabled } returns true
        every { appConfig.pwmBasedAlarms } returns true
        every { appConfig.alarmFactor1 } returns 70
        every { appConfig.alarmFactor2 } returns 90
        val data = eucData(pwm = 75.0)   // 75 % > 70 % threshold
        val result = DashboardMapper.map(connectedState(data), null, appConfig)
        assertThat(result.alarmLevel).isEqualTo(AlarmLevel.WARN)
    }

    @Test
    fun `CRITICAL alarm when PWM fraction crosses alarmFactor2 threshold`() {
        every { appConfig.alarmsEnabled } returns true
        every { appConfig.pwmBasedAlarms } returns true
        every { appConfig.alarmFactor1 } returns 70
        every { appConfig.alarmFactor2 } returns 90
        val data = eucData(pwm = 92.0)
        val result = DashboardMapper.map(connectedState(data), null, appConfig)
        assertThat(result.alarmLevel).isEqualTo(AlarmLevel.CRITICAL)
    }

    @Test
    fun `WARN alarm for speed-based alarm1`() {
        every { appConfig.alarmsEnabled } returns true
        every { appConfig.pwmBasedAlarms } returns false
        every { appConfig.alarm1Speed } returns 15
        every { appConfig.alarm1Battery } returns 90
        // speed=20 >= 15, battery=80 <= 90 → WARN
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.alarmLevel).isEqualTo(AlarmLevel.WARN)
    }

    @Test
    fun `CRITICAL alarm for speed-based alarm3`() {
        every { appConfig.alarmsEnabled } returns true
        every { appConfig.pwmBasedAlarms } returns false
        every { appConfig.alarm3Speed } returns 15
        every { appConfig.alarm3Battery } returns 90
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.alarmLevel).isEqualTo(AlarmLevel.CRITICAL)
    }

    // ── Short PWM ──────────────────────────────────────────────────────────

    @Test
    fun `useShortPwm is forwarded from appConfig`() {
        every { appConfig.useShortPwm } returns true
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.useShortPwm).isTrue()
    }

    // ── Session stats ──────────────────────────────────────────────────────

    @Test
    fun `maxPwm comes from sessionMaxPwm in state`() {
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.maxPwm).isEqualTo(45f)
    }

    @Test
    fun `batteryLowest comes from sessionBatteryLowest in state`() {
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.batteryLowest).isEqualTo(75)
    }

    @Test
    fun `rideTime uses sessionRidingTimeSec when available`() {
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.rideTimeFormatted).isEqualTo("00:30:00")
    }

    @Test
    fun `wheelModel prefers configured profile name`() {
        every { appConfig.profileName } returns "Garage S22"

        val result = DashboardMapper.map(connectedState(), null, appConfig)

        assertThat(result.wheelModel).isEqualTo("Garage S22")
    }

    // ── formatRideTime helper ──────────────────────────────────────────────

    @Test
    fun `formatRideTime formats seconds correctly`() {
        assertThat(DashboardMapper.formatRideTime(0L)).isEqualTo("00:00:00")
        assertThat(DashboardMapper.formatRideTime(3661L)).isEqualTo("01:01:01")
        assertThat(DashboardMapper.formatRideTime(86399L)).isEqualTo("23:59:59")
    }

    // ── Battery fractions ──────────────────────────────────────────────────

    @Test
    fun `batteryFraction is normalised 0-1`() {
        val result = DashboardMapper.map(connectedState(), null, appConfig)
        assertThat(result.batteryFraction).isWithin(0.001f).of(0.8f)  // battery=80
    }

    @Test
    fun `temperatureFraction is normalised to 80C range`() {
        val data = eucData(temperature = 40.0)  // 40°C → 0.5
        val result = DashboardMapper.map(connectedState(data), null, appConfig)
        assertThat(result.temperatureFraction).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `battery and temperature displays mirror legacy formatting`() {
        every { appConfig.useFahrenheit } returns false
        val data = eucData(battery = 9, temperature = 7.2)

        val result = DashboardMapper.map(connectedState(data), null, appConfig)

        assertThat(result.batteryDisplay).isEqualTo("09%")
        assertThat(result.temperatureDisplay).isEqualTo("07℃")
    }
}
