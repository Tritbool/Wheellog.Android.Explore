package com.cooper.wheellog

import android.Manifest
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
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.cooper.wheellog.ble.BleSessionState
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.DialogHelper.checkAndShowPrivatePolicyDialog
import com.cooper.wheellog.DialogHelper.checkBatteryOptimizationsAndShowAlert
import com.cooper.wheellog.compose.MainScreen
import com.cooper.wheellog.databinding.ActivityMainBinding
import com.cooper.wheellog.settings.mainScreen
import com.cooper.wheellog.ui.theme.AppTheme
import com.cooper.wheellog.utils.*
import com.cooper.wheellog.utils.Alarms.checkAlarm
import com.cooper.wheellog.utils.PermissionsUtil.checkBlePermissions
import com.cooper.wheellog.utils.PermissionsUtil.checkExternalFilePermission
import com.cooper.wheellog.utils.PermissionsUtil.checkNotificationsPermissions
import com.cooper.wheellog.utils.PermissionsUtil.isMaxBleReq
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
    private val speedModel: PiPView.SpeedModel by lazy { PiPView.SpeedModel() }
    private val viewModel: BleSessionViewModel by viewModel()
    private var settingsNavHostController: NavHostController? = null

    // Current connection state, derived from sessionState observation
    private var mConnectionState: BLEConstants.ConnectionState =
        BLEConstants.ConnectionState.DISCONNECTED

    // Logging service
    private var loggingService: LoggingService? = null
    private val mLoggingServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            loggingService = (service as LoggingService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            loggingService = null
            Timber.e("LoggingService disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            loggingService = null
        }
    }
    //endregion

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
            togglePipView(show = isInPictureInPictureMode)
        }
    }

    private fun togglePipView(show: Boolean) {
        if (show) {
            try {
                pipView.setContent {
                    PiPView().SpeedWidget(modifier = Modifier.fillMaxSize(), model = speedModel)
                }
                pipView.visibility = View.VISIBLE
            } catch (_: Exception) {
                // ignore
            }
        } else {
            pipView.visibility = View.GONE
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (appConfig.usePipMode
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !this.isInPictureInPictureMode
        ) {
            when (appConfig.pipBlock) {
                getString(R.string.consumption) -> speedModel.title =
                    getString(R.string.consumption)

                else -> speedModel.title = getString(R.string.speed)
            }
            try {
                this.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            } catch (e: RuntimeException) {
                Toast.makeText(this, R.string.pip_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setConnectionState(connectionState: BLEConstants.ConnectionState) {
        when (connectionState) {
            BLEConstants.ConnectionState.CONNECTED -> {
                pagerAdapter.configureSecondDisplay()
                val mac = viewModel.getMac
                if (mac.isNotEmpty()) {
                    appConfig.lastMac = mac
                }
                if (appConfig.useBeepOnVolumeUp) {
                    volumeKeyController.setActive(true)
                }
                hideSnackBar()
            }

            BLEConstants.ConnectionState.DISCONNECTED -> {
                if (mConnectionState == BLEConstants.ConnectionState.CONNECTED) {
                    showSnackBar(getString(R.string.connection_lost_at), Snackbar.LENGTH_INDEFINITE)
                }
                if (appConfig.useBeepOnVolumeUp) {
                    volumeKeyController.setActive(false)
                }
            }

            else -> {}
        }
        mConnectionState = connectionState
        setMenuIconStates()
    }

    /**
     * Observes BleSessionState and updates the UI accordingly.
     * Replaces the legacy broadcast receivers for WHEEL_DATA_AVAILABLE and BLUETOOTH_CONNECTION_STATE.
     */
    private fun handleSessionStateChange(state: BleSessionState) {
        if (isPaused) return

        // Connection state changes
        if (state.connectionState != mConnectionState) {
            setConnectionState(state.connectionState)

            // Notify logging service of connection state change
            val blessedState = if (state.isConnected)
                BLEConstants.ConnectionState.CONNECTED
            else
                BLEConstants.ConnectionState.DISCONNECTED
            loggingService?.updateConnectionState(blessedState)

            when (state.connectionState) {
                BLEConstants.ConnectionState.CONNECTED -> {
                    if (!LoggingService.isInstanceCreated() &&
                        appConfig.autoLog &&
                        appConfig.startAutoLoggingWhenIsMovingMore == 0f
                    ) {
                        toggleLoggingService()
                    }
                    notifications.notificationMessageId = R.string.connected
                }

                BLEConstants.ConnectionState.DISCONNECTED -> {
                    notifications.notificationMessageId = R.string.disconnected
                }

                else -> {}
            }
            notifications.update()
        }

        // Data updates
        if (state.lastDataTimestamp != null) {
            // Update logging
            loggingService?.updateFile()

            notifications.update()

            // Auto-start logging when moving
            if (!LoggingService.isInstanceCreated() &&
                appConfig.startAutoLoggingWhenIsMovingMore != 0f &&
                appConfig.autoLog &&
                viewModel.speedDouble > appConfig.startAutoLoggingWhenIsMovingMore
            ) {
                toggleLoggingService()
            }

            // Check alarms
            if (appConfig.alarmsEnabled) {
                checkAlarm(viewModel.calculatedPwm / 100, applicationContext)
            }

            // Update PiP if active
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                when (appConfig.pipBlock) {
                    getString(R.string.consumption) -> speedModel.value.floatValue =
                        Calculator.whByKm.toFloat()

                    else -> speedModel.value.floatValue = (viewModel.speedDouble).toFloat()
                }
                pipView.invalidate()
            }

            // Update the pager
            pagerAdapter.updateScreen(false)
        }
    }

    private fun setMenuIconStates() {
        if (mMenu == null) return
        val lastMac = appConfig.lastMac
        if (lastMac.isEmpty()) {
            miWheel!!.isEnabled = false
            miWheel!!.icon!!.alpha = 64
        } else {
            miWheel!!.isEnabled = true
            miWheel!!.icon!!.alpha = 255
        }
        mMenu?.findItem(R.id.miReset)?.isVisible = appConfig.mainMenuButtons.contains("reset")

        if (LoggingService.isInstanceCreated()) {
            miLogging!!.setTitle(R.string.stop_data_service)
            miLogging!!.setIcon(ThemeManager.getId(ThemeIconEnum.MenuLogOn))
        } else {
            miLogging!!.setTitle(R.string.start_data_service)
            miLogging!!.setIcon(ThemeManager.getId(ThemeIconEnum.MenuLogOff))
        }
        when (mConnectionState) {
            BLEConstants.ConnectionState.CONNECTED -> {
                miWheel!!.setIcon(ThemeManager.getId(ThemeIconEnum.MenuWheelOn))
                miWheel!!.setTitle(R.string.disconnect_from_wheel)
                miSearch!!.isEnabled = false
                miSearch!!.icon!!.alpha = 64
                miLogging!!.isEnabled = true
                miLogging!!.icon!!.alpha = 255
            }

            BLEConstants.ConnectionState.DISCONNECTED -> {
                miWheel!!.setIcon(ThemeManager.getId(ThemeIconEnum.MenuWheelOff))
                miWheel!!.setTitle(R.string.connect_to_wheel)
                miSearch!!.isEnabled = true
                miSearch!!.icon!!.alpha = 255
                if (LoggingService.isInstanceCreated()) {
                    miLogging!!.isEnabled = true
                    miLogging!!.icon!!.alpha = 255
                } else {
                    miLogging!!.isEnabled = false
                    miLogging!!.icon!!.alpha = 64
                }
            }

            else -> {
                // Connecting / scanning state - show searching animation
                miWheel!!.setIcon(ThemeManager.getId(ThemeIconEnum.MenuWheelSearch))
                miWheel!!.setTitle(R.string.disconnect_from_wheel)
                (miWheel!!.icon as? AnimationDrawable)?.start()
                miSearch!!.isEnabled = false
                miSearch!!.icon!!.alpha = 64
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pagerAdapter.updateScreen(true)
    }

    private fun createPager() {
        pager = binding.pager
        pager.offscreenPageLimit = 10
        val pages = ArrayList<Int>()
        pages.add(R.layout.main_view_main)
        pages.add(R.layout.main_view_params_list)
        if (appConfig.pageGraph) {
            pages.add(R.layout.main_view_graph)
        }
        if (appConfig.pageTrips) {
            pages.add(R.layout.main_view_trips)
        }
        if (appConfig.pageEvents) {
            pages.add(R.layout.main_view_events)
        }
        pagerAdapter = MainPageAdapter(pages, this)
        pager.adapter = pagerAdapter
        pager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                pagerAdapter.position = position
                pagerAdapter.updateScreen(true)
            }
        })
        eventsLoggingTree = EventsLoggingTree(applicationContext, pagerAdapter)
        Timber.plant(eventsLoggingTree!!)
        val indicator = binding.indicator
        indicator.setViewPager(pager)
        pagerAdapter.registerAdapterDataObserver(indicator.adapterDataObserver)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.settingsView.visibility == View.VISIBLE) {
                    if (settingsNavHostController?.previousBackStackEntry == null) {
                        toggleSettings()
                    } else {
                        settingsNavHostController?.navigateUp()
                    }
                } else if (doubleBackToExitPressedOnce) {
                    finish()
                } else {
                    doubleBackToExitPressedOnce = true
                    showSnackBar(R.string.back_to_exit)
                    Handler(Looper.getMainLooper()).postDelayed(
                        { doubleBackToExitPressedOnce = false },
                        2000
                    )
                }
            }
        })

        if (onDestroyProcess) {
            Process.killProcess(Process.myPid())
            return
        }

        AppCompatDelegate.setDefaultNightMode(appConfig.dayNightThemeMode)
        setTheme(appConfig.appTheme)
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC

        binding = ActivityMainBinding.inflate(layoutInflater)

        if (appConfig.useComposeUI) {
            setContent {
                AppTheme {
                    MainScreen()
                }
            }
        } else {
            setContentView(binding.root)
        }

        createPager()
        pipView = binding.pipView

        binding.textClock.typeface = ThemeManager.getTypeface(applicationContext)
        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        checkAndShowPrivatePolicyDialog(this)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
        }

        val bluetoothManager = this.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
        } else if (!mBluetoothAdapter!!.isEnabled) {
            if (checkBlePermissions(this, RESULT_REQUEST_PERMISSIONS_BT)) {
                enableBleLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            connectToLastDevice()
        }

        // Observe notification button broadcasts from the system notification
        ContextCompat.registerReceiver(
            this,
            mNotificationButtonReceiver,
            makeNotificationIntentFilter(),
            ContextCompat.RECEIVER_EXPORTED
        )

        notifications.update()

        binding.settingsView.apply {
            setContent {
                AppTheme(useDarkTheme = true) {
                    settingsNavHostController = rememberNavController()
                    mainScreen(navController = settingsNavHostController!!)
                }
            }
        }

        checkBatteryOptimizationsAndShowAlert(this)

        // Observe sessionState for the lifecycle of this activity
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessionState.collect { state ->
                    handleSessionStateChange(state)
                }
            }
        }
    }

    private fun checkClockVisible() {
        if (appConfig.showClock) {
            binding.textClock.visibility = View.VISIBLE
        } else {
            binding.textClock.visibility = View.GONE
        }
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    public override fun onResume() {
        super.onResume()
        isPaused = false
        if (checkNotificationsPermissions(this)) {
            notifications.update()
        }
        pagerAdapter.updateScreen(true)
        pagerAdapter.updatePageOfTrips()
        checkClockVisible()
        setMenuIconStates()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setMenuIconStates()
    }

    public override fun onPause() {
        super.onPause()
        isPaused = true
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            togglePipView(show = true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            togglePipView(show = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("connectionState", mConnectionState.ordinal)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val ordinal = savedInstanceState.getInt(
            "connectionState",
            BLEConstants.ConnectionState.DISCONNECTED.ordinal
        )
        mConnectionState =
            BLEConstants.ConnectionState.entries.getOrElse(ordinal) { BLEConstants.ConnectionState.DISCONNECTED }
        setMenuIconStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!this.isFinishing) {
            Timber.wtf("Recreate main activity")
            return
        }
        Timber.wtf("-=[ finish ]=-")
        onDestroyProcess = true
        stopLoggingService()
        viewModel.fullReset()
        if (loggingService != null) {
            try {
                unbindService(mLoggingServiceConnection)
            } catch (_: Exception) {
                // ignored
            }
        }
        try {
            unregisterReceiver(mNotificationButtonReceiver)
        } catch (_: Exception) {
            // ignore
        }
        ThemeManager.changeAppIcon(this@MainActivity)
        object : CountDownTimer((2 * 60 * 1000L), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!LoggingService.isInstanceCreated()) {
                    onFinish()
                }
            }

            override fun onFinish() {
                notifications.close()
                Timber.uproot(eventsLoggingTree!!)
                eventsLoggingTree!!.close()
                eventsLoggingTree = null
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val runningProcesses = am.runningAppProcesses
                for (process in runningProcesses) {
                    if (Process.myPid() != process.pid) {
                        Process.killProcess(process.pid)
                    }
                }
                Process.killProcess(Process.myPid())
            }
        }.start()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Timber.wtf("[Warning] Low memory")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        mMenu = menu.apply {
            miSearch = findItem(R.id.miSearch)
            miWheel = findItem(R.id.miWheel)
            miLogging = findItem(R.id.miLogging)
        }

        if (appConfig.appTheme == R.style.AJDMTheme) {
            val miSettings = mMenu!!.findItem(R.id.miSettings)
            miSettings.setIcon(ThemeManager.getId(ThemeIconEnum.MenuSettings))
            miSearch!!.setIcon(ThemeManager.getId(ThemeIconEnum.MenuBluetooth))
        }
        return true
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Alarms.vibrate(this, longArrayOf(0, 30, 40))
        return when (item.itemId) {
            R.id.miSearch -> {
                startScanActivity()
                true
            }

            R.id.miWheel -> {
                toggleConnectToWheel()
                true
            }

            R.id.miLogging -> {
                if (LoggingService.isInstanceCreated() && appConfig.continueThisDayLog) {
                    val dialog = AlertDialog.Builder(this)
                        .setTitle(R.string.continue_this_day_log_alert_title)
                        .setMessage(R.string.continue_this_day_log_alert_description)
                        .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                            appConfig.continueThisDayLogMacException = appConfig.lastMac
                            toggleLogging()
                        }
                        .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> toggleLogging() }
                        .create()
                    dialog.setOnShowListener(object : OnShowListener {
                        val AUTO_DISMISS_MILLIS = 5000
                        override fun onShow(dialog: DialogInterface) {
                            val defaultButton =
                                (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                            val negativeButtonText = defaultButton.text
                            object : CountDownTimer(AUTO_DISMISS_MILLIS.toLong(), 100) {
                                override fun onTick(millisUntilFinished: Long) {
                                    defaultButton.text = String.format(
                                        Locale.getDefault(), "%s (%d)",
                                        negativeButtonText,
                                        TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) + 1
                                    )
                                }

                                override fun onFinish() {
                                    if (dialog.isShowing) {
                                        appConfig.continueThisDayLogMacException = appConfig.lastMac
                                        toggleLogging()
                                        dialog.dismiss()
                                    }
                                }
                            }.start()
                        }
                    })
                    dialog.show()
                } else {
                    toggleLogging()
                }
                true
            }

            R.id.miReset -> {
                viewModel.resetMaxValues()
                showSnackBar(getString(R.string.reset_extremum_values_title))
                true
            }

            R.id.miSettings -> {
                toggleSettings()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    fun toggleSettings() {
        if (binding.settingsView.visibility != View.VISIBLE) {
            binding.settingsView.apply {
                alpha = 0f
                visibility = View.VISIBLE
                animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setListener(null)
            }
        } else {
            binding.settingsView
                .animate()
                .alpha(0f)
                .setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.settingsView.visibility = View.GONE
                    }
                })
            checkClockVisible()
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            /*            KeyEvent.KEYCODE_BACK -> {
                            if (binding.settingsView.visibility == View.VISIBLE) {
                                if (settingsNavHostController != null) {
                                    if (settingsNavHostController?.previousBackStackEntry == null) {
                                        toggleSettings()
                                    } else {
                                        settingsNavHostController?.navigateUp()
                                    }
                                } else {
                                    toggleSettings()
                                }
                                return true
                            }
                            if (doubleBackToExitPressedOnce) {
                                finish()
                                return true
                            }
                            doubleBackToExitPressedOnce = true
                            showSnackBar(R.string.back_to_exit)
                            Handler(Looper.getMainLooper()).postDelayed(
                                { doubleBackToExitPressedOnce = false },
                                2000
                            )
                            true
                        }*/

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showSnackBar(msg: Int) {
        showSnackBar(getString(msg))
    }

    private fun showSnackBar(msg: String?, timeout: Int = 2000) {
        if (!isPaused) {
            if (snackbar == null) {
                snackbar = Snackbar
                    .make(binding.mainView, "", Snackbar.LENGTH_LONG).apply {
                        view.setBackgroundResource(R.color.primary_dark)
                        setAction(android.R.string.ok) { }
                    }
            }
            snackbar?.apply {
                duration = timeout
                setText(msg!!)
                show()
            }
        }
        Timber.wtf(msg)
    }

    private fun hideSnackBar() {
        snackbar?.dismiss()
    }

    //region services
    private fun stopLoggingService() {
        if (LoggingService.isInstanceCreated()) {
            toggleLoggingService()
        }
    }

    private fun toggleLogging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            toggleLoggingService()
        } else {
            checkExternalFilePermission(this, RESULT_REQUEST_PERMISSIONS_IO)
        }
    }

    fun toggleLoggingService() {
        val dataLoggerServiceIntent = Intent(applicationContext, LoggingService::class.java)
        if (LoggingService.isInstanceCreated()) {
            unbindService(mLoggingServiceConnection)
            loggingService = null
            if (!onDestroyProcess) {
                CoroutineScope(Dispatchers.Main + Job()).launch {
                    pagerAdapter.updatePageOfTrips()
                }
            }
        } else if (mConnectionState == BLEConstants.ConnectionState.CONNECTED) {
            bindService(dataLoggerServiceIntent, mLoggingServiceConnection, BIND_AUTO_CREATE)
        }
        setMenuIconStates()
        notifications.update()
    }
    //endregion

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToLastDevice() {
        val lastMac = appConfig.lastMac
        if (checkBlePermissions(this, RESULT_REQUEST_PERMISSIONS_BT) && lastMac.isNotEmpty()) {
            viewModel.connectByAddress(lastMac)
        } else if (isMaxBleReq) {
            showSnackBar(R.string.bluetooth_required)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun toggleConnectToWheel() {
        if (mConnectionState == BLEConstants.ConnectionState.CONNECTED) {
            if (checkBlePermissions(this, RESULT_REQUEST_PERMISSIONS_BT)) {
                viewModel.disconnect()
            }
        } else {
            connectToLastDevice()
        }
    }

    @androidx.annotation.RequiresPermission(
        android.Manifest.permission.BLUETOOTH_CONNECT
    )
    private fun startScanActivity() {
        if (checkBlePermissions(this, RESULT_REQUEST_PERMISSIONS_BT)) {
            scanLauncher.launch(Intent(this@MainActivity, ScanActivity::class.java))
        } else if (isMaxBleReq) {
            showSnackBar(R.string.bluetooth_required)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RESULT_REQUEST_PERMISSIONS_BT) {
            connectToLastDevice()
        } else if (requestCode == RESULT_REQUEST_PERMISSIONS_IO) {
            toggleLoggingService()
        }
    }

    /**
     * Broadcast receiver for notification action buttons.
     * Replaces the legacy mCoreBroadcastReceiver for notification button handling.
     */
    private val mNotificationButtonReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.NOTIFICATION_BUTTON_CONNECTION -> {
                    toggleConnectToWheel()
                    notifications.update()
                }

                Constants.NOTIFICATION_BUTTON_LOGGING -> {
                    toggleLogging()
                    notifications.update()
                }

                Constants.NOTIFICATION_BUTTON_BEEP -> playBeep()
                Constants.ACTION_PREFERENCE_RESET -> {
                    Timber.i("Reset battery lowest")
                    pagerAdapter.wheelView?.resetBatteryLowest()
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private val scanLauncher =

        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode == RESULT_OK) {
                val mac = result.data?.getStringExtra("MAC") ?: ""
                val name = result.data?.getStringExtra("NAME") ?: ""
                Timber.i("Device selected MAC = %s, Name = %s", mac, name)
                viewModel.fullReset()
                pagerAdapter.updateScreen(true)
                setMenuIconStates()

                if (mac.isNotEmpty() && checkBlePermissions(this, RESULT_REQUEST_PERMISSIONS_BT)) {
                    viewModel.connectByAddress(mac, name)
                }
            } else {
                Timber.i("Scan device selection cancelled.")
            }
        }

    @Suppress("MissingPermission")
    private val enableBleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && mBluetoothAdapter!!.isEnabled) {
                connectToLastDevice()
            } else {
                Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    // File import via Android content picker
    val getCsvResults =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
            uris?.let {
                CoroutineScope(Dispatchers.IO + Job()).launch {
                    for (uri in it) {
                        contentResolver.apply {
                            query(uri, null, null, null, null)?.use { cursor ->
                                val nameIndex =
                                    cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                                cursor.moveToFirst()
                                cursor.getString(nameIndex)
                            }?.let { fileName ->
                                openInputStream(uri)?.use { stream ->
                                    val fileUtil = FileUtil(this@MainActivity)
                                    fileUtil.prepareFile(fileName, "manual")
                                    fileUtil.writeAllStream(stream)
                                    fileUtil.close()
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (::pagerAdapter.isInitialized) {
                            pagerAdapter.updatePageOfTrips()
                        }
                    }
                }
            }
        }

    private fun makeNotificationIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(Constants.NOTIFICATION_BUTTON_CONNECTION)
            addAction(Constants.NOTIFICATION_BUTTON_LOGGING)
            addAction(Constants.NOTIFICATION_BUTTON_BEEP)
            addAction(Constants.ACTION_PREFERENCE_RESET)
        }
    }

    companion object {
        lateinit var audioManager: AudioManager

        const val RESULT_REQUEST_PERMISSIONS_BT = 40
        const val RESULT_REQUEST_PERMISSIONS_IO = 50
        private var onDestroyProcess = false

        var isPaused = true
    }
}