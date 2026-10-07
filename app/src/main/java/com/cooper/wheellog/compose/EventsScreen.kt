package com.cooper.wheellog.compose

import android.widget.TextView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.EventsLoggingTree
import com.cooper.wheellog.R
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import org.koin.compose.koinInject

@Composable
fun EventsScreen(appConfig: AppConfig = koinInject()) {
    val owner = LocalView.current.findViewTreeLifecycleOwner()
    val presentation by produceState(
        initialValue = EventsLoggingTree.events.text.value to appConfig.appTheme,
        owner, appConfig
    ) {
        owner?.lifecycle?.repeatOnLifecycle(Lifecycle.State.STARTED) {
            combine(EventsLoggingTree.events.text, appConfig.telemetryPreferences()) { text, preferences ->
                text to preferences.appTheme
            }.collect { value = it }
        }
    }
    EventsScreen(presentation.first, presentation.second, rememberScrollState())
}

@Composable
fun EventsScreen(text: String, appTheme: Int, scrollState: ScrollState) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val appearance = remember(context, appTheme) { TextView(context) }
    val font = remember(appTheme) {
        FontFamily(Font(if (appTheme == R.style.AJDMTheme) R.font.ajdm else R.font.prime))
    }
    Text(
        text = text,
        style = TextStyle(
            color = Color(appearance.currentTextColor),
            fontSize = with(density) { appearance.textSize.toSp() },
            fontFamily = font,
            textAlign = TextAlign.Start,
            platformStyle = PlatformTextStyle(includeFontPadding = true)
        ),
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
    )
}
