package com.cooper.wheellog.telemetry

import android.content.Context
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import com.cooper.wheellog.utils.MathsUtil
import com.cooper.wheellog.utils.StringUtil.toTempString
import java.util.Locale

/** The legacy params page's selection and formatting, shared by both renderers. */
object TelemetryPresentation {
    fun fields(type: WHEEL_TYPE): List<Int> = when (type) {
        WHEEL_TYPE.KINGSONG -> listOf(
            R.string.speed, R.string.dynamic_speed_limit, R.string.top_speed,
            R.string.average_speed, R.string.average_riding_speed, R.string.battery,
            R.string.output, R.string.cpuload, R.string.temperature, R.string.temperature2,
            R.string.ride_time, R.string.riding_time, R.string.distance, R.string.wheel_distance,
            R.string.user_distance, R.string.total_distance, R.string.voltage, R.string.voltage_sag,
            R.string.current, R.string.power, R.string.fan_status, R.string.charging_status,
            R.string.charging, R.string.mode, R.string.name, R.string.model, R.string.version,
            R.string.serial_number
        )
        WHEEL_TYPE.VETERAN -> listOf(
            R.string.speed, R.string.top_speed, R.string.average_speed, R.string.average_riding_speed,
            R.string.battery, R.string.temperature, R.string.ride_time, R.string.riding_time,
            R.string.distance, R.string.wheel_distance, R.string.user_distance, R.string.total_distance,
            R.string.voltage, R.string.voltage_sag, R.string.current, R.string.phase_current,
            R.string.power, R.string.angle, R.string.sleep_timer, R.string.charging_status,
            R.string.charging, R.string.model, R.string.version
        )
        WHEEL_TYPE.GOTWAY -> listOf(
            R.string.speed, R.string.top_speed, R.string.average_speed, R.string.average_riding_speed,
            R.string.battery, R.string.temperature, R.string.temperature2, R.string.ride_time,
            R.string.riding_time, R.string.distance, R.string.wheel_distance, R.string.user_distance,
            R.string.total_distance, R.string.voltage, R.string.voltage_sag, R.string.current,
            R.string.phase_current, R.string.power, R.string.model, R.string.version, R.string.charging
        )
        WHEEL_TYPE.INMOTION_V2 -> listOf(
            R.string.speed, R.string.dynamic_speed_limit, R.string.torque, R.string.top_speed,
            R.string.average_speed, R.string.average_riding_speed, R.string.battery,
            R.string.temperature, R.string.temperature2, R.string.cpu_temp, R.string.imu_temp,
            R.string.angle, R.string.roll, R.string.ride_time, R.string.riding_time, R.string.distance,
            R.string.wheel_distance, R.string.user_distance, R.string.total_distance, R.string.voltage,
            R.string.voltage_sag, R.string.current, R.string.dynamic_current_limit, R.string.power,
            R.string.motor_power, R.string.mode, R.string.model, R.string.version, R.string.serial_number
        )
        WHEEL_TYPE.INMOTION -> listOf(
            R.string.speed, R.string.top_speed, R.string.average_speed, R.string.average_riding_speed,
            R.string.battery, R.string.temperature, R.string.imu_temp, R.string.angle, R.string.roll,
            R.string.ride_time, R.string.riding_time, R.string.distance, R.string.wheel_distance,
            R.string.user_distance, R.string.total_distance, R.string.voltage, R.string.voltage_sag,
            R.string.current, R.string.power, R.string.mode, R.string.model, R.string.version,
            R.string.serial_number, R.string.charging
        )
        WHEEL_TYPE.NINEBOT_Z, WHEEL_TYPE.NINEBOT -> listOf(
            R.string.speed, R.string.top_speed, R.string.average_speed, R.string.average_riding_speed,
            R.string.battery, R.string.temperature, R.string.ride_time, R.string.riding_time,
            R.string.distance, R.string.user_distance, R.string.total_distance, R.string.voltage,
            R.string.voltage_sag, R.string.current, R.string.power, R.string.model, R.string.version,
            R.string.error, R.string.serial_number
        )
        else -> emptyList()
    }

    fun values(context: Context, config: AppConfig, model: BleSessionViewModel): Map<Int, String> {
        fun speed(value: Double) = String.format(
            Locale.US, "%.1f " + context.getString(if (config.useMph) R.string.mph else R.string.kmh),
            if (config.useMph) MathsUtil.kmToMiles(value) else value
        )
        fun distance(value: Double) = String.format(
            Locale.US, (if (config.useMph) "%.2f " else "%.3f ") +
                context.getString(if (config.useMph) R.string.miles else R.string.km),
            if (config.useMph) MathsUtil.kmToMiles(value) else value
        )
        fun unit(value: Double, resource: Int) =
            String.format(Locale.US, "%.2f " + context.getString(resource), value)
        fun percent(value: Int) = String.format(Locale.US, "%d%%", value)
        return mapOf(
            R.string.speed to speed(model.speedDouble),
            R.string.top_speed to speed(model.topSpeedDouble),
            R.string.average_speed to speed(model.averageSpeedDouble),
            R.string.average_riding_speed to speed(model.averageRidingSpeedDouble),
            R.string.dynamic_speed_limit to speed(model.speedLimit),
            R.string.distance to distance(model.distanceDouble),
            R.string.wheel_distance to distance(model.wheelDistanceDouble),
            R.string.user_distance to distance(model.userDistanceDouble),
            R.string.total_distance to distance(model.totalDistanceDouble),
            R.string.voltage to unit(model.voltageDouble, R.string.volt),
            R.string.voltage_sag to unit(model.voltageSagDouble, R.string.volt),
            R.string.temperature to model.temperatureDouble.toInt().toTempString(),
            R.string.temperature2 to model.motorTemperatureDouble.toInt().toTempString(),
            R.string.cpu_temp to model.cpuTemp.toTempString(),
            R.string.imu_temp to model.imuTemp.toTempString(),
            R.string.angle to String.format(Locale.US, "%.2f°", model.angle),
            R.string.roll to String.format(Locale.US, "%.2f°", model.roll),
            R.string.current to unit(model.currentDouble, R.string.amp),
            R.string.phase_current to unit(model.phaseCurrentDouble, R.string.amp),
            R.string.dynamic_current_limit to unit(model.currentLimit, R.string.amp),
            R.string.torque to unit(model.torque, R.string.newton),
            R.string.power to unit(model.powerDouble, R.string.watt),
            R.string.motor_power to unit(model.motorPower, R.string.watt),
            R.string.battery to percent(model.batteryLevel),
            R.string.fan_status to context.getString(if (model.fanStatus == 0) R.string.off else R.string.on),
            R.string.charging_status to context.getString(
                if (model.chargingStatus == 0) R.string.discharging else R.string.charging
            ),
            R.string.version to String.format(Locale.US, "%s", model.version),
            R.string.error to String.format(Locale.US, "%s", model.error),
            R.string.output to percent(model.output),
            R.string.cpuload to percent(model.cpuLoad),
            R.string.name to model.name,
            R.string.model to model.model,
            R.string.serial_number to model.serial,
            R.string.ride_time to model.rideTimeString,
            R.string.sleep_timer to model.sleepTimerString,
            R.string.riding_time to model.ridingTimeString,
            R.string.mode to model.modeStr,
            R.string.charging to model.chargeTime
        )
    }
}
