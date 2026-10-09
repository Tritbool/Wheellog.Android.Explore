package com.cooper.wheellog.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.BuildConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.ble.getWheelType
import com.cooper.wheellog.utils.Constants
import com.cooper.wheellog.utils.ThemeIconEnum
import org.koin.compose.koinInject

@Composable
fun startScreen(
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit = {},
) {
    val viewModel: BleSessionViewModel = koinInject()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val isSpecificVisible = sessionState.getWheelType() != Constants.WHEEL_TYPE.Unknown
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val context: Context = LocalContext.current

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
        var showAboutDialog by remember { mutableStateOf(false) }

        if (showAboutDialog) {
            AlertDialog(
                shape = RoundedCornerShape(8.dp),
                onDismissRequest = { showAboutDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.about_app_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}\n" +
                                    "Build at ${BuildConfig.BUILD_TIME}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        clickablePref(
                            name = stringResource(R.string.github),
                            desc = stringResource(R.string.github_desc),
                        ) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/Wheellog/Wheellog.Android".toUri(),
                                ),
                            )
                        }
                        clickablePref(
                            name = stringResource(R.string.FAQ),
                        ) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/Wheellog/Wheellog.Android/wiki".toUri()
                                ),
                            )
                        }
                        clickablePref(
                            name = stringResource(R.string.bug_report),
                        ) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/Wheellog/Wheellog.Android/issues".toUri()
                                ),
                            )
                        }
                        Text(
                            text = stringResource(R.string.about_app_desc),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showAboutDialog = false }) {
                        Text(stringResource(id = android.R.string.ok))
                    }
                },
            )
        }
        clickablePref(
            name = stringResource(R.string.about_app_title),
            themeIcon = ThemeIconEnum.SettingsAbout,
            showArrowIcon = false,
            showDiv = false,
        ) {
            showAboutDialog = true
        }
    }
}

@Preview
@Composable
fun startScreenPreview(appConfig: AppConfig = koinInject()) {
    startScreen()
}