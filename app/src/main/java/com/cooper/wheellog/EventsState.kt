package com.cooper.wheellog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class EventsState(private val maxLines: Int = 500) {
    private val lines = ArrayDeque<String>()
    private var pending = ""
    private val mutableText = MutableStateFlow("")
    val text: StateFlow<String> = mutableText.asStateFlow()

    init {
        require(maxLines > 0)
    }

    @Synchronized
    fun replace(text: String) {
        lines.clear()
        pending = ""
        append(text)
    }

    @Synchronized
    fun append(text: String) {
        val parts = (pending + text).split('\n')
        parts.dropLast(1).forEach { lines.addLast(it) }
        pending = parts.last()
        while (lines.size + (if (pending.isEmpty()) 0 else 1) > maxLines) {
            lines.removeFirst()
        }
        mutableText.value = buildString {
            lines.forEach { append(it).append('\n') }
            append(pending)
        }
    }
}
