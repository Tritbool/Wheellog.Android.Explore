package com.cooper.wheellog.feature.dashboard

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath

// ── Arc geometry constants (match legacy WheelView) ──────────────────────────
private const val ARC_START_ANGLE = 144f
private const val ARC_TOTAL_SWEEP = 252f
private const val SEGMENT_COUNT = 112
private const val SEGMENT_SWEEP = 1.5f                        // each lit segment, degrees
private const val SEGMENT_STEP = ARC_TOTAL_SWEEP / SEGMENT_COUNT // ≈ 2.25°/segment

// Battery inner arc: 40 segments span the left 90° of the inner ring
private const val BATTERY_SEGMENTS = 40
// Temperature inner arc: 40 segments span the right 90°, drawn right-to-left
private const val TEMP_SEGMENTS = 40

// ── Colour palette (matches res/values/colors.xml for OriginalTheme) ─────────
private val COLOR_ARC_DIM = Color(0x30000000)
private val COLOR_MAIN_POSITIVE = Color(0xFFFF5722)   // orange-red (forward speed)
private val COLOR_MAIN_NEGATIVE = Color(0xFF00CC00)   // green      (regen / negative)
private val COLOR_ALARM_WARN = Color(0xFFFFCC00)
private val COLOR_ALARM_CRITICAL = Color(0xFFFF1744)
private val COLOR_BATTERY = Color(0xFF00CC00)
private val COLOR_BATTERY_LOW = Color(0xFFCCCC00)
private val COLOR_TEMPERATURE = Color(0xFF33CCFF)
private val COLOR_SPEED_TEXT = Color.White
private val COLOR_LABEL = Color(0xAAFFFFFF)
private val COLOR_PWM_SAFE = Color(0xAAFFFFFF)
private val COLOR_PWM_WARN_START = Color(0xAAFFFFAA)
private val COLOR_PWM_CRITICAL = Color(0xFFFF0000)

/**
 * Jetpack Compose Canvas gauge that replaces the legacy [WheelView].
 *
 * Rendering is driven entirely by the immutable [DashboardUiState] — no
 * preferences or singletons are accessed from here.
 *
 * Tapping the gauge calls [onToggleDisplayMode] so the [DashboardViewModel]
 * can flip the speed ↔ PWM display mode.
 */
