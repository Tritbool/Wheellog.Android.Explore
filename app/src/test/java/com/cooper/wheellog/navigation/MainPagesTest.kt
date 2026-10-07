package com.cooper.wheellog.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainPagesTest {
    @Test fun `inserting BMS before graph preserves the visible identity`() {
        val pages = MainPages(listOf(1, 2, 3, 4, 5), 3)
        val updated = pages.reconcile(listOf(1, 2, 6, 3, 4, 5))
        assertThat(updated.selectedId).isEqualTo(3)
        assertThat(updated.selectedIndex).isEqualTo(3)
    }

    @Test fun `removing an earlier optional page preserves selected events`() {
        val updated = MainPages(listOf(1, 2, 3, 4, 5), 5).reconcile(listOf(1, 2, 4, 5))
        assertThat(updated.selectedId).isEqualTo(5)
        assertThat(updated.selectedIndex).isEqualTo(3)
    }

    @Test fun `removing selected page chooses same slot or last remaining page`() {
        assertThat(MainPages(listOf(1, 2, 3, 4), 3).reconcile(listOf(1, 2, 4)).selectedId).isEqualTo(4)
        assertThat(MainPages(listOf(1, 2, 3), 3).reconcile(listOf(1, 2)).selectedId).isEqualTo(2)
    }

    @Test fun `stale callback cannot select a removed page`() {
        val pages = MainPages(listOf(1, 2), 2)
        assertThat(pages.select(3)).isEqualTo(pages)
        assertThat(pages.select(1).selectedIndex).isEqualTo(0)
    }

    @Test fun `page lists are detached snapshots and restoration uses identity`() {
        val incoming = mutableListOf(1, 2, 3)
        val updated = MainPages(listOf(1, 2), 1).reconcile(incoming).select(3)
        incoming.clear()
        assertThat(updated.ids).containsExactly(1, 2, 3).inOrder()
        assertThat(updated.selectedIndex).isEqualTo(2)
    }
}
