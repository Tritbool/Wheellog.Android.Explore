package com.cooper.wheellog.telemetry

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TelemetryPresentationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `field order matches the legacy manufacturer fixtures exactly`() {
        val fixtures = mapOf(
            WHEEL_TYPE.KINGSONG to
                "speed dynamic_speed_limit top_speed average_speed average_riding_speed battery output cpuload temperature temperature2 ride_time riding_time distance wheel_distance user_distance total_distance voltage voltage_sag current power fan_status charging_status charging mode name model version serial_number",
            WHEEL_TYPE.VETERAN to
                "speed top_speed average_speed average_riding_speed battery temperature ride_time riding_time distance wheel_distance user_distance total_distance voltage voltage_sag current phase_current power angle sleep_timer charging_status charging model version",
            WHEEL_TYPE.GOTWAY to
                "speed top_speed average_speed average_riding_speed battery temperature temperature2 ride_time riding_time distance wheel_distance user_distance total_distance voltage voltage_sag current phase_current power model version charging",
            WHEEL_TYPE.INMOTION_V2 to
                "speed dynamic_speed_limit torque top_speed average_speed average_riding_speed battery temperature temperature2 cpu_temp imu_temp angle roll ride_time riding_time distance wheel_distance user_distance total_distance voltage voltage_sag current dynamic_current_limit power motor_power mode model version serial_number",
            WHEEL_TYPE.INMOTION to
                "speed top_speed average_speed average_riding_speed battery temperature imu_temp angle roll ride_time riding_time distance wheel_distance user_distance total_distance voltage voltage_sag current power mode model version serial_number charging",
            WHEEL_TYPE.NINEBOT to
                "speed top_speed average_speed average_riding_speed battery temperature ride_time riding_time distance user_distance total_distance voltage voltage_sag current power model version error serial_number",
            WHEEL_TYPE.NINEBOT_Z to
                "speed top_speed average_speed average_riding_speed battery temperature ride_time riding_time distance user_distance total_distance voltage voltage_sag current power model version error serial_number",
            WHEEL_TYPE.Unknown to ""
        )
        assertThat(fixtures.keys).containsExactlyElementsIn(WHEEL_TYPE.values().toList())
        fixtures.forEach { (type, fixture) ->
            val expected = if (fixture.isEmpty()) emptyList() else fixture.split(" ")
            assertThat(TelemetryPresentation.fields(type).map(context.resources::getResourceEntryName))
                .containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun `formatting retains legacy precision units status text and temperature conversion`() {
        val config = AppConfig(context)
        config.useMph = false
        config.useFahrenheit = false
        val model = mockk<BleSessionViewModel>(relaxed = true) {
            every { speedDouble } returns 16.09344
            every { topSpeedDouble } returns 32.18688
            every { averageSpeedDouble } returns 8.04672
            every { averageRidingSpeedDouble } returns 24.14016
            every { speedLimit } returns 40.2336
            every { distanceDouble } returns 1.609344
            every { wheelDistanceDouble } returns 3.218688
            every { userDistanceDouble } returns 4.828032
            every { totalDistanceDouble } returns 160.9344
            every { voltageDouble } returns 84.126
            every { voltageSagDouble } returns 3.456
            every { temperatureDouble } returns 5.9
            every { motorTemperatureDouble } returns 25.9
            every { cpuTemp } returns 0
            every { imuTemp } returns -5
            every { angle } returns -1.236
            every { roll } returns 2.345
            every { currentDouble } returns -2.345
            every { phaseCurrentDouble } returns 12.346
            every { currentLimit } returns 18.126
            every { torque } returns 1.256
            every { powerDouble } returns -123.456
            every { motorPower } returns 987.654
            every { batteryLevel } returns 87
            every { output } returns 42
            every { cpuLoad } returns 19
            every { fanStatus } returns 0
            every { chargingStatus } returns 0
            every { version } returns "1.2"
            every { error } returns "E01"
            every { name } returns "Wheel"
            every { model } returns "S22"
            every { serial } returns "123"
            every { rideTimeString } returns "01:02"
            every { ridingTimeString } returns "00:42"
            every { sleepTimerString } returns "00:10"
            every { modeStr } returns "Hard"
            every { chargeTime } returns "00:15"
        }
        val previousLocale = Locale.getDefault()
        startKoin { modules(module { single { config } }) }
        try {
            Locale.setDefault(Locale.FRANCE)
            val metric = TelemetryPresentation.values(context, config, model)
            val expected = mapOf(
                R.string.speed to "16.1 km/h", R.string.top_speed to "32.2 km/h",
                R.string.average_speed to "8.0 km/h", R.string.average_riding_speed to "24.1 km/h",
                R.string.dynamic_speed_limit to "40.2 km/h", R.string.distance to "1.609 km",
                R.string.wheel_distance to "3.219 km", R.string.user_distance to "4.828 km",
                R.string.total_distance to "160.934 km", R.string.voltage to "84.13 V",
                R.string.voltage_sag to "3.46 V", R.string.temperature to "05℃",
                R.string.temperature2 to "25℃", R.string.cpu_temp to "00℃",
                R.string.imu_temp to "-5℃", R.string.angle to "-1.24°", R.string.roll to "2.35°",
                R.string.current to "-2.35 A", R.string.phase_current to "12.35 A",
                R.string.dynamic_current_limit to "18.13 A", R.string.torque to "1.26 N*m",
                R.string.power to "-123.46 W", R.string.motor_power to "987.65 W",
                R.string.battery to "87%", R.string.output to "42%", R.string.cpuload to "19%",
                R.string.fan_status to context.getString(R.string.off),
                R.string.charging_status to context.getString(R.string.discharging),
                R.string.version to "1.2", R.string.error to "E01", R.string.name to "Wheel",
                R.string.model to "S22", R.string.serial_number to "123",
                R.string.ride_time to "01:02", R.string.riding_time to "00:42",
                R.string.sleep_timer to "00:10", R.string.mode to "Hard", R.string.charging to "00:15"
            )
            assertThat(metric).containsExactlyEntriesIn(expected)

            config.useMph = true
            config.useFahrenheit = true
            every { model.fanStatus } returns 1
            every { model.chargingStatus } returns 1
            val imperial = TelemetryPresentation.values(context, config, model)
            assertThat(imperial).containsAtLeastEntriesIn(mapOf(
                R.string.speed to "10.0 mph", R.string.top_speed to "20.0 mph",
                R.string.average_speed to "5.0 mph", R.string.average_riding_speed to "15.0 mph",
                R.string.dynamic_speed_limit to "25.0 mph", R.string.distance to "1.00 mi",
                R.string.wheel_distance to "2.00 mi", R.string.user_distance to "3.00 mi",
                R.string.total_distance to "100.00 mi", R.string.temperature to "41℉",
                R.string.temperature2 to "77℉", R.string.cpu_temp to "32℉",
                R.string.imu_temp to "23℉", R.string.fan_status to context.getString(R.string.on),
                R.string.charging_status to context.getString(R.string.charging)
            ))
        } finally {
            Locale.setDefault(previousLocale)
            stopKoin()
        }
    }
}
