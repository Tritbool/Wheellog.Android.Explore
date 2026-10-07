package com.cooper.wheellog.trips

import com.cooper.wheellog.TripsState
import com.cooper.wheellog.data.TripItemState
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripsStateTest {
    @Test fun `new refresh cancels stale parsing and keeps latest list`() = runTest {
        var calls = 0
        var cancelled = false
        val fresh = listOf(mockk<TripItemState>())
        val state = TripsState(load = {
            if (++calls == 1) {
                try { delay(1000) } catch (exception: CancellationException) {
                    cancelled = true
                    throw exception
                }
            }
            fresh
        }, failure = { throw AssertionError(it) })
        val collection = backgroundScope.launch { state.collectRefreshes() }
        runCurrent()
        state.refresh()
        runCurrent()
        assertThat(cancelled).isTrue()
        assertThat(state.items.value).isEqualTo(fresh)
        advanceTimeBy(2000)
        runCurrent()
        assertThat(calls).isEqualTo(2)
        collection.cancel()
        state.refresh()
        runCurrent()
        assertThat(calls).isEqualTo(2)
    }

    @Test fun `stopped refreshes are remembered on restart and failed load retains previous data`() = runTest {
        val previous = listOf(mockk<TripItemState>())
        var fail = false
        var failures = 0
        var calls = 0
        val state = TripsState(load = {
            calls++
            if (fail) error("provider failed")
            previous
        }, failure = { failures++ })
        val first = backgroundScope.launch { state.collectRefreshes() }
        runCurrent()
        assertThat(state.items.value).isEqualTo(previous)
        first.cancel()
        runCurrent()
        state.refresh()
        state.refresh()
        runCurrent()
        assertThat(calls).isEqualTo(1)
        fail = true
        backgroundScope.launch { state.collectRefreshes() }
        runCurrent()
        assertThat(calls).isEqualTo(2)
        assertThat(failures).isEqualTo(1)
        assertThat(state.items.value).isEqualTo(previous)
    }

    @Test fun `initial filenames publish before parsing completes and pausing cancels stale progress`() = runTest {
        val preview = listOf(mockk<TripItemState>())
        val complete = listOf(mockk<TripItemState>())
        var progress: ((List<TripItemState>) -> Unit)? = null
        var attempts = 0
        var cancelled = false
        val state = TripsState(load = { publish ->
            attempts++
            progress = publish
            publish(preview)
            try {
                delay(1000)
            } catch (exception: CancellationException) {
                cancelled = true
                throw exception
            }
            complete
        }, failure = { throw AssertionError(it) })
        backgroundScope.launch { state.collectRefreshes() }
        runCurrent()
        assertThat(state.items.value).isEqualTo(preview)
        val staleProgress = progress!!
        state.pauseLoading()
        runCurrent()
        assertThat(cancelled).isTrue()
        var rejected = false
        try { staleProgress(complete) } catch (_: CancellationException) { rejected = true }
        assertThat(rejected).isTrue()
        assertThat(state.items.value).isEqualTo(preview)
        advanceTimeBy(2000)
        runCurrent()
        assertThat(attempts).isEqualTo(1)
        state.refresh()
        runCurrent()
        assertThat(attempts).isEqualTo(2)
        advanceTimeBy(1000)
        runCurrent()
        assertThat(state.items.value).isEqualTo(complete)
    }
}
