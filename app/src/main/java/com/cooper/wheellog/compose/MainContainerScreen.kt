package com.cooper.wheellog.compose

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cooper.wheellog.R
import com.cooper.wheellog.navigation.MainPages
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

@Composable
internal fun MainContainerScreen(
    pages: MainPages,
    header: View,
    onSelected: (Int) -> Unit,
    pageContent: @Composable (Int) -> Unit
) {
    val pager = rememberPagerState(initialPage = pages.selectedIndex, pageCount = { pages.ids.size })
    val latestSelected by rememberUpdatedState(onSelected)
    LaunchedEffect(pages.ids) {
        // Preserve the selected identity when optional pages are inserted or removed.
        pager.scrollToPage(pages.selectedIndex)
        snapshotFlow { pages.ids.getOrNull(pager.currentPage) }
            .distinctUntilChanged().collect { id -> if (id != null) latestSelected(id) }
    }
    Column(Modifier.fillMaxSize()) {
        // Keep the real ActionBar menu, clock, settings NavHost and PiP View instances.
        AndroidView(factory = { header }, modifier = Modifier.fillMaxWidth())
        HorizontalPager(
            state = pager,
            key = { pages.ids[it] },
            beyondViewportPageCount = pages.ids.lastIndex,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { index -> pageContent(pages.ids[index]) }
        Row(
            Modifier.fillMaxWidth().height(14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            pages.ids.forEachIndexed { index, _ ->
                Box(Modifier.padding(horizontal = 10.dp).width(30.dp).height(2.dp).background(
                    colorResource(if (index == pager.currentPage) R.color.accent else R.color.wheelview_arc_dim)
                ))
            }
        }
    }
}
