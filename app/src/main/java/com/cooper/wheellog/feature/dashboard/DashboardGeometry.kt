package com.cooper.wheellog.feature.dashboard

import kotlin.math.*

data class DashboardRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

/** Original dashboard pixel geometry, shared by the Compose canvas and gesture hit testing. */
data class DashboardGeometry(
    val outer: DashboardRect,
    val middle: DashboardRect,
    val inner: DashboardRect,
    val speed: DashboardRect,
    val blocks: List<DashboardRect>,
    val outerStroke: Float,
    val innerStroke: Float,
    val landscape: Boolean
) {
    fun blockAt(x: Float, y: Float) = blocks.indexOfFirst { it.contains(x, y) }

    companion object {
        fun calculate(w: Float, h: Float, density: Float, ajdm: Boolean, count: Int): DashboardGeometry {
            val pad = 10 * density
            val landscape = w > h
            val ww = (min(w, h) - 2 * pad).coerceAtLeast(1f)
            val outerStroke = ww / 8
            val innerStroke = (outerStroke * .6f).roundToInt().toFloat()
            val diameter = ww - outerStroke
            val cx = w / 2
            val cy = if (landscape) h / 2 else ww / 2 + pad
            fun circle(d: Float): DashboardRect {
                val radius = d.coerceAtLeast(1f) / 2
                return DashboardRect(cx - radius, cy - radius, cx + radius, cy + radius)
            }
            val outer = circle(diameter)
            val middle = circle(diameter - outerStroke - 20 * density)
            val inner = circle(if (ajdm) middle.width - 10 * density - innerStroke - 10 * density
                else diameter - outerStroke - innerStroke - 10 * density)
            val speedSize = (sqrt(2.0) * (inner.width - innerStroke).roundToInt() / 2)
                .roundToInt().coerceAtLeast(1).toFloat()
            val speed = DashboardRect(cx - speedSize / 2, cy - speedSize / 2, cx + speedSize / 2, cy + speedSize / 2)
            val boxes = mutableListOf<DashboardRect>()
            if (count > 0 && w > 2 * pad && h > 2 * pad) {
                var cols = 2
                var rows = ceil(count / 2.0).toInt()
                var top = if (landscape) pad else pad + outer.top + diameter / 2 +
                    (cos(Math.toRadians(54.0)) * (diameter + outerStroke) / 2).toFloat()
                if (!landscape) {
                    if (count == 1) { cols = 1; rows = 1 }
                    else {
                        val available = (h - top - 2 * pad).coerceAtLeast(1f)
                        rows = sqrt(count / (w / available) * 3).toInt().coerceIn(1, count)
                        cols = ceil(count.toDouble() / rows).toInt()
                        rows = ceil(count.toDouble() / cols).toInt()
                    }
                }
                val boxH = (h - top - pad) / rows - pad
                val boxW = (if (landscape) (w - diameter - pad) / cols - outerStroke
                    else (w - pad) / cols - pad)
                if (boxH > 0 && boxW > 0) {
                    repeat(rows) {
                        repeat(cols) { col ->
                            if (boxes.size < count) {
                                val left = if (landscape && col == 1) w - boxW - pad
                                    else pad + col * (boxW + pad)
                                boxes += DashboardRect(left, top, left + boxW, top + boxH)
                            }
                        }
                        top += boxH + pad
                    }
                }
            }
            return DashboardGeometry(outer, middle, inner, speed, boxes, outerStroke, innerStroke, landscape)
        }
    }
}