@Composable
fun DashboardGauge(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onToggleDisplayMode: () -> Unit = {}
) {
    // ── Animated values ───────────────────────────────────────────────────────
    val animatedDial by animateFloatAsState(
        targetValue = state.mainDialFraction,
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "dial"
    )
    val animatedBattery by animateFloatAsState(
        targetValue = state.batteryFraction,
        animationSpec = tween(durationMillis = 400),
        label = "battery"
    )
    val animatedTemp by animateFloatAsState(
        targetValue = state.temperatureFraction,
        animationSpec = tween(durationMillis = 400),
        label = "temperature"
    )
    val animatedMaxTemp by animateFloatAsState(
        targetValue = state.maxTemperatureFraction,
        animationSpec = tween(durationMillis = 400),
        label = "maxTemperature"
    )

    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onToggleDisplayMode() }
            }
    ) {
        val canvasSize = minOf(size.width, size.height)
        val outerStrokeWidth = canvasSize / 8f
        val innerStrokeWidth = outerStrokeWidth * 0.6f
        val oaDiameter = canvasSize - outerStrokeWidth
        val oaRadius = oaDiameter / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Outer arc rectangle (centred on canvas)
        val outerRect = Rect(
            left = cx - oaRadius,
            top = cy - oaRadius,
            right = cx + oaRadius,
            bottom = cy + oaRadius
        )

        // Inner arc rectangle — inset by half of each stroke so rings don't overlap
        val innerInset = outerStrokeWidth / 2f + innerStrokeWidth / 2f + innerStrokeWidth * 0.4f
        val iaDiameter = oaDiameter - innerInset * 2f
        val iaRadius = iaDiameter / 2f
        val innerRect = Rect(
            left = cx - iaRadius,
            top = cy - iaRadius,
            right = cx + iaRadius,
            bottom = cy + iaRadius
        )

        // ── Segment counts ──────────────────────────────────────────────────
        val dialSeg = (animatedDial * SEGMENT_COUNT).roundToInt().coerceIn(0, SEGMENT_COUNT)
        val batterySeg = (animatedBattery * BATTERY_SEGMENTS).roundToInt().coerceIn(0, BATTERY_SEGMENTS)
        val batteryLowestSeg = (state.batteryLowestFraction * BATTERY_SEGMENTS).roundToInt()
            .coerceIn(0, BATTERY_SEGMENTS)
        // Temperature fills from the right side of the inner arc (segment 111 → 72)
        val tempThreshold = SEGMENT_COUNT - (animatedTemp * TEMP_SEGMENTS).roundToInt()
            .coerceIn(0, TEMP_SEGMENTS)
        // Fixed marker position for the max-reached-temperature label (doesn't track
        // the live/current reading, so it never flickers on transient bad telemetry).
        val maxTempThreshold = SEGMENT_COUNT - (animatedMaxTemp * TEMP_SEGMENTS).roundToInt()
            .coerceIn(0, TEMP_SEGMENTS)

        val pwmColor = pwmColor(state.pwm, state.colorPwmStart, state.colorPwmEnd)

        // ── Draw outer (main dial) arc ──────────────────────────────────────
        drawSegmentedArc(
            rect = outerRect,
            strokeWidth = outerStrokeWidth,
            dimColor = COLOR_ARC_DIM,
            totalSegments = SEGMENT_COUNT,
            activeSegments = dialSeg,
            activeColor = mainDialColor(state, pwmColor)
        )

        // ── Draw inner (battery + temperature) arc ──────────────────────────
        // Background tracks
        drawArcSegment(innerRect, ARC_START_ANGLE, 90f, innerStrokeWidth, COLOR_ARC_DIM)
        drawArcSegment(innerRect, 306f, 90f, innerStrokeWidth, COLOR_ARC_DIM)

        // Active inner arc segments.
        // The battery-lowest marker is drawn as a narrow band (not a colour that
        // persists to the end of the ring) so it reads as "lowest point reached
        // this session" rather than an unexplained wash of yellow.
        val batteryLowestMarkerWidth = 2
        val batteryLowestMarkerStart = (batteryLowestSeg - batteryLowestMarkerWidth + 1)
            .coerceAtLeast(0)
        for (i in 0..111) {
            val innerColor = when {
                i >= tempThreshold -> COLOR_TEMPERATURE
                batteryLowestSeg in 1 until batterySeg && i in batteryLowestMarkerStart..batteryLowestSeg ->
                    COLOR_BATTERY_LOW
                else -> COLOR_BATTERY
            }
            if (i < batterySeg || i >= tempThreshold) {
                val startAngle = ARC_START_ANGLE + i * SEGMENT_STEP
                drawArcSegment(innerRect, startAngle, SEGMENT_SWEEP, innerStrokeWidth, innerColor)
            }
        }

        // ── Draw centre text (main value) ────────────────────────────────────
        val mainTextStr = when (state.displayMode) {
            DisplayMode.SPEED -> state.speedDisplay
            DisplayMode.PWM -> state.pwm.roundToInt().toString()
        }
        val mainTextColor = when {
            state.displayMode == DisplayMode.PWM -> pwmColor
            state.alarmLevel == AlarmLevel.CRITICAL -> COLOR_ALARM_CRITICAL
            state.alarmLevel == AlarmLevel.WARN -> COLOR_ALARM_WARN
            else -> COLOR_SPEED_TEXT
        }
        val mainLayout = measureFittedText(
            textMeasurer = textMeasurer,
            text = mainTextStr,
            color = mainTextColor,
            fontWeight = FontWeight.Bold,
            maxFontPx = iaDiameter / 4.6f,
            targetWidthPx = iaDiameter * 0.72f,
            targetHeightPx = iaDiameter * 0.34f
        )
        val mainTop = cy - mainLayout.size.height / 2f - iaDiameter * 0.02f
        drawText(mainLayout, topLeft = Offset(cx - mainLayout.size.width / 2f, mainTop))

        // ── Sub-label (unit or short PWM string) ────────────────────────────
        val subFontSize = (iaDiameter / 11f).coerceAtLeast(8f).sp
        val (subText, subColor) = buildSubLabel(state, pwmColor)
        val subLayout = textMeasurer.measure(
            text = subText,
            style = TextStyle(color = subColor, fontSize = subFontSize)
        )
        val subTop = mainTop + mainLayout.size.height + iaDiameter * 0.01f
        drawText(subLayout, topLeft = Offset(cx - subLayout.size.width / 2f, subTop))

        if (state.isConnected) {
            drawArcLabel(
                text = state.batteryDisplay,
                cx = cx,
                cy = cy,
                rotationDegrees = ARC_START_ANGLE + batterySeg * SEGMENT_STEP - 180f,
                textX = innerRect.left - innerStrokeWidth / 2f,
                textY = cy,
                color = COLOR_LABEL,
                textSizePx = innerStrokeWidth * 0.6f
            )
            drawArcLabel(
                text = state.maxTemperatureDisplay,
                cx = cx,
                cy = cy,
                rotationDegrees = 143.5f + maxTempThreshold * SEGMENT_STEP,
                textX = innerRect.right + innerStrokeWidth / 2f,
                textY = cy,
                color = COLOR_LABEL,
                textSizePx = innerStrokeWidth * 0.42f
            )
            if (state.wheelModel.isNotBlank()) {
                drawWheelModelLabel(
                    text = state.wheelModel,
                    rect = innerRect,
                    padding = innerStrokeWidth * 0.35f,
                    textSizePx = innerStrokeWidth * 0.36f
                )
            }
        }
    }
}

