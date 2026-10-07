package com.cooper.wheellog.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.cooper.wheellog.feature.dashboard.*

/** View fallback using exactly the same presentation, canvas geometry and actions as Compose. */
@SuppressLint("ClickableViewAccessibility")
class WheelView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val renderer = DashboardCanvasRenderer(context)
    private var state = DashboardUiState.EMPTY
    private var actions: DashboardActions? = null
    private val gestures = DashboardGestures(context, { state },
        { renderer.geometry(width.toFloat(), height.toFloat(), state) },
        { actions?.singleTap() }, { actions?.doubleTap() },
        { block, data -> actions?.replaceBlock(block, data) })
    var refreshDisplay = false
    private var drawingEnabled = true

    init { setOnTouchListener { _, event -> gestures.touch(event) } }

    fun render(data: DashboardUiState, callbacks: DashboardActions) {
        state = data
        actions = callbacks
        drawingEnabled = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderer.draw(canvas, width.toFloat(), height.toFloat(), state)
        refreshDisplay = renderer.animating
        if (drawingEnabled && refreshDisplay && isAttachedToWindow && visibility == VISIBLE) postInvalidateDelayed(30)
    }

    fun stop() { drawingEnabled = false; gestures.cancel() }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
}
