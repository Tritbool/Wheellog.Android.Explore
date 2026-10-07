package com.cooper.wheellog.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DashboardGauge(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    actions: DashboardActions? = null
) {
    val context = LocalContext.current
    val renderer = remember(context) { DashboardCanvasRenderer(context) }
    val latest by rememberUpdatedState(state)
    var geometry by remember { mutableStateOf<DashboardGeometry?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    val gestures = remember(context, actions) {
        DashboardGestures(context, { latest }, { geometry },
            { actions?.singleTap() },
            { actions?.doubleTap() }, { block, data -> actions?.replaceBlock(block, data) })
    }
    DisposableEffect(gestures) { onDispose { gestures.cancel() } }
    LaunchedEffect(state) {
        do {
            frame++
            delay(30)
        } while (renderer.animating)
    }
    Canvas(modifier.fillMaxSize()
        .onSizeChanged { geometry = renderer.geometry(it.width.toFloat(), it.height.toFloat(), latest) }
        .pointerInteropFilter { gestures.touch(it) }) {
        frame
        geometry = renderer.geometry(size.width, size.height, state)
        drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, size.height, state) }
    }
}