// ── Arc drawing helpers ───────────────────────────────────────────────────────

/** Draw a full dim background arc plus [activeSegments] lit segments. */
private fun DrawScope.drawSegmentedArc(
    rect: Rect,
    strokeWidth: Float,
    dimColor: Color,
    totalSegments: Int,
    activeSegments: Int,
    activeColor: Color
) {
    // Dim background
    drawArcSegment(rect, ARC_START_ANGLE, ARC_TOTAL_SWEEP, strokeWidth, dimColor)
    // Active portion
    for (i in 0 until activeSegments) {
        val startAngle = ARC_START_ANGLE + i * SEGMENT_STEP
        drawArcSegment(rect, startAngle, SEGMENT_SWEEP, strokeWidth, activeColor)
    }
}

private fun DrawScope.drawArcSegment(
    rect: Rect,
    startAngle: Float,
    sweepAngle: Float,
    strokeWidth: Float,
    color: Color
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
    )
}

// ── Colour helpers ────────────────────────────────────────────────────────────

private fun mainDialColor(state: DashboardUiState, pwmColor: Color): Color = when {
    state.displayMode == DisplayMode.PWM -> pwmColor
    state.alarmLevel == AlarmLevel.CRITICAL -> COLOR_ALARM_CRITICAL
    state.alarmLevel == AlarmLevel.WARN -> COLOR_ALARM_WARN
    else -> COLOR_MAIN_POSITIVE
}

/**
 * Returns the PWM colour: white below [startPct], linear gradient from
 * warm-white to full red between [startPct] and [endPct], then red above.
 * Matches [WheelView.getPwmColor].
 */
fun pwmColor(pwm: Float, startPct: Int, endPct: Int): Color = when {
    pwm < startPct -> COLOR_PWM_SAFE
    pwm >= endPct -> COLOR_PWM_CRITICAL
    else -> lerp(
        COLOR_PWM_WARN_START,
        COLOR_PWM_CRITICAL,
        (pwm - startPct).toFloat() / (endPct - startPct).toFloat()
    )
}

private fun buildSubLabel(state: DashboardUiState, pwmColor: Color): Pair<String, Color> {
    return when {
        // Short-PWM mode, speed on dial: show "pwm% / maxPwm%" in PWM colour
        state.useShortPwm && state.displayMode == DisplayMode.SPEED ->
            String.format("%02.0f%% / %02.0f%%", state.pwm, state.maxPwm) to pwmColor

        // Short-PWM mode, PWM on dial: show speed value with unit
        state.useShortPwm && state.displayMode == DisplayMode.PWM ->
            "${state.speedDisplay} ${state.speedUnit}" to COLOR_LABEL

        // Default: just the unit label
        else -> state.speedUnit to COLOR_LABEL
    }
}

