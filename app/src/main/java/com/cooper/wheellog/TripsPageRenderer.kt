package com.cooper.wheellog

import android.os.Parcelable
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.cooper.wheellog.compose.TripsScreen
import com.cooper.wheellog.data.TripItemState
import com.cooper.wheellog.data.TripRepository
import com.cooper.wheellog.views.TripAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import timber.log.Timber

internal class TripsScroll {
    val compose = LazyListState()
    var views: Parcelable? = null
}

internal class TripsPageRenderer(
    page: View,
    private val repository: TripRepository,
    private val appConfig: AppConfig,
    private val scroll: TripsScroll,
    private val owner: LifecycleOwner
) {
    private val context = page.context
    private val views = page.findViewById<RecyclerView>(R.id.list_trips)
    private val compose = page.findViewById<ComposeView>(R.id.tripsComposeView)
    private val items = mutableStateOf<List<TripItemState>>(emptyList())
    private val theme = mutableStateOf(appConfig.appTheme)
    private var useMph = appConfig.useMph
    private var autoUpload = appConfig.autoUploadEc
    private var collecting: Job? = null
    private val deletions = mutableSetOf<Job>()
    private var restoredViewsScroll = false
    private var disposed = false
    private val state = TripsState(
        load = { publish -> repository.loadItems(context, useMph, publish) },
        failure = { Timber.w(it, "Loading trips"); Toast.makeText(context, R.string.trip_load_failed, Toast.LENGTH_LONG).show() }
    )
    private val adapter = TripAdapter(::delete)

    init {
        views.adapter = adapter
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { TripsScreen(items.value, theme.value, scroll.compose, ::delete) }
        preferences(appConfig.useComposeTrips, appConfig.appTheme, useMph, autoUpload)
    }

    fun preferences(useCompose: Boolean, appTheme: Int, mph: Boolean, upload: Boolean) {
        if (disposed) return
        val wasViews = views.visibility == View.VISIBLE
        if (views.visibility == View.VISIBLE) saveScroll()
        val reload = useMph != mph || autoUpload != upload
        useMph = mph
        autoUpload = upload
        theme.value = appTheme
        adapter.updateTrips(items.value, appTheme)
        views.visibility = if (useCompose) View.GONE else View.VISIBLE
        compose.visibility = if (useCompose) View.VISIBLE else View.GONE
        if (!useCompose && !wasViews) views.layoutManager?.onRestoreInstanceState(scroll.views)
        if (reload) refresh()
    }

    fun refresh() = state.refresh()

    fun start() {
        if (disposed) return
        if (collecting?.isActive == true) return
        collecting = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { state.collectRefreshes() }
                launch {
                    state.items.collect {
                        items.value = it
                        adapter.updateTrips(it, theme.value)
                        if (!restoredViewsScroll && it.isNotEmpty()) {
                            views.layoutManager?.onRestoreInstanceState(scroll.views)
                            restoredViewsScroll = true
                        }
                    }
                }
            }
        }
    }

    private fun delete(item: TripItemState) {
        if (disposed) return
        state.pauseLoading()
        val job = owner.lifecycleScope.launch {
            try {
                if (!repository.deleteFile(context, item)) {
                    Toast.makeText(context, R.string.trip_delete_failed, Toast.LENGTH_LONG).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Timber.w(exception, "Deleting trip")
                Toast.makeText(context, R.string.trip_delete_failed, Toast.LENGTH_LONG).show()
            } finally {
                if (!disposed) refresh()
            }
        }
        deletions.add(job)
        job.invokeOnCompletion { deletions.remove(job) }
    }

    fun saveScroll() {
        if (restoredViewsScroll && adapter.itemCount > 0) {
            scroll.views = views.layoutManager?.onSaveInstanceState()
        }
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
        deletions.toList().forEach { it.cancel() }
        compose.disposeComposition()
        views.adapter = null
    }
}
