package com.cooper.wheellog

import com.cooper.wheellog.data.TripItemState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update

internal class TripsState(
    private val load: suspend ((List<TripItemState>) -> Unit) -> List<TripItemState>,
    private val failure: (Exception) -> Unit
) {
    private data class Request(val revision: Long, val load: Boolean)
    private val requests = MutableStateFlow(Request(0L, true))
    private val mutableItems = MutableStateFlow<List<TripItemState>>(emptyList())
    val items: StateFlow<List<TripItemState>> = mutableItems

    fun refresh() { requests.update { Request(it.revision + 1, true) } }

    fun pauseLoading() { requests.update { Request(it.revision + 1, false) } }

    suspend fun collectRefreshes() {
        requests.collectLatest {
            if (!it.load) return@collectLatest
            try {
                val coroutine = kotlinx.coroutines.currentCoroutineContext()
                val loaded = load { snapshot ->
                    coroutine.ensureActive()
                    mutableItems.value = snapshot
                }
                // A non-cooperative provider must not publish results after cancellation.
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                mutableItems.value = loaded
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                failure(exception)
            }
        }
    }
}
