package com.cooper.wheellog

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.view.*
import android.widget.TextView
import android.widget.ScrollView
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.gridlayout.widget.GridLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cooper.wheellog.utils.MathsUtil
import com.cooper.wheellog.utils.SomeUtil.getColorEx
import com.cooper.wheellog.data.TripDao
import com.cooper.wheellog.data.TripRepository
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.compose.MainPageScreen
import com.cooper.wheellog.compose.ParamsListScreen
import com.cooper.wheellog.telemetry.TelemetryPresentation
import com.cooper.wheellog.ui.theme.AppTheme
import com.cooper.wheellog.views.WheelView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class MainPageAdapter(private var pages: MutableList<Int>, val activity: MainActivity) : RecyclerView.Adapter<MainPageAdapter.ViewHolder>(), OnSharedPreferenceChangeListener, KoinComponent {
    private val appConfig: AppConfig by inject()
    private val viewModel: BleSessionViewModel by inject()
    private var xAxisLabels = ArrayList<String>()

    var wheelView: WheelView? = null
    private var dashboardRenderer: DashboardPageRenderer? = null
    private var chart1: LineChart? = null
    var position: Int = -1
        set(value) {
            field = value
            viewModel.bmsView = pages.getOrNull(value) == R.layout.main_view_smart_bms
        }
    private var pagesView = LinkedHashMap<Int, View?>()

    private val tripDao: TripDao by inject()
    private val tripRepository by lazy { TripRepository(tripDao) }
    private val tripsScroll = TripsScroll()
    private var tripsRenderer: TripsPageRenderer? = null
    private val bmsScroll = BmsScroll()
    private var bmsRenderer: BmsPageRenderer? = null
    private var telemetryPreferencesJob: Job? = null
    private val telemetryItems = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    private val telemetryTheme = mutableStateOf(appConfig.appTheme)
    private val telemetryScroll = ScrollState(0)
    private var telemetryViewsScrollY = 0
    private val eventsScroll = ScrollState(0)
    private var eventsViewsScrollY = 0
    private var eventsRenderer: EventsPageRenderer? = null
    private var observing = false

    internal fun pageIds(): List<Int> = pages.toList()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        startObserving()
    }

    internal fun startObserving() {
        if (observing) return
        observing = true
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity.application)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        telemetryPreferencesJob?.cancel()
        eventsRenderer?.start(activity)
        tripsRenderer?.start()
        bmsRenderer?.start()
        dashboardRenderer?.start()
        telemetryPreferencesJob = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appConfig.telemetryPreferences().collect { preferences ->
                    val themeChanged = telemetryTheme.value != preferences.appTheme
                    telemetryTheme.value = preferences.appTheme
                    if (themeChanged) createSecondPage()
                    refreshTelemetryValues()
                    switchTelemetryRenderer()
                    eventsRenderer?.preferences(preferences.useComposeEvents, preferences.appTheme)
                    tripsRenderer?.preferences(
                        preferences.useComposeTrips, preferences.appTheme,
                        preferences.useMph, preferences.autoUploadEc
                    )
                    bmsRenderer?.preferences(preferences.useComposeBms, preferences.appTheme)
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        stopObserving()
    }

    internal fun stopObserving() {
        if (!observing) return
        observing = false
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity.application)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        telemetryPreferencesJob?.cancel()
        telemetryPreferencesJob = null
        saveEventsScroll()
        eventsRenderer?.stop()
        tripsRenderer?.stop()
        bmsRenderer?.stop()
        dashboardRenderer?.stop()
    }

    fun addPage(page: Int, index: Int = 0) {
        if (!pages.contains(page)) {
            if (index == 0 || index >= pages.size) {
                pages.add(page)
            } else {
                pages.add(index, page)
            }
            pagesView[page] = null
            notifyItemInserted(pages.indexOf(page))
        }
    }

    fun removePage(page: Int) {
        if (pages.contains(page)) {
            if (page == R.layout.main_view_smart_bms) {
                bmsRenderer?.dispose()
                bmsRenderer = null
                viewModel.bmsView = false
            }
            if (page == R.layout.main_view_trips) {
                tripsRenderer?.dispose()
                tripsRenderer = null
            }
            if (page == R.layout.main_view_events) {
                saveEventsScroll()
                eventsRenderer?.dispose()
                eventsRenderer = null
            }
            val index = pages.indexOf(page)
            pages.removeAt(index)
            pagesView.remove(page)
            notifyItemRemoved(index)
        }
    }

    fun updatePageOfTrips() {
        tripsRenderer?.refresh()
    }

    fun resetBatteryLowest() { dashboardRenderer?.resetBatteryLowest() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(inflater.inflate(viewType, parent, false))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = holder.itemView
        pagesView[pages[position]] = view
        when (pages[position]) {
            R.layout.main_view_main -> {
                dashboardRenderer?.dispose()
                holder.dashboardRenderer = DashboardPageRenderer(view, viewModel, appConfig, activity)
                dashboardRenderer = holder.dashboardRenderer
                wheelView = dashboardRenderer?.wheelView
            }
            R.layout.main_view_params_list -> {
                createSecondPage()
                view.findViewById<ComposeView>(R.id.paramsComposeView).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        ParamsListScreen(telemetryItems.value, telemetryTheme.value, telemetryScroll)
                    }
                }
                switchTelemetryRenderer()
                view.findViewById<ScrollView>(R.id.params_views_scroll).post {
                    view.findViewById<ScrollView>(R.id.params_views_scroll).scrollTo(0, telemetryViewsScrollY)
                }
            }
            R.layout.main_view_graph -> {
                chart1 = view.findViewById(R.id.chart)
                chart1?.apply {
                    setDrawGridBackground(false)
                    description.isEnabled = false
                    setHardwareAccelerationEnabled(true)
                    isHighlightPerTapEnabled = false
                    isHighlightPerDragEnabled = false
                    legend.textColor = getColorEx(android.R.color.white)
                    setNoDataText(resources.getString(R.string.no_chart_data))
                    setNoDataTextColor(getColorEx(android.R.color.white))
                }

                val leftAxis: YAxis = chart1!!.axisLeft
                val rightAxis: YAxis = chart1!!.axisRight
                leftAxis.axisMinimum = 0f
                rightAxis.axisMinimum = 0f
                leftAxis.setDrawGridLines(false)
                rightAxis.setDrawGridLines(false)
                leftAxis.textColor = view.getColorEx(android.R.color.white)
                rightAxis.textColor = view.getColorEx(android.R.color.white)

                val xAxis: XAxis = chart1!!.xAxis
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.textColor = view.getColorEx(android.R.color.white)
                xAxis.valueFormatter = chartAxisValueFormatter
            }
            R.layout.main_view_events -> {
                eventsRenderer?.dispose()
                holder.eventsRenderer = EventsPageRenderer(
                    view, EventsLoggingTree.events, eventsScroll, appConfig.appTheme
                )
                eventsRenderer = holder.eventsRenderer
                eventsRenderer?.preferences(appConfig.useComposeEvents, appConfig.appTheme)
                view.findViewById<ScrollView>(R.id.events_views_scroll).post {
                    view.findViewById<ScrollView>(R.id.events_views_scroll).scrollTo(0, eventsViewsScrollY)
                }
            }
            R.layout.main_view_trips -> {
                tripsRenderer?.dispose()
                holder.tripsRenderer = TripsPageRenderer(view, tripRepository, appConfig, tripsScroll, activity)
                tripsRenderer = holder.tripsRenderer
            }
            R.layout.main_view_smart_bms -> {
                bmsRenderer?.dispose()
                holder.bmsRenderer = BmsPageRenderer(view, viewModel, appConfig, bmsScroll, activity)
                bmsRenderer = holder.bmsRenderer
            }
        }
    }

    override fun getItemCount(): Int {
        return pages.size
    }

    override fun getItemViewType(position: Int): Int {
        return pages[position]
    }

    fun updateScreen(updateGraph: Boolean) {
        if (position == -1 || position >= pages.size) {
            return
        }
        when (pages[position]) {
            R.layout.main_view_main -> {
                // The dashboard owns lifecycle-bound session and preference collection.
            }
            R.layout.main_view_params_list -> {
                refreshTelemetryValues()
            }
            R.layout.main_view_graph -> {
                if (!updateGraph || chart1 == null) {
                    return
                }
                xAxisLabels = viewModel.xAxis
                if (xAxisLabels.size > 0) {
                    val dataSetSpeed: LineDataSet
                    val dataSetCurrent: LineDataSet
                    if (chart1!!.data == null) {
                        dataSetSpeed = LineDataSet(null, activity.getString(R.string.speed_axis))
                        dataSetCurrent = LineDataSet(null, activity.getString(R.string.current_axis))
                        dataSetSpeed.lineWidth = 2f
                        dataSetCurrent.lineWidth = 2f
                        dataSetSpeed.axisDependency = YAxis.AxisDependency.LEFT
                        dataSetCurrent.axisDependency = YAxis.AxisDependency.RIGHT
                        dataSetSpeed.mode = LineDataSet.Mode.CUBIC_BEZIER
                        dataSetCurrent.mode = LineDataSet.Mode.CUBIC_BEZIER
                        dataSetSpeed.color = chart1!!.getColorEx(android.R.color.white)
                        dataSetCurrent.color = chart1!!.getColorEx(R.color.accent)
                        dataSetSpeed.setDrawCircles(false)
                        dataSetCurrent.setDrawCircles(false)
                        dataSetSpeed.setDrawValues(false)
                        dataSetCurrent.setDrawValues(false)
                        val chart1LineData = LineData()
                        chart1LineData.addDataSet(dataSetCurrent)
                        chart1LineData.addDataSet(dataSetSpeed)
                        chart1!!.data = chart1LineData
                        pagesView[R.layout.main_view_graph]?.findViewById<View>(R.id.leftAxisLabel)?.visibility = View.VISIBLE
                        pagesView[R.layout.main_view_graph]?.findViewById<View>(R.id.leftAxisLabel)?.visibility = View.VISIBLE
                    } else {
                        dataSetSpeed = chart1!!.data.getDataSetByLabel(activity.getString(R.string.speed_axis), true) as LineDataSet
                        dataSetCurrent = chart1!!.data.getDataSetByLabel(activity.getString(R.string.current_axis), true) as LineDataSet
                    }
                    // TODO: fix me
                    // ужасно-тормозной код по перерисовывнию графика.
                    // например каждая очистка вызывает перерисовку
                    dataSetSpeed.clear()
                    dataSetCurrent.clear()

                    val currentAxis = ArrayList(viewModel.currentAxis)
                    val speedAxis = ArrayList(viewModel.speedAxis)
                    for (d in currentAxis) {
                        var value = 0f
                        if (d != null) value = d
                        dataSetCurrent.addEntry(Entry(dataSetCurrent.entryCount.toFloat(), value))
                    }
                    for (d in speedAxis) {
                        var value = 0f
                        if (d != null) value = d
                        if (appConfig.useMph)
                            dataSetSpeed.addEntry(Entry(dataSetSpeed.entryCount.toFloat(), MathsUtil.kmToMiles(value)))
                        else
                            dataSetSpeed.addEntry(Entry(dataSetSpeed.entryCount.toFloat(), value))
                    }
                    dataSetCurrent.notifyDataSetChanged()
                    dataSetSpeed.notifyDataSetChanged()
                    chart1?.apply {
                        this.data.notifyDataChanged()
                        notifyDataSetChanged()
                        invalidate()
                    }
                }
            }
            R.layout.main_view_smart_bms -> {
                bmsRenderer?.refresh()
            }
        }
    }

    private var chartAxisValueFormatter: IndexAxisValueFormatter = object : IndexAxisValueFormatter () {
        override fun getFormattedValue(value: Float): String {
            return if (value < xAxisLabels.size) xAxisLabels[value.toInt()] else ""
        }

        // we don't draw numbers, so no decimal digits needed
        fun getDecimalDigits(): Int {
            return 0
        }
    }

    //region SecondPage
    private val secondPageValues = LinkedHashMap<Int, String>()

    private fun updateFieldForSecondPage(resId: Int, value: String) {
        if (secondPageValues.containsKey(resId)) {
            secondPageValues[resId] = value
        }
    }

    private fun createSecondPage() {
        telemetryItems.value = secondPageValues.toList()
        val layout = pagesView[R.layout.main_view_params_list]?.findViewById<GridLayout>(R.id.page_two_grid) ?: return
        layout.removeAllViews()
        if (secondPageValues.isEmpty()) return
        val font = androidx.core.content.res.ResourcesCompat.getFont(
            activity, if (appConfig.appTheme == R.style.AJDMTheme) R.font.ajdm else R.font.prime
        )
        for ((key, value) in secondPageValues) {
            val headerText = (activity.layoutInflater.inflate(
                R.layout.textview_title_template, layout, false
            ) as TextView).apply {
                text = activity.getString(key)
                typeface = font
            }
            val valueText = (activity.layoutInflater.inflate(
                R.layout.textview_value_template, layout, false
            ) as TextView).apply {
                text = value
                typeface = font
            }
            layout.addView(headerText)
            layout.addView(valueText)
        }
    }

    private fun updateSecondPage() {
        telemetryItems.value = secondPageValues.toList()
        val layout = pagesView[R.layout.main_view_params_list]?.findViewById<GridLayout>(R.id.page_two_grid) ?: return
        val count = layout.childCount
        if (secondPageValues.size * 2 != count) {
            return
        }
        var index = 1
        for (value in secondPageValues.values) {
            val valueText = layout.getChildAt(index) as TextView
            valueText.text = value
            index += 2
        }
    }

    private fun refreshTelemetryValues() {
        if (secondPageValues.isEmpty()) return
        TelemetryPresentation.values(activity, appConfig, viewModel).forEach { (key, value) ->
            updateFieldForSecondPage(key, value)
        }
        updateSecondPage()
    }

    private fun switchTelemetryRenderer() {
        val page = pagesView[R.layout.main_view_params_list] ?: return
        val views = page.findViewById<ScrollView>(R.id.params_views_scroll)
        val compose = page.findViewById<ComposeView>(R.id.paramsComposeView)
        // Keep each renderer's scroll state while changing visibility; never replace the pager page.
        views.visibility = if (appConfig.useComposeTelemetry) View.GONE else View.VISIBLE
        compose.visibility = if (appConfig.useComposeTelemetry) View.VISIBLE else View.GONE
    }

    override fun onViewRecycled(holder: ViewHolder) {
        if (holder.itemViewType == R.layout.main_view_main) {
            if (pagesView[R.layout.main_view_main] === holder.itemView) {
                pagesView[R.layout.main_view_main] = null
                dashboardRenderer = null
                wheelView = null
            }
            holder.dashboardRenderer?.dispose()
            holder.dashboardRenderer = null
        }
        if (holder.itemViewType == R.layout.main_view_smart_bms) {
            if (pagesView[R.layout.main_view_smart_bms] === holder.itemView) {
                pagesView[R.layout.main_view_smart_bms] = null
                bmsRenderer = null
            }
            holder.bmsRenderer?.dispose()
            holder.bmsRenderer = null
        }
        if (holder.itemViewType == R.layout.main_view_trips) {
            if (pagesView[R.layout.main_view_trips] === holder.itemView) {
                pagesView[R.layout.main_view_trips] = null
                tripsRenderer = null
            }
            holder.tripsRenderer?.dispose()
            holder.tripsRenderer = null
        }
        if (holder.itemViewType == R.layout.main_view_events) {
            if (pagesView[R.layout.main_view_events] === holder.itemView) {
                saveEventsScroll()
                pagesView[R.layout.main_view_events] = null
                eventsRenderer = null
            }
            holder.eventsRenderer?.dispose()
            holder.eventsRenderer = null
        }
        if (holder.itemViewType == R.layout.main_view_params_list) {
            telemetryViewsScrollY = holder.itemView.findViewById<ScrollView>(R.id.params_views_scroll).scrollY
            holder.itemView.findViewById<ComposeView>(R.id.paramsComposeView).disposeComposition()
            if (pagesView[R.layout.main_view_params_list] === holder.itemView) {
                pagesView[R.layout.main_view_params_list] = null
            }
        }
        if (holder.itemViewType == R.layout.main_view_graph &&
            pagesView[R.layout.main_view_graph] === holder.itemView) {
            pagesView[R.layout.main_view_graph] = null
            chart1 = null
        }
        super.onViewRecycled(holder)
    }

    private fun saveEventsScroll() {
        pagesView[R.layout.main_view_events]?.findViewById<ScrollView>(R.id.events_views_scroll)?.let {
            eventsViewsScrollY = it.scrollY
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.dashboardRenderer?.start()
        holder.bmsRenderer?.apply {
            preferences(appConfig.useComposeBms, appConfig.appTheme)
            start()
        }
        holder.eventsRenderer?.apply {
            preferences(appConfig.useComposeEvents, appConfig.appTheme)
            start(activity)
        }
        holder.tripsRenderer?.apply {
            preferences(appConfig.useComposeTrips, appConfig.appTheme, appConfig.useMph, appConfig.autoUploadEc)
            start()
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.dashboardRenderer?.stop()
        holder.bmsRenderer?.stop()
        holder.eventsRenderer?.stop()
        holder.tripsRenderer?.stop()
        super.onViewDetachedFromWindow(holder)
    }
    //endregion

    fun configureSecondDisplay() {
        secondPageValues.clear()
        TelemetryPresentation.fields(viewModel.wheelType).forEach { secondPageValues[it] = "" }
        createSecondPage()
    }

    fun configureSmartBmsDisplay() {
        addPage(R.layout.main_view_smart_bms, 2)
        bmsRenderer?.refresh()
    }

    //endregion

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (appConfig.getResId(key)) {
            R.string.show_page_graph -> if (appConfig.pageGraph) {
                addPage(R.layout.main_view_graph, 2)
            } else {
                removePage(R.layout.main_view_graph)
            }
            R.string.show_page_events -> if (appConfig.pageEvents) {
                addPage(R.layout.main_view_events)
            } else {
                removePage(R.layout.main_view_events)
            }
            R.string.show_page_trips -> if (appConfig.pageTrips) {
                addPage(R.layout.main_view_trips)
            } else {
                removePage(R.layout.main_view_trips)
            }
            R.string.view_blocks_string -> updateScreen(true)
        }
    }

    class ViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {
        internal var dashboardRenderer: DashboardPageRenderer? = null
        internal var bmsRenderer: BmsPageRenderer? = null
        internal var eventsRenderer: EventsPageRenderer? = null
        internal var tripsRenderer: TripsPageRenderer? = null
    }
}
