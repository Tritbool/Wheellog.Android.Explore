package com.cooper.wheellog.compose

import android.widget.TextView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.cooper.wheellog.R
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.telemetry.TelemetryPresentation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import org.koin.compose.koinInject

@Composable
fun ParamsListScreen(
    viewModel: BleSessionViewModel = koinInject(),
    appConfig: AppConfig = koinInject()
) {
    val context = LocalContext.current
    val owner = LocalView.current.findViewTreeLifecycleOwner()
    val presentation by produceState(
        initialValue = appConfig.appTheme to emptyList<Pair<Int, String>>(),
        owner, viewModel, appConfig, context
    ) {
        owner?.lifecycle?.repeatOnLifecycle(Lifecycle.State.STARTED) {
            combine(viewModel.sessionState, appConfig.telemetryPreferences()) { _, preferences ->
                val values = TelemetryPresentation.values(context, appConfig, viewModel)
                preferences.appTheme to TelemetryPresentation.fields(viewModel.wheelType).map {
                    it to values.getValue(it)
                }
            }.collect { value = it }
        }
    }
    ParamsListScreen(presentation.second, presentation.first, rememberScrollState())
}

@Composable
fun ParamsListScreen(items: List<Pair<Int, String>>, appTheme: Int, scrollState: ScrollState) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Resolve the same XML appearances using the host's resource theme, not Material's palette.
    val title = remember(context, appTheme) { TextView(context, null, 0, R.style.Stats_Title) }
    val value = remember(context, appTheme) { TextView(context, null, 0, R.style.Stats) }
    val font = remember(appTheme) {
        FontFamily(Font(if (appTheme == R.style.AJDMTheme) R.font.ajdm else R.font.prime))
    }
    val titleStyle = TextStyle(
        color = Color(title.currentTextColor),
        fontSize = with(density) { title.textSize.toSp() },
        fontFamily = font,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Right,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )
    val valueStyle = TextStyle(
        color = Color(value.currentTextColor),
        fontSize = with(density) { value.textSize.toSp() },
        fontFamily = font,
        textAlign = TextAlign.Left,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )
    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
        items.forEach { (resource, text) ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(resource),
                    modifier = Modifier.weight(1f).alignByBaseline().padding(
                        start = with(density) { title.paddingLeft.toDp() },
                        top = with(density) { title.paddingTop.toDp() },
                        end = with(density) { title.paddingRight.toDp() },
                        bottom = with(density) { title.paddingBottom.toDp() }
                    ),
                    style = titleStyle
                )
                Text(text = text, modifier = Modifier.weight(1f).alignByBaseline(), style = valueStyle)
            }
        }
    }
}
