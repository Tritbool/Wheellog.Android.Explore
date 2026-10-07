package com.cooper.wheellog.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import com.cooper.wheellog.R
import com.cooper.wheellog.utils.MathsUtil
import com.cooper.wheellog.views.TripModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class TripItemState(
    val uri: Uri,
    val fileName: String,
    val legacyPath: String?,
    val title: String,
    val description: String,
    val description2: String
) {
    val key: String get() = uri.toString()
}

object TripPresentation {
    fun item(context: Context, model: TripModel, trip: TripDataDbEntry?, useMph: Boolean): TripItemState {
        val descriptions = descriptions(context, trip, useMph, model.description)
        return TripItemState(
            model.uri, model.fileName, model.pathLegacyAndroid,
            friendlyName(context, model.title), descriptions.first, descriptions.second
        )
    }

    fun descriptions(context: Context, trip: TripDataDbEntry?, useMph: Boolean, fallback: String): Pair<String, String> {
        if (trip == null || trip.duration == 0) return fallback to ""
        fun format(value: Float) = String.format(Locale.getDefault(), "%.2f", value)
        fun units(value: Float) = format(if (useMph) MathsUtil.kmToMiles(value) else value)
        val speedUnit = context.getString(if (useMph) R.string.mph else R.string.kmh)
        val distanceUnit = context.getString(if (useMph) R.string.miles else R.string.km)
        val first = "🚀 ${units(trip.maxSpeed)} $speedUnit\n♿ ${units(trip.avgSpeed)} $speedUnit" +
            "\n😱 ${trip.maxPwm}%" + if (trip.ecId != 0) "\n⚡ electro.club" else ""
        val consumption = if (useMph) trip.consumptionByKm / MathsUtil.kmToMilesMultiplier.toFloat() else trip.consumptionByKm
        val second = "⌚ ${trip.duration} ${context.getString(R.string.min)}" +
            "\n📏 ${units(trip.distance / 1000f)} $distanceUnit" +
            "\n⚡ ${format(trip.consumptionTotal)} ${context.getString(R.string.wh)}" +
            "\n🔋 ${format(consumption)} ${context.getString(if (useMph) R.string.whmi else R.string.whkm)}"
        return first to second
    }

    fun friendlyName(context: Context, title: String, now: Long = System.currentTimeMillis()): String {
        val locale = Locale.getDefault()
        val date = try {
            SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).parse(title)
        } catch (_: Exception) { null } ?: return title
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val then = Calendar.getInstance().apply { time = date }
        return when {
            today.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) ->
                context.getString(R.string.today) + SimpleDateFormat(", EEE, HH:mm", locale).format(date)
            now - date.time < 604_800_000 ->
                SimpleDateFormat("EEEE, HH:mm", locale).format(date)
            else -> {
                val previousYear = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    today.get(Calendar.YEAR) != then.get(Calendar.YEAR)
                val skeleton = (if (previousYear) "yyyy " else "") + "MMMM dd, HH:mm"
                SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
            }
        }
    }
}
