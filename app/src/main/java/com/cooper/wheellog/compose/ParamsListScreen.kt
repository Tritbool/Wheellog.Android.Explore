package com.cooper.wheellog.compose

import android.widget.TextView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.cooper.wheellog.R

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
