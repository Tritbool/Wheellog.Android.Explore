package com.cooper.wheellog.compose

import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.cooper.wheellog.R
import com.cooper.wheellog.scan.ScanUiState
import com.google.android.material.textfield.TextInputLayout

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScanScreen(
    state: ScanUiState,
    scroll: LazyListState,
    onSelect: (String) -> Unit,
    onForceProtocol: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onManualSelect: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val title = remember(context) {
        TextView(context).apply {
            val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.textAppearanceLarge))
            try { setTextAppearance(context, attributes.getResourceId(0, android.R.style.TextAppearance_Large)) }
            finally { attributes.recycle() }
        }
    }
    val titleStyle = TextStyle(
        color = Color(title.currentTextColor),
        fontSize = with(density) { title.textSize.toSp() },
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )
    val gap = dimensionResource(R.dimen.default_gap)
    val rowColor = colorResource(R.color.primary_dark)
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (state.scanning) R.string.scanning else R.string.devices),
                Modifier.weight(1f).padding(gap), style = titleStyle, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (state.scanning) AndroidView(factory = { ProgressBar(it) })
        }
        ScanDivider()
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxWidth().padding(gap).height(dimensionResource(R.dimen.scan_device_list_height))
        ) {
            items(state.devices, key = { it.address }) { device ->
                Column(Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onSelect(device.address) },
                    onLongClick = { onForceProtocol(device.address) }
                )) {
                    Text(device.name, style = TextStyle(color = rowColor, fontSize = 24.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true)))
                    Text(device.address, style = TextStyle(color = rowColor, fontSize = 12.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true)))
                }
                ScanDivider()
            }
        }
        ScanDivider()
        // Keep the existing Material input/IME and end-icon behavior during coexistence.
        if (!state.scanning) AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.scan_manual_address, null) as TextInputLayout).apply {
                    editText!!.setText(state.manualAddress)
                    editText!!.doAfterTextChanged { onAddressChanged(it.toString()) }
                    setEndIconOnClickListener { onManualSelect() }
                }
            },
            update = { input ->
                if (input.editText!!.text.toString() != state.manualAddress) input.editText!!.setText(state.manualAddress)
                input.error = if (state.invalidAddress) "incorrect MAC" else null
                input.errorIconDrawable = null
            }
        )
    }
}

@Composable
private fun ScanDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(colorResource(android.R.color.darker_gray)))
}
