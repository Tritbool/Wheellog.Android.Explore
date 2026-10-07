package com.cooper.wheellog.feature.dashboard

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.DialogHelper.setBlackIcon
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.utils.Alarms
import com.cooper.wheellog.utils.SomeUtil
import io.github.tritbool.euc.ble.protocols.CommandType

/** Dashboard commands live in gesture callbacks, never in Compose drawing or recomposition. */
class DashboardActions(
    private val context: Context,
    private val session: BleSessionViewModel,
    private val config: AppConfig
) {
    private var dialog: AlertDialog? = null
    private var active = true

    fun start() { active = true }

    fun singleTap() {
        if (!active) return
        if (config.useBeepOnSingleTap) SomeUtil.playBeep()
    }

    fun doubleTap() {
        if (!active) return
        toggleLight(session, config)
    }

    fun replaceBlock(block: DashboardBlock, state: DashboardUiState) {
        if (!active) return
        val titles = config.viewBlocks.toList()
        if (titles.getOrNull(block.slot) != block.selectionKey) return
        val selected = state.infoBlocks.filter { it.metric != 0 }.map { it.metric }.toSet()
        val items = state.catalogue.filter { it.metric !in selected && it.label !in titles }.map { it.label }
        if (items.isEmpty()) return
        Alarms.vibrate(context, longArrayOf(0, 50, 50))
        dialog?.dismiss()
        dialog = AlertDialog.Builder(context, R.style.OriginalTheme_Dialog_Alert)
            .setIcon(R.drawable.ic_baseline_dashboard_customize_24)
            .setTitle(context.getString(R.string.replace_info_block, block.label))
            .setItems(items.toTypedArray()) { _, which ->
                val latest = config.viewBlocks.toList()
                if (latest.getOrNull(block.slot) == block.selectionKey) {
                    config.viewBlocks = DashboardCatalogue.replace(latest, block.slot, items[which]).toTypedArray()
                }
            }
            .setCancelable(true).create()
        dialog?.show()
        dialog?.setBlackIcon()
    }

    fun dispose() { active = false; dialog?.dismiss(); dialog = null }

    companion object {
        fun toggleLight(session: BleSessionViewModel, config: AppConfig) {
            val on = session.isCommandSupported(CommandType.LIGHT_ON)
            val off = session.isCommandSupported(CommandType.LIGHT_OFF)
            if (!on && !off) return
            val enabled = session.sessionState.value.lightMode?.let { it != 1 } ?: config.lightEnabled
            val command = when {
                enabled && off -> CommandType.LIGHT_OFF
                !enabled && on -> CommandType.LIGHT_ON
                on -> CommandType.LIGHT_ON
                else -> CommandType.LIGHT_OFF
            }
            config.lightEnabled = command == CommandType.LIGHT_ON
            session.sendCommand(command)
        }
    }
}

class DashboardGestures(
    context: Context,
    private val state: () -> DashboardUiState,
    private val geometry: () -> DashboardGeometry?,
    private val single: () -> Unit,
    private val double: () -> Unit,
    private val long: (DashboardBlock, DashboardUiState) -> Unit
) {
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            single()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { double(); return true }
        override fun onLongPress(e: MotionEvent) {
            val data = state()
            data.infoBlocks.getOrNull(geometry()?.blockAt(e.x, e.y) ?: -1)?.let { long(it, data) }
        }
    })

    fun touch(event: MotionEvent): Boolean { detector.onTouchEvent(event); return true }

    fun cancel() {
        val now = android.os.SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0).also {
            detector.onTouchEvent(it)
            it.recycle()
        }
    }
}
