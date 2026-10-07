package com.cooper.wheellog.navigation

data class MainPages(val ids: List<Int>, val selectedId: Int) {
    val selectedIndex: Int get() = ids.indexOf(selectedId).coerceAtLeast(0)

    fun select(id: Int): MainPages = if (id in ids) copy(selectedId = id) else this

    fun reconcile(updated: List<Int>): MainPages {
        require(updated.isNotEmpty())
        require(updated.distinct().size == updated.size)
        val selected = if (selectedId in updated) selectedId
            else updated[selectedIndex.coerceAtMost(updated.lastIndex)]
        return MainPages(updated.toList(), selected)
    }
}
