package com.cooper.wheellog.compose

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cooper.wheellog.R
import com.cooper.wheellog.data.TripItemState
import com.cooper.wheellog.utils.ThemeIconEnum
import com.cooper.wheellog.utils.ThemeManager
import com.cooper.wheellog.views.TripActions

@Composable
fun TripsScreen(
    trips: List<TripItemState> = emptyList(),
    appTheme: Int = R.style.OriginalTheme,
    scrollState: LazyListState = rememberLazyListState(),
    delete: (TripItemState) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), state = scrollState) {
        items(trips, key = { it.key }) { TripItem(it, appTheme, delete) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripItem(trip: TripItemState, appTheme: Int, delete: (TripItemState) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val appearance = remember(context, appTheme) { TextView(context) }
    val font = remember(appTheme) {
        FontFamily(Font(if (appTheme == R.style.AJDMTheme) R.font.ajdm else R.font.prime))
    }
    val style = TextStyle(
        fontFamily = font, color = Color(appearance.currentTextColor),
        fontSize = with(density) { appearance.textSize.toSp() },
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )
    var anchor by remember { mutableStateOf<View?>(null) }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.primary)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
            onClick = {}, onLongClick = { anchor?.let { TripActions.menu(it, trip, delete, appTheme) } }
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    trip.title, style = style.copy(fontSize = 20.sp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp)
                )
                Row(Modifier.padding(start = 16.dp, top = 6.dp)) {
                    Text(trip.description, style = style)
                    Text(trip.description2, style = style, modifier = Modifier.padding(start = 24.dp))
                }
            }
            AndroidView(
                factory = { ctx ->
                    ImageButton(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        val padding = (10 * ctx.resources.displayMetrics.density).toInt()
                        setPadding(padding, padding, padding, padding)
                        contentDescription = ctx.getString(R.string.share)
                        anchor = this
                    }
                },
                update = { button ->
                    button.setImageResource(ThemeManager.getId(ThemeIconEnum.TripsPopupButton, appTheme))
                    button.setOnClickListener { TripActions.menu(button, trip, delete, appTheme) }
                }
            )
        }
    }
}
