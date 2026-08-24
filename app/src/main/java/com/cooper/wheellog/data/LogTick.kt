package com.cooper.wheellog.data

data class LogTick(
    /**
     * Time in 1/10 second
     */
    val time: Float,
    val timeString: String,
    /**
     * Time in 1/10 second + day of week
     */
    val timePlusDayOfWeek: Float,
    val batteryLevel: Int,
    /**
     * Battery voltage in Volts
     */
    val voltage: Double,
    /**
     * Current in Ampere
     */
    val current: Double,
    /**
     * Power in Watts
     */
    val power: Double,
    /**
     * Speed in km/h
     */
    val speed: Double,
    /**
     * Temperature in Celsius
     */
    val temperature: Int,
    /**
     * PWM in %
     */
    val pwm: Double,
    /**
     * Distance in meters
     */
    val distance: Int,
    /**
     * Total distance in meters
     */
    val totalDistance: Int,
)