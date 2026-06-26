package com.cooper.wheellog.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.SessionManager
import com.cooper.wheellog.utils.Constants
import org.koin.compose.koinInject

@Composable
fun tripScreen( ) {
    val sessionManager: SessionManager = koinInject()
    
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        clickablePref(
            name = stringResource(R.string.reset_max_values_title),
            desc = stringResource(R.string.reset_max_values_description),
            showArrowIcon = false,
        ) {
            sessionManager.resetMaxValues()
        }
        val context = LocalContext.current
        clickablePref(
            name = stringResource(R.string.reset_lowest_battery_title),
            showArrowIcon = false,
        ) {
            sessionManager.resetBatteryLowest()
            context.sendBroadcast(Intent(Constants.ACTION_PREFERENCE_RESET))
        }
        clickablePref(
            name = stringResource(R.string.reset_user_distance_title),
            showArrowIcon = false,
        ) {
            sessionManager.resetUserDistance()
        }
    }
}
