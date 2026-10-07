package com.cooper.wheellog.feature.dashboard

import android.content.Context
import android.content.res.Configuration
import com.cooper.wheellog.R
import com.cooper.wheellog.utils.MathsUtil
import java.util.Locale
import kotlin.math.roundToInt

/** Resource ids also identify metrics; persisted selections remain localized legacy titles. */
object DashboardCatalogue {
    val labels = listOf(
        R.string.pwm, R.string.max_pwm, R.string.voltage, R.string.average_riding_speed,
        R.string.riding_time, R.string.speed, R.string.top_speed, R.string.distance,
        R.string.total, R.string.battery, R.string.current, R.string.phase_current,
        R.string.maxcurrent, R.string.maxphasecurrent, R.string.power, R.string.maxpower,
        R.string.temperature, R.string.temperature2, R.string.maxtemperature,
        R.string.average_speed, R.string.ride_time, R.string.wheel_distance,
        R.string.remaining_distance, R.string.battery_per_km, R.string.avg_cell_volt,
        R.string.user_distance, R.string.consumption
    )

    fun select(
        titles: List<String>,
        catalogue: List<DashboardBlock>,
        aliases: Map<String, Int> = emptyMap()
    ): List<DashboardBlock> =
        titles.mapIndexedNotNull { slot, title ->
            (catalogue.firstOrNull { it.label == title }
                ?: aliases[title]?.let { metric -> catalogue.firstOrNull { it.metric == metric } })
                ?.copy(slot = slot, selectionKey = title)
        }

    /** Read legacy titles in packaged locales without rewriting persisted selections. */
    fun aliases(context: Context): Map<String, Int> {
        val titles = mutableMapOf<String, MutableSet<Int>>()
        val languages = (listOf("en", "fr", "ru") + context.assets.locales.orEmpty()).distinct()
        languages.forEach { language ->
            val localized = context.createConfigurationContext(Configuration().apply {
                setLocale(Locale.forLanguageTag(language.replace('_', '-')))
            })
            labels.forEach { metric ->
                val title = localized.getString(metric)
                if (title.isNotBlank()) titles.getOrPut(title) { mutableSetOf() }.add(metric)
            }
        }
        return titles.filterValues { it.size == 1 }.mapValues { it.value.single() }
    }

    fun replace(titles: List<String>, slot: Int, title: String): List<String> =
        titles.mapIndexed { index, old -> if (index == slot) title else old }
}

object DashboardFormatting {
    fun speed(tenthsKmh: Int, mph: Boolean): String {
        val tenths = if (mph) MathsUtil.kmToMiles(tenthsKmh.toFloat()).roundToInt() else tenthsKmh
        return if (tenths < 100) format("%.1f", tenths / 10.0)
        else format("%02d", (tenths / 10.0).roundToInt())
    }

    fun temperature(celsius: Int, fahrenheit: Boolean): String =
        if (fahrenheit) format("%02d℉", MathsUtil.celsiusToFahrenheit(celsius.toDouble()).toInt())
        else format("%02d℃", celsius)

    fun format(pattern: String, vararg values: Any): String =
        String.format(Locale.US, pattern, *values)

    fun fraction(value: Float, maximum: Int): Float =
        if (!value.isFinite()) 0f else (value / maximum.coerceAtLeast(1)).coerceIn(-1f, 1f)
}
