package com.cooper.wheellog.events

import com.cooper.wheellog.EventsState
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

class EventsStateTest {
    @Test
    fun `retention drops whole lines including newline and keeps immutable snapshots`() {
        val state = EventsState(3)
        state.append("first\nsecond\n")
        val previous = state.text.value
        state.append("third\nfourth\nfifth\n")
        assertThat(state.text.value).isEqualTo("third\nfourth\nfifth\n")
        assertThat(previous).isEqualTo("first\nsecond\n")
        state.append("six")
        state.append("th\n")
        assertThat(state.text.value).isEqualTo("fourth\nfifth\nsixth\n")
        state.replace("history\n------------\n")
        assertThat(state.text.value).isEqualTo("history\n------------\n")
    }

    @Test
    fun `default bound counts multiline messages rather than appends`() {
        val state = EventsState()
        state.append((1..1000).joinToString("\n", postfix = "\n"))
        assertThat(state.text.value.lines().dropLast(1))
            .containsExactlyElementsIn((501..1000).map { it.toString() }).inOrder()
    }

    @Test
    fun `rolling default retention trims complete lines after more than five hundred events`() {
        val state = EventsState()
        (1..600).forEach { state.append("event-$it\n") }
        assertThat(state.text.value).isEqualTo(
            (101..600).joinToString("\n", postfix = "\n") { "event-$it" }
        )
        state.append("event-601\nevent-602\n")
        assertThat(state.text.value).isEqualTo(
            (103..602).joinToString("\n", postfix = "\n") { "event-$it" }
        )
    }

    @Test
    fun `concurrent producers lose no lines and remain bounded`() {
        val state = EventsState(1000)
        val executor = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(1)
        try {
            val tasks = (0..7).map { producer ->
                executor.submit {
                    ready.await()
                    repeat(100) { state.append("$producer-$it\n") }
                }
            }
            ready.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertThat(state.text.value.lines().dropLast(1)).containsExactlyElementsIn(
                (0..7).flatMap { producer -> (0..99).map { "$producer-$it" } }
            )
            state.append((1..1000).joinToString("\n", postfix = "\n"))
            assertThat(state.text.value.lines().dropLast(1)).hasSize(1000)
        } finally {
            executor.shutdownNow()
        }
    }
}
