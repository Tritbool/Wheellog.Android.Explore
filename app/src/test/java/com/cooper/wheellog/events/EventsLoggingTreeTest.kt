package com.cooper.wheellog.events

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.EventsLoggingTree
import com.cooper.wheellog.EventsState
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class EventsLoggingTreeTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun clearFile() {
        context.deleteFile("eventsLog")
    }

    private fun history(count: Int): List<String> {
        val lines = (1..count).map { "old-$it" }
        context.openFileOutput("eventsLog", Context.MODE_PRIVATE).use {
            it.write(lines.joinToString("\n", postfix = "\n").toByteArray())
        }
        return lines
    }

    private fun fileLines() = context.openFileInput("eventsLog").bufferedReader().use { it.readLines() }

    @Test
    fun `history displays last hundred plus divider and truncates file at two hundred`() {
        val old = history(200)
        val state = EventsState()
        val tree = EventsLoggingTree(context, state)
        try {
            assertThat(state.text.value).isEqualTo(
                old.takeLast(100).joinToString("\n", postfix = "\n") + "------------\n"
            )
            assertThat(fileLines()).containsExactlyElementsIn(old.takeLast(100)).inOrder()
        } finally {
            tree.close()
        }
    }

    @Test
    fun `shorter history stays on disk and reopening does not duplicate cached history`() {
        val old = history(199)
        val state = EventsState()
        EventsLoggingTree(context, state).close()
        val snapshot = state.text.value
        EventsLoggingTree(context, state).close()
        assertThat(state.text.value).isEqualTo(snapshot)
        assertThat(fileLines()).containsExactlyElementsIn(old).inOrder()
    }

    @Test
    fun `info filter timestamp and concurrent file and snapshot ordering agree`() {
        val state = EventsState()
        val tree = EventsLoggingTree(context, state)
        val executor = Executors.newFixedThreadPool(4)
        Timber.plant(tree)
        try {
            Timber.d("not an event")
            assertThat(state.text.value).isEmpty()
            assertThat(tree.isLoggable(null, Log.DEBUG)).isFalse()
            assertThat(tree.isLoggable(null, Log.INFO)).isTrue()
            val tasks = (1..100).map { index -> executor.submit { Timber.i("event-$index") } }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            val lines = fileLines()
            assertThat(lines).hasSize(100)
            lines.forEach { assertThat(it).matches("\\d{2}:\\d{2}:\\d{2} - event-\\d+") }
            assertThat(state.text.value).isEqualTo(lines.joinToString("\n", postfix = "\n"))
            tree.close()
            tree.close()
            Timber.i("after close")
            assertThat(fileLines()).containsExactlyElementsIn(lines).inOrder()
        } finally {
            executor.shutdownNow()
            Timber.uproot(tree)
            tree.close()
        }
    }
}
