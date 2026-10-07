package com.cooper.wheellog

import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.res.ResourcesCompat
import androidx.gridlayout.widget.GridLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.bms.BmsMapper
import com.cooper.wheellog.bms.BmsPresentation
import com.cooper.wheellog.compose.SmartBmsScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class BmsScroll {
    val compose = ScrollState(0)
    var viewsY = 0
}

internal class BmsPageRenderer(
    page: View,
    private val session: BleSessionViewModel,
    private val config: AppConfig,
    private val scroll: BmsScroll,
    private val owner: LifecycleOwner
) {
    private val context = page.context
    private val views = page.findViewById<ScrollView>(R.id.bms_views_scroll)
    private val grid = page.findViewById<GridLayout>(R.id.page_smart_bms_grid)
    private val compose = page.findViewById<ComposeView>(R.id.bmsComposeView)
    private val presentation = mutableStateOf(BmsMapper.present(session.bmsDisplay.value))
    private val theme = mutableStateOf(config.appTheme)
    private var shape: Pair<Boolean, List<Int>>? = null
    private var collecting: Job? = null
    private var disposed = false
    private var restoredViewsScroll = false

    init {
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { SmartBmsScreen(presentation.value, theme.value, scroll.compose) }
        preferences(config.useComposeBms, config.appTheme)
        render(presentation.value)
        views.post {
            if (!disposed) {
                views.scrollTo(0, scroll.viewsY)
                restoredViewsScroll = true
            }
        }
    }

    fun preferences(useCompose: Boolean, appTheme: Int) {
        if (disposed) return
        saveScroll()
        if (theme.value != appTheme) {
            theme.value = appTheme
            shape = null
            render(presentation.value)
        }
        views.visibility = if (useCompose) View.GONE else View.VISIBLE
        compose.visibility = if (useCompose) View.VISIBLE else View.GONE
    }

    private fun render(data: BmsPresentation) {
        presentation.value = data
        val newShape = data.showSecond to data.rows.map { it.label }
        if (shape != newShape) {
            shape = newShape
            grid.removeAllViews()
            grid.columnCount = if (data.showSecond) 4 else 2
            val inflater = LayoutInflater.from(context)
            val font = ResourcesCompat.getFont(context, if (theme.value == R.style.AJDMTheme) R.font.ajdm else R.font.prime)
            fun add(template: Int, text: String) {
                grid.addView((inflater.inflate(template, grid, false) as TextView).apply {
                    this.text = text
                    typeface = font
                })
            }
            add(R.layout.textview_smart_bms_battery_template, context.getString(R.string.bmsBattery1Title))
            if (data.showSecond) add(R.layout.textview_smart_bms_battery_template, context.getString(R.string.bmsBattery2Title))
            data.rows.forEach {
                add(R.layout.textview_smart_bms_title_template, context.getString(it.label))
                add(R.layout.textview_smart_bms_value_template, it.first)
                if (data.showSecond) {
                    add(R.layout.textview_smart_bms_title_template, context.getString(it.label))
                    add(R.layout.textview_smart_bms_value_template, it.second)
                }
            }
        } else {
            val headers = if (data.showSecond) 2 else 1
            val stride = if (data.showSecond) 4 else 2
            data.rows.forEachIndexed { index, row ->
                (grid.getChildAt(headers + index * stride + 1) as TextView).text = row.first
                if (data.showSecond) (grid.getChildAt(headers + index * stride + 3) as TextView).text = row.second
            }
        }
    }

    fun refresh() {
        if (!disposed) render(BmsMapper.present(session.bmsDisplay.value))
    }

    fun start() {
        if (disposed || collecting?.isActive == true) return
        collecting = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(session.bmsDisplay, config.telemetryPreferences()) { snapshot, prefs -> snapshot to prefs }
                    .collect { (snapshot, prefs) ->
                        preferences(prefs.useComposeBms, prefs.appTheme)
                        render(BmsMapper.present(snapshot))
                    }
            }
        }
    }

    private fun saveScroll() {
        if (restoredViewsScroll && views.visibility == View.VISIBLE) scroll.viewsY = views.scrollY
    }

    fun stop() {
        saveScroll()
        collecting?.cancel()
        collecting = null
    }

    fun dispose() {
        if (disposed) return
        stop()
        disposed = true
        compose.disposeComposition()
    }
}
