package com.cooper.wheellog.feature.dashboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.content.res.ResourcesCompat
import androidx.appcompat.app.AppCompatDelegate
import com.cooper.wheellog.BuildConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.utils.SomeUtil.getColorEx
import kotlin.math.*

/** The original WheelView drawing contract, without BLE, preferences or View ownership. */
class DashboardCanvasRenderer(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = Rect()
    private var fontTheme = 0
    private var dial = 0
    private var secondary = 0
    private var battery = 0
    private var temperature = 112
    private var paletteKey: Pair<Int, Int>? = null
    private var paletteContext = context
    var animating = false
        private set

    fun geometry(w: Float, h: Float, state: DashboardUiState) =
        DashboardGeometry.calculate(w, h, density, state.appTheme == R.style.AJDMTheme, state.infoBlocks.size)

    private fun fitted(rect: DashboardRect, text: String, lines: Int = 1): Float {
        paint.textSize = 100f
        paint.getTextBounds(text, 0, text.length, bounds)
        val height = rect.height / if (lines == 1) 1f else lines * 1.2f
        val result = (min(height / bounds.height().coerceAtLeast(1),
            rect.width / paint.measureText(text).coerceAtLeast(1f)) * 100).coerceAtLeast(0f)
        paint.textSize = result
        paint.getTextBounds(text, 0, text.length, bounds)
        return result
    }

    fun draw(canvas: Canvas, w: Float, h: Float, state: DashboardUiState) {
        if (w <= 0 || h <= 0) return
        val g = geometry(w, h, state)
        val ajdm = state.appTheme == R.style.AJDMTheme
        val key = state.nightMode to context.resources.configuration.uiMode
        if (paletteKey != key) {
            paletteKey = key
            val night = when (state.nightMode) {
                AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
                AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
                else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            }
            paletteContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            })
        }
        if (fontTheme != state.appTheme) {
            paint.typeface = ResourcesCompat.getFont(context, if (ajdm) R.font.ajdm else R.font.prime)
            fontTheme = state.appTheme
        }
        fun color(original: Int, alternate: Int = original) = paletteContext.getColorEx(if (ajdm) alternate else original)
        val dim = color(R.color.wheelview_arc_dim, R.color.ajdm_wheelview_arc_dim)
        val textColor = color(R.color.wheelview_text, R.color.ajdm_wheelview_text)
        val pwmColor = dashboardPwmColor(state.pwm.toInt(), state.colorPwmStart, state.colorPwmEnd)
        val targetDial = (state.mainDialFraction * 112).roundToInt()
        val targetSecondary = (state.secondaryDialFraction * 112).roundToInt()
        val targetBattery = (state.batteryFraction * 40).roundToInt()
        val lowest = (state.batteryLowestFraction * 40).roundToInt()
        val targetTemperature = 112 - (state.temperatureFraction * 40).roundToInt()
        fun step(target: Int, current: Int) = current + (target - current).sign
        fun fast(target: Int, current: Int) =
            if (abs(target) > abs(current) || target.sign != current.sign) target else step(target, current)
        dial = if (state.valueOnDial == "1" || state.valueOnDial == "3") fast(targetDial, dial) else step(targetDial, dial)
        secondary = if (state.valueOnDial == "1" || state.valueOnDial == "3") step(targetSecondary, secondary)
            else fast(targetSecondary, secondary)
        battery = step(targetBattery, battery)
        temperature = step(targetTemperature, temperature)
        animating = dial != targetDial || (ajdm && secondary != targetSecondary) ||
            battery != targetBattery || temperature != targetTemperature
        fun arc(rect: DashboardRect, start: Float, sweep: Float, stroke: Float, c: Int) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = c
            canvas.drawArc(RectF(rect.left, rect.top, rect.right, rect.bottom), start, sweep, false, paint)
        }
        fun dialArc(rect: DashboardRect, value: Int, stroke: Float, c: Int) {
            arc(rect, 144f, 252f, stroke, dim)
            if (ajdm) arc(rect, 144f, abs(value).coerceAtMost(112) * 2.25f, stroke, c)
            else repeat(abs(value).coerceAtMost(112)) { arc(rect, 144 + it * 2.25f, 1.5f, stroke, c) }
        }
        val dialColor = if (state.valueOnDial == "2") pwmColor else if (dial >= 0)
            color(R.color.wheelview_main_positive_dial, R.color.ajdm_wheelview_main_positive_dial)
            else color(R.color.wheelview_main_negative_dial, R.color.ajdm_wheelview_main_negative_dial)
        dialArc(g.outer, dial, g.outerStroke, dialColor)
        if (ajdm) dialArc(g.middle, secondary, 10 * density, color(
            R.color.ajdm_wheelview_max_speed_dial.takeIf { secondary >= 0 } ?: R.color.ajdm_wheelview_avg_speed_dial))
        arc(g.inner, 144f, 90f, g.innerStroke, dim)
        arc(g.inner, 306f, 90f, g.innerStroke, dim)
        val batteryColor = color(R.color.wheelview_battery_dial, R.color.ajdm_wheelview_battery_dial)
        val lowColor = color(R.color.wheelview_battery_low_dial, R.color.ajdm_wheelview_battery_low_dial)
        val tempColor = color(R.color.wheelview_temperature_dial, R.color.ajdm_wheelview_temperature_dial)
        if (ajdm) {
            arc(g.inner, 144f, battery * 2.25f, g.innerStroke, batteryColor)
            arc(g.inner, 144f, lowest * 2.25f, g.innerStroke, lowColor)
            val value = (temperature - 112) * 2.25f * 100 / 80
            if (state.temperature > 0) arc(g.inner, 306 - value, 90 + value, g.innerStroke, tempColor)
        } else {
            var c = batteryColor
            repeat(112) {
                if (it == lowest) c = lowColor
                if (it == temperature) c = tempColor
                if (it < battery || it >= temperature) arc(g.inner, 144 + it * 2.25f, 1.5f, g.innerStroke, c)
            }
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        val speedSize = fitted(g.speed, "00")
        val speedHeight = bounds.height()
        val speedTop = (g.outer.cy - speedHeight / 2f - speedHeight / 10f).roundToInt().toFloat()
        val speedBottom = speedTop + speedHeight
        paint.color = when {
            state.displayMode == DisplayMode.PWM -> pwmColor
            state.speedWarning -> color(R.color.accent, R.color.ajdm_accent)
            else -> color(R.color.wheelview_speed_text, R.color.ajdm_wheelview_speed_text)
        }
        canvas.drawText(if (state.displayMode == DisplayMode.PWM) state.pwm.roundToInt().toString()
            else state.speedDisplay, g.outer.cx, speedBottom, paint)
        val unitRect = DashboardRect(0f, 0f, g.speed.width / 3, g.speed.height / 3)
        val unitSize = fitted(unitRect, context.getString(R.string.kmh))
        val unitHeight = bounds.height()
        paint.textSize = if (state.useShortPwm) unitSize * 1.2f else unitSize
        paint.color = if (state.useShortPwm && state.displayMode == DisplayMode.SPEED) pwmColor else textColor
        val subtitle = if (!state.useShortPwm) state.speedUnit else if (state.displayMode == DisplayMode.PWM)
            state.speedDisplay + state.speedUnit else DashboardFormatting.format("%02.0f%% / %02.0f%%", state.pwm, state.maxPwm)
        canvas.drawText(subtitle, g.outer.cx, speedBottom + unitHeight *
            if (state.useShortPwm) 3.3f else if (ajdm) 1.1f else 1.7f, paint)
        paint.color = textColor
        val labelRect = DashboardRect(0f, 0f, g.innerStroke, g.innerStroke)
        paint.textSize = fitted(labelRect, "88%")
        fun rotated(text: String, rotation: Float, x: Float) {
            canvas.save()
            canvas.rotate(rotation, g.inner.cx, g.inner.cy)
            canvas.drawText(text, x, g.inner.cy, paint)
            canvas.restore()
        }
        if (if (ajdm) state.temperature > 0 && state.battery > -1 else state.isConnected) {
            rotated(state.batteryDisplay, if (ajdm) 140 + (if (g.landscape) -3.3f else -2f) * 2.25f - 180
                else 144 + battery * 2.25f - 180, g.inner.left)
            if (state.batteryCalculation.isNotEmpty()) rotated(state.batteryCalculation,
                if (ajdm) (if (g.landscape) 147 else 146) + battery * 2.25f - 180
                else 144 + (if (g.landscape) -3.3f else -2f) * 2.25f - 180, g.inner.left)
            rotated(state.temperatureDisplay, if (ajdm) (if (g.landscape) 138f else 135f) + 120 * 2.25f
                else 143.5f + temperature * 2.25f, g.inner.right)
            if (!ajdm) rotated(state.maxTemperatureDisplay, -50f, g.inner.right)
        }
        val modelRect = DashboardRect(g.inner.left + 10 * density, g.inner.top + 10 * density,
            g.inner.right - 10 * density, g.inner.bottom - 10 * density)
        paint.color = paletteContext.getColorEx(R.color.wheelview_text)
        val path = Path().apply { addArc(RectF(modelRect.left, modelRect.top, modelRect.right, modelRect.bottom), 190f, 160f) }
        paint.textSize = fitted(modelRect.copy(bottom = modelRect.top + g.innerStroke * 1.2f), state.wheelModel) / 2
        canvas.drawTextOnPath(state.wheelModel, path, 0f, 0f, paint)
        if (g.blocks.isNotEmpty()) {
            val boxTextSize = fitted(g.blocks.first(), "10000 km/h", 2) * 1.2f
            state.infoBlocks.zip(g.blocks).forEach { (block, rect) ->
                val y = rect.cy - 10 * density
                paint.color = paletteContext.getColorEx(R.color.wheelview_text)
                paint.textSize = boxTextSize * .8f
                canvas.drawText(block.value, rect.cx, y, paint)
                paint.textSize = boxTextSize / 2
                paint.alpha = 150
                canvas.drawText(block.label, rect.cx, y + boxTextSize * .7f, paint)
            }
        }
        if (w * 1.2f < h) {
            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = (h / 50).roundToInt().toFloat()
            paint.color = paletteContext.getColorEx(R.color.wheelview_versiontext)
            canvas.drawText("ver ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_DATE}", w - 10 * density, h - 10 * density, paint)
        }
    }
}

fun dashboardPwmColor(value: Int, start: Int, end: Int): Int {
    if (value < start) return 0xAAFFFFFF.toInt()
    if (value >= end) return 0xFFFF0000.toInt()
    val fraction = (value - start).toFloat() / (end - start).coerceAtLeast(1)
    fun channel(from: Int, to: Int) = (from + fraction * (to - from)).toInt()
    return (channel(170, 255) shl 24) or (255 shl 16) or (channel(255, 0) shl 8) or channel(170, 0)
}
