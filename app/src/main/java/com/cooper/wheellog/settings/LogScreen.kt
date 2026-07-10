package com.cooper.wheellog.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.BuildConfig
import com.cooper.wheellog.MainActivity
import com.cooper.wheellog.R
import com.cooper.wheellog.utils.FileUtil
import com.cooper.wheellog.utils.ThemeIconEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun logScreen(appConfig: AppConfig = koinInject()) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        var autoLogDependency by remember { mutableStateOf(appConfig.autoLog) }
        val writePermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            appConfig.autoLog = granted
            autoLogDependency = granted
        }
        switchPref(
            name = stringResource(R.string.auto_log_title),
            desc = stringResource(R.string.auto_log_description),
            themeIcon = ThemeIconEnum.SettingsAutoLog,
            default = appConfig.autoLog,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appConfig.autoLog = it
                autoLogDependency = it
            } else {
                if (it) {
                    writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    appConfig.autoLog = false
                    autoLogDependency = false
                }
            }
        }

        AnimatedVisibility (autoLogDependency) {
            sliderPref(
                name = stringResource(R.string.auto_log_when_moving_title),
                desc = stringResource(R.string.auto_log_when_moving_description),
                position = appConfig.startAutoLoggingWhenIsMovingMore,
                min = 0f,
                max = 20f,
                unit = R.string.kmh,
                format = "%.1f",
                showSwitch = true,
                disableSwitchAtMin = true,
            ) {
                appConfig.startAutoLoggingWhenIsMovingMore = it
            }
        }

        switchPref(
            name = stringResource(R.string.use_raw_title),
            desc = stringResource(R.string.use_raw_description),
            default = appConfig.enableRawData,
        ) {
            appConfig.enableRawData = it
        }

        switchPref(
            name = stringResource(R.string.continue_this_day_log_title),
            desc = stringResource(R.string.continue_this_day_log_description),
            default = appConfig.continueThisDayLog,
        ) {
            appConfig.continueThisDayLog = it
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || BuildConfig.DEBUG) {
            val activity = (LocalContext.current as? MainActivity)
            clickablePref(
                name = stringResource(R.string.import_log),
            ) {
                activity?.getCsvResults?.launch("text/*")
            }

            clickablePref(
                name = stringResource(R.string.create_test_log),
            ) {
                activity?.let {
                    val fileUtil = FileUtil(activity.applicationContext)
                    val sdFormatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
                    val filename = sdFormatter.format(Date()) + ".csv"
                    if (fileUtil.prepareFile(filename, "test")) {
                        fileUtil.writeLine(
                        "date,time,speed,voltage,phase_current,current,power,torque,pwm,battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert\n" +
                             "2025-01-29,23:05:53.835,14.72,83.28,0.00,0.88,73.29,0.00,44.06,95,1,4778555,24,0,0.13,1.42,Drive,\n" +
                             "2025-01-29,23:05:54.021,19.92,83.28,0.00,1.04,86.61,0.00,59.62,95,1,4778555,24,0,0.14,1.26,Drive,\n" +
                             "2025-01-29,23:05:54.021,19.92,83.28,0.00,1.04,86.61,0.00,59.62,95,1,4778555,24,0,0.14,1.26,Drive,\n" +
                             "2025-01-29,23:05:54.205,22.05,83.28,0.00,1.33,110.76,0.00,65.99,95,1,4778555,24,0,0.09,0.51,Drive,"
                        )
                        for (i in 10..59) {
                            fileUtil.writeLine("2025-01-29,23:$i:00.000,22.05,83.28,0.00,1.33,110.76,0.00,65.99,95,1,4778555,24,0,0.09,0.51,Drive,")
                        }
                        fileUtil.close()
                    }
                    CoroutineScope(Dispatchers.Main + Job()).launch {
                        activity.pagerAdapter.updatePageOfTrips()
                    }
                }
            }
        }
    }
}
