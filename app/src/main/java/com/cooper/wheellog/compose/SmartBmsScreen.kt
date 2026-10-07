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
import androidx.compose.ui.unit.sp
import com.cooper.wheellog.R
import com.cooper.wheellog.bms.BmsPresentation

@Composable
fun SmartBmsScreen(presentation: BmsPresentation, appTheme: Int, scroll: ScrollState) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val title = remember(context, appTheme) { TextView(context, null, 0, R.style.StatsBMS_Title) }
    val value = remember(context, appTheme) { TextView(context, null, 0, R.style.StatsBMS) }
    val font = remember(appTheme) {
        FontFamily(Font(if (appTheme == R.style.AJDMTheme) R.font.ajdm else R.font.prime))
    }
    val titleStyle = TextStyle(
        color = Color(title.currentTextColor), fontSize = with(density) { title.textSize.toSp() },
        fontFamily = font, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )
    val valueStyle = TextStyle(
        color = Color(value.currentTextColor), fontSize = with(density) { value.textSize.toSp() },
        fontFamily = font, textAlign = TextAlign.Left,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )
    val titlePadding = with(density) {
        PaddingValues(title.paddingLeft.toDp(), title.paddingTop.toDp(), title.paddingRight.toDp(), title.paddingBottom.toDp())
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bmsBattery1Title), Modifier.weight(1f).padding(titlePadding),
                style = titleStyle.copy(fontSize = 20.sp, textAlign = TextAlign.Center))
            if (presentation.showSecond) {
                Text(stringResource(R.string.bmsBattery2Title), Modifier.weight(1f).padding(titlePadding),
                    style = titleStyle.copy(fontSize = 20.sp, textAlign = TextAlign.Center))
            }
        }
        presentation.rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(row.label), Modifier.weight(1f).alignByBaseline().padding(titlePadding), style = titleStyle)
                Text(row.first, Modifier.weight(1f).alignByBaseline(), style = valueStyle)
                if (presentation.showSecond) {
                    Text(stringResource(row.label), Modifier.weight(1f).alignByBaseline().padding(titlePadding), style = titleStyle)
                    Text(row.second, Modifier.weight(1f).alignByBaseline(), style = valueStyle)
                }
            }
        }
    }
}
