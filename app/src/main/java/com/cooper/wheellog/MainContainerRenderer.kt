package com.cooper.wheellog

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.RecyclerView
import com.cooper.wheellog.compose.MainContainerScreen
import com.cooper.wheellog.databinding.ActivityMainBinding
import com.cooper.wheellog.navigation.MainPages

internal class MainContainerRenderer(
    private val binding: ActivityMainBinding,
    private val adapter: MainPageAdapter,
    restoredPage: Int?
) {
    private val pages = mutableStateOf(MainPages(adapter.pageIds(), adapter.pageIds().first()).let {
        if (restoredPage != null) it.select(restoredPage) else it
    })
    private var rendered = false
    private var disposed = false
    private var pendingRestoredPage = restoredPage?.takeUnless { it in pages.value.ids }
    val selectedId: Int get() = pages.value.selectedId
    val pageToSave: Int get() = pendingRestoredPage ?: selectedId

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = reconcile()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = reconcile()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = reconcile()
    }

    init {
        binding.mainComposeContainer.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        adapter.registerAdapterDataObserver(observer)
    }

    fun render() {
        if (disposed || rendered) return
        rendered = true
        adapter.startObserving()
        (binding.mainHeader.parent as? ViewGroup)?.removeView(binding.mainHeader)
        binding.mainComposeContainer.setContent {
            MainContainerScreen(pages.value, binding.mainHeader, ::select) { id ->
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        FrameLayout(context).apply {
                            val holder = adapter.createViewHolder(this, id)
                            adapter.bindViewHolder(holder, adapter.pageIds().indexOf(id))
                            addView(holder.itemView)
                            val listener = object : View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(view: View) {
                                    adapter.onViewAttachedToWindow(holder)
                                    adapter.updateScreen(true)
                                }
                                override fun onViewDetachedFromWindow(view: View) {
                                    adapter.onViewDetachedFromWindow(holder)
                                }
                            }
                            addOnAttachStateChangeListener(listener)
                            tag = holder to listener
                        }
                    },
                    onRelease = { frame ->
                        @Suppress("UNCHECKED_CAST")
                        val (holder, listener) = frame.tag as Pair<MainPageAdapter.ViewHolder, View.OnAttachStateChangeListener>
                        frame.removeOnAttachStateChangeListener(listener)
                        adapter.onViewDetachedFromWindow(holder)
                        adapter.onViewRecycled(holder)
                    }
                )
            }
        }
        select(selectedId)
    }

    private fun reconcile() {
        if (disposed) return
        pages.value = pages.value.reconcile(adapter.pageIds())
        pendingRestoredPage?.takeIf { it in pages.value.ids }?.let {
            pages.value = pages.value.select(it)
            pendingRestoredPage = null
        }
        select(selectedId)
    }

    private fun select(id: Int) {
        if (disposed || id !in pages.value.ids) return
        if (id != pages.value.selectedId) pendingRestoredPage = null
        pages.value = pages.value.select(id)
        adapter.position = pages.value.selectedIndex
        adapter.updateScreen(true)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        adapter.unregisterAdapterDataObserver(observer)
        binding.mainComposeContainer.disposeComposition()
        adapter.stopObserving()
    }
}