private fun DrawScope.measureFittedText(
    textMeasurer: TextMeasurer,
    text: String,
    color: Color,
    fontWeight: FontWeight? = null,
    maxFontPx: Float,
    targetWidthPx: Float,
    targetHeightPx: Float
) = run {
    var fontPx = maxFontPx.coerceAtLeast(12f)
    var layout = textMeasurer.measure(
        text = text,
        style = TextStyle(color = color, fontSize = fontPx.toSp(), fontWeight = fontWeight)
    )
    val widthScale = targetWidthPx / layout.size.width.coerceAtLeast(1)
    val heightScale = targetHeightPx / layout.size.height.coerceAtLeast(1)
    val fitScale = min(widthScale, heightScale)
    if (fitScale < 1f) {
        fontPx = (fontPx * fitScale * 0.98f).coerceAtLeast(12f)
        layout = textMeasurer.measure(
            text = text,
            style = TextStyle(color = color, fontSize = fontPx.toSp(), fontWeight = fontWeight)
        )
    }
    layout
}

private fun DrawScope.drawArcLabel(
    text: String,
    cx: Float,
    cy: Float,
    rotationDegrees: Float,
    textX: Float,
    textY: Float,
    color: Color,
    textSizePx: Float
) {
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = AndroidPaint.Align.CENTER
            textSize = textSizePx
        }
        nativeCanvas.save()
        nativeCanvas.rotate(rotationDegrees, cx, cy)
        nativeCanvas.drawText(text, textX, textY, paint)
        nativeCanvas.restore()
    }
}

private fun DrawScope.drawWheelModelLabel(
    text: String,
    rect: Rect,
    padding: Float,
    textSizePx: Float
) {
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LABEL.toArgb()
            textAlign = AndroidPaint.Align.CENTER
            textSize = textSizePx
        }
        val path = AndroidPath().apply {
            addArc(
                rect.left + padding,
                rect.top + padding,
                rect.right - padding,
                rect.bottom - padding,
                190f,
                160f
            )
        }
        nativeCanvas.drawTextOnPath(text, path, 0f, 0f, paint)
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF284a73)
@Composable
private fun DashboardGaugePreview_Connected() {
    DashboardGauge(
        state = DashboardUiState(
            isConnected = true,
            speed = 28.5f,
            speedDisplay = "28.5",
            speedUnit = "km/h",
            pwm = 55f,
            maxPwm = 72f,
            battery = 63,
            batteryLowest = 60,
            temperature = 32f,
            maxTemperature = 38f,
            voltage = 67.2f,
            current = 12.5f,
            topSpeed = 35f,
            distance = 12.4f,
            displayMode = DisplayMode.SPEED,
            mainDialFraction = 0.57f,
            batteryFraction = 0.63f,
            temperatureFraction = 0.4f,
            maxTemperatureFraction = 0.475f,
            batteryLowestFraction = 0.60f,
            colorPwmStart = 60,
            colorPwmEnd = 90
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF284a73)
@Composable
private fun DashboardGaugePreview_Alarm() {
    DashboardGauge(
        state = DashboardUiState(
            isConnected = true,
            speed = 42f,
            speedDisplay = "42.0",
            speedUnit = "km/h",
            pwm = 88f,
            maxPwm = 88f,
            battery = 45,
            temperature = 55f,
            displayMode = DisplayMode.SPEED,
            alarmLevel = AlarmLevel.CRITICAL,
            mainDialFraction = 0.84f,
            batteryFraction = 0.45f,
            temperatureFraction = 0.69f,
            batteryLowestFraction = 0.44f,
            colorPwmStart = 60,
            colorPwmEnd = 90
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF284a73)
@Composable
private fun DashboardGaugePreview_PwmMode() {
    DashboardGauge(
        state = DashboardUiState(
            isConnected = true,
            speed = 25f,
            speedDisplay = "25.0",
            speedUnit = "km/h",
            pwm = 72f,
            maxPwm = 78f,
            battery = 70,
            temperature = 28f,
            displayMode = DisplayMode.PWM,
            useShortPwm = false,
            mainDialFraction = 0.72f / 50f,   // pwm/maxSpeed
            batteryFraction = 0.7f,
            temperatureFraction = 0.35f,
            batteryLowestFraction = 0.68f,
            colorPwmStart = 60,
            colorPwmEnd = 90
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    )
}
