package com.cooper.wheellog.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.BuildConfig
import com.cooper.wheellog.LocaleManager
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.ble.getWheelType
import com.cooper.wheellog.utils.Constants
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import com.cooper.wheellog.utils.ThemeIconEnum
import com.cooper.wheellog.utils.ThemeManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Migrated version of StartScreen that uses BleSessionViewModel instead of WheelData.
 * This is an example of how to migrate from the legacy WheelData singleton to the new
 * reactive state-based approach.
 */
@Composable
fun startScreenMigrated(
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit = {},
    viewModel: BleSessionViewModel = koinViewModel()
) {
    // Collect the session state as a state that can be observed
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    
    // Determine if wheel-specific settings should be visible
    // A wheel is considered "known" if we have a valid wheel type (not Unknown)
    val isSpecificVisible by derivedStateOf {
        sessionState.getWheelType() != WHEEL_TYPE.Unknown
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val context: Context = LocalContext.current

        // Listen for wheel model changes via the session state
        // Note: In the new architecture, we observe the StateFlow instead of system broadcasts
        // The wheel model changes will be reflected in sessionState changes
        
        clickablePref(
            name = stringResource(R.string.speed_settings_title),
            themeIcon = ThemeIconEnum.SettingsSpeedometer,
        ) {
            onSelect(SettingsScreenEnum.Application.name)
        }
        clickablePref(
            name = stringResource(R.string.logs_settings_title),
            themeIcon = ThemeIconEnum.SettingsLog,
        ) {
            onSelect(SettingsScreenEnum.Log.name)
        }
        if (isSpecificVisible) {
            clickablePref(
                name = stringResource(R.string.alarm_settings_title),
                themeIcon = ThemeIconEnum.SettingsVibration,
            ) {
                onSelect(SettingsScreenEnum.Alarm.name)
            }
        }
        clickablePref(
            name = stringResource(R.string.watch_settings_title),
            themeIcon = ThemeIconEnum.SettingsWatch,
        ) {
            onSelect(SettingsScreenEnum.Watch.name)
        }
        if (isSpecificVisible) {
            clickablePref(
                name = stringResource(R.string.wheel_settings_title),
                themeIcon = ThemeIconEnum.SettingsWheel,
            ) {
                onSelect(SettingsScreenEnum.Wheel.name)
            }
            clickablePref(
                name = stringResource(R.string.trip_settings_title),
                themeIcon = ThemeIconEnum.SettingsTrips,
            ) {
                onSelect(SettingsScreenEnum.Trip.name)
            }
        }
        clickablePref(
            name = stringResource(R.string.ble_settings_title),
            themeIcon = ThemeIconEnum.SettingsBluetooth,
        ) {
            onSelect(SettingsScreenEnum.Bluetooth.name)
        }
        clickablePref(
            name = stringResource(R.string.notifications_settings_title),
            themeIcon = ThemeIconEnum.SettingsNotifications,
        ) {
            onSelect(SettingsScreenEnum.Notification.name)
        }
        clickablePref(
            name = stringResource(R.string.about_settings_title),
            themeIcon = ThemeIconEnum.SettingsAbout,
        ) {
            onSelect(SettingsScreenEnum.About.name)
        }
    }
}

/**
 * Preview for the migrated StartScreen
 */
@Preview(showBackground = true)
@Composable
fun StartScreenMigratedPreview() {
    MaterialTheme {
        startScreenMigrated()
    }
}