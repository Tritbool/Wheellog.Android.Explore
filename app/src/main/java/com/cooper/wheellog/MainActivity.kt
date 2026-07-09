package com.cooper.wheellog

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.DialogInterface.OnShowListener
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.AnimationDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Rational
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.DialogHelper.checkAndShowPrivatePolicyDialog
import com.cooper.wheellog.DialogHelper.checkBatteryOptimizationsAndShowAlert
import com.cooper.wheellog.DialogHelper.checkPWMIsSetAndShowAlert
import com.cooper.wheellog.compose.MainScreen
import com.cooper.wheellog.data.TripDatabase.Companion.getDataBase
import com.cooper.wheellog.data.TripParser
import com.cooper.wheellog.databinding.ActivityMainBinding
import com.cooper.wheellog.settings.mainScreen
import com.cooper.wheellog.ui.theme.AppTheme
import com.cooper.wheellog.utils.*
import com.cooper.wheellog.utils.Alarms.checkAlarm
import com.cooper.wheellog.utils.Constants.ALARM_TYPE
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import com.cooper.wheellog.utils.PermissionsUtil.checkBlePermissions
import com.cooper.wheellog.utils.PermissionsUtil.checkExternalFilePermission
import com.cooper.wheellog.utils.PermissionsUtil.checkNotificationsPermissions
import com.cooper.wheellog.utils.PermissionsUtil.isMaxBleReq
import com.cooper.wheellog.utils.SomeUtil.getSerializable
import com.cooper.wheellog.utils.SomeUtil.playBeep
import com.cooper.wheellog.views.PiPView
import com.google.android.material.snackbar.Snackbar
import io.github.tritbool.euc.ble.core.BLEConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val appConfig: AppConfig by inject()
    private val notifications: NotificationUtil by inject()
    private val volumeKeyController: VolumeKeyController by inject()
    private var eventsLoggingTree: EventsLoggingTree? = null
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.setLocale(base))
    }

    //region private variables
    private lateinit var binding: ActivityMainBinding
    lateinit var pager: ViewPager2
    lateinit var pagerAdapter: MainPageAdapter
    lateinit var pipView: ComposeView
    var mMenu: Menu? = null
    private var miSearch: MenuItem? = null
    private var miWheel: MenuItem? = null
    private var miLogging: MenuItem? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var doubleBackToExitPressedOnce = false
    private var snackbar: Snackbar? = null
    private val timeFormatter = SimpleDateFormat("HH:mm:ss ", Locale.US)
    private val speedModel: PiPView.SpeedModel by lazy { PiPView.SpeedModel() }
    private val viewModel: BleSessionViewModel by viewModel()
    private var settingsNavHostController: NavHostController? = null
