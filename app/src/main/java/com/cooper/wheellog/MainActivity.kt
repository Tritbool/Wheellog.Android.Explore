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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.cooper.wheellog.DialogHelper.checkAndShowPrivatePolicyDialog
import com.cooper.wheellog.DialogHelper.checkBatteryOptimizationsAndShowAlert
import com.cooper.wheellog.DialogHelper.checkPWMIsSetAndShowAlert
import com.cooper.wheellog.ble.BleService
import com.cooper.wheellog.ble.EucBleManager
import com.cooper.wheellog.compose.MainScreen
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
import io.github.tritbool.euc.ble.models.EUCDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val appConfig: AppConfig by inject()
    private val notifications: NotificationUtil by inject()
    private val volumeKeyController: VolumeKeyController by inject()
    private val eucBleManager: EucBleManager by inject()
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
    // mSelectedDevice : EUCDevice choisi via ScanActivity. Contient toujours un bluetoothDevice
    // non-null quand il vient du scan. null tant qu'aucun device n'a été sélectionné depuis
    // le scan dans cette session (même si lastMac est connu : on utilise connectByAddress).
    private var mSelectedDevice: EUCDevice? = null
    private var mConnectionState = BLEConstants.ConnectionState.DISCONNECTED
    private var isWheelSearch = false
    private var doubleBackToExitPressedOnce = false
    private var snackbar: Snackbar? = null
    private val timeFormatter = SimpleDateFormat("HH:mm:ss ", Locale.US)
    private val speedModel: PiPView.SpeedModel by lazy { PiPView.SpeedModel() }
    private var settingsNavHostController: NavHostController? = null
    private var loggingService: LoggingService? = null
    // Job de collecte du StateFlow BLE — annulé dans onPause, relancé dans onResume.
    private var bleStateJob: Job? = null

    // BleService binding
    private var bleService: BleService? = null
    private val mBleServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            bleService = (service as BleService.LocalBinder).getService()
            Timber.i("BleService connected")
        }
        override fun onServiceDisconnected(name: ComponentName) {
            bleService = null
            Timber.e("BleService disconnected")
        }
    }

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
            Timber.e("LoggingService binding died")
        }
    }

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
                ContextCompat.registerReceiver(
                    this,
                    mPiPBroadcastReceiver,
                    makeIntentPipFilter(),
                    ContextCompat.RECEIVER_EXPORTED
                )
            } catch (_: Exception) {}
            finally {
                pipView.setContent {
                    PiPView().SpeedWidget(modifier = Modifier.fillMaxSize(), model = speedModel)
                }
                pipView.visibility = View.VISIBLE
            }
        } else {
            try {
                unregisterReceiver(mPiPBroadcastReceiver)
            } catch (_: Exception) {}
            pipView.visibility = View.GONE
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (appConfig.usePipMode
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !this.isInPictureInPictureMode) {
            when (appConfig.pipBlock) {
                getString(R.string.consumption) -> speedModel.title = getString(R.string.consumption)
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
                val mac = mSelectedDevice?.address ?: appConfig.lastMac
                if (mac.isNotEmpty()) {
                    appConfig.lastMac = mac
                    if (appConfig.autoUploadEc && appConfig.ecToken != null) {
                        ElectroClub.instance.getAndSelectGarageByMacOrShowChooseDialog(
                            mac,
                            this
                        ) { }
                    }
                }
                if (appConfig.useBeepOnVolumeUp) {
                    volumeKeyController.setActive(true)
                }
                hideSnackBar()
                if (!LoggingService.isInstanceCreated() &&
                    appConfig.autoLog &&
                    appConfig.startAutoLoggingWhenIsMovingMore == 0f
                ) {
                    toggleLoggingService()
                }
                // Protocol init (name/serial requests etc.) is handled internally
                // by the euc_ble_library protocols — no adapter calls needed here.
                bleService?.onWheelConnected()
                notifications.notificationMessageId = R.string.connected
            }
            BLEConstants.ConnectionState.CONNECTING -> {
                isWheelSearch = true
                bleService?.onWheelConnecting()
                notifications.notificationMessageId = R.string.connecting
            }
            BLEConstants.ConnectionState.DISCONNECTED -> {
                val wasConnectedOrConnecting =
                    mConnectionState == BLEConstants.ConnectionState.CONNECTED ||
                    mConnectionState == BLEConstants.ConnectionState.CONNECTING
                if (wasConnectedOrConnecting) {
                    val disconnectMsg = timeFormatter.format(Date()) + getString(R.string.connection_lost_at)
                    showSnackBar(disconnectMsg, Snackbar.LENGTH_INDEFINITE)
                    // unexpected disconnect → play disconnect sound + beep timer
                    bleService?.onWheelDisconnected(unexpected = true)
                } else {
                    bleService?.onWheelDisconnected(unexpected = false)
                }
                if (appConfig.useBeepOnVolumeUp) {
                    volumeKeyController.setActive(false)
                }
                isWheelSearch = false
                // Protocol state reset is handled internally by euc_ble_library.
                // No legacy adapter calls (InMotionAdapter.newInstance etc.) needed.
                notifications.notificationMessageId = R.string.disconnected
            }
            BLEConstants.ConnectionState.DISCONNECTING -> {
                notifications.notificationMessageId = R.string.disconnected
            }
        }
        mConnectionState = connectionState
        // WheelData.getInstance().isConnected = (connectionState == BLEConstants.ConnectionState.CONNECTED)
        // Now using eucBleManager.isConnected directly
        setMenuIconStates()
        notifications.update()
    }

    private val mMainViewBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isPaused) return
            when (intent.action) {
                Constants.ACTION_WHEEL_TYPE_CHANGED -> {
                    Timber.i("Wheel type switched")
                    pagerAdapter.configureSecondDisplay()
                    pagerAdapter.updateScreen(true)
                }
                Constants.ACTION_WHEEL_DATA_AVAILABLE -> {
                    pagerAdapter.updateScreen(
                        intent.hasExtra(Constants.INTENT_EXTRA_GRAPH_UPDATE_AVAILABLE)
                    )
                }
                Constants.ACTION_WHEEL_NEWS_AVAILABLE -> {
                    Timber.i("Received news")
                    showSnackBar(intent.getStringExtra(Constants.INTENT_EXTRA_NEWS), 1500)
                }
                Constants.ACTION_WHEEL_TYPE_RECOGNIZED -> {}
                Constants.ACTION_WHEEL_MODEL_CHANGED -> pagerAdapter.configureSmartBmsDisplay()
                Constants.ACTION_ALARM_TRIGGERED -> {
                    val alarmType = intent.getSerializable(Constants.INTENT_EXTRA_ALARM_TYPE, ALARM_TYPE::class.java)?.value ?: 0
                    val alarmValue = intent.getDoubleExtra(Constants.INTENT_EXTRA_ALARM_VALUE, 0.0)
                    if (alarmType < 4) {
                        showSnackBar(resources.getString(R.string.alarm_text_speed) + String.format(": %.1f", alarmValue), 3000)
                    }
                    if (alarmType == ALARM_TYPE.CURRENT.value) {
                        showSnackBar(resources.getString(R.string.alarm_text_current) + String.format(": %.1f", alarmValue), 3000)
                    }
                    if (alarmType == ALARM_TYPE.TEMPERATURE.value) {
                        showSnackBar(resources.getString(R.string.alarm_text_temperature) + String.format(": %.1f", alarmValue), 3000)
                    }
                    if (alarmType == ALARM_TYPE.PWM.value) {
                        showSnackBar(resources.getString(R.string.alarm_text_pwm) + String.format(": %.1f", alarmValue * 100), 3000)
                    }
                    if (alarmType == ALARM_TYPE.BATTERY.value) {
                        showSnackBar(resources.getString(R.string.alarm_text_battery) + String.format(": %.0f", alarmValue), 3000)
                    }
                }
                Constants.ACTION_WHEEL_IS_READY -> checkPWMIsSetAndShowAlert(this@MainActivity)
            }
        }
    }

    private val mPiPBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.ACTION_WHEEL_TYPE_CHANGED -> Timber.i("Wheel type switched")
                Constants.ACTION_WHEEL_DATA_AVAILABLE -> {
                    when (appConfig.pipBlock) {
                        getString(R.string.consumption) -> speedModel.value.floatValue = Calculator.whByKm.toFloat()
                        else -> speedModel.value.floatValue = eucBleManager.eucData.value?.speed?.toFloat() ?: 0f
                    }
                    pipView.invalidate()
                }
            }
        }
    }

    private val mCoreBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Constants.ACTION_PREFERENCE_RESET -> {
                    Timber.i("Reset battery lowest")
                    pagerAdapter.wheelView?.resetBatteryLowest()
                }
                Constants.ACTION_WHEEL_DATA_AVAILABLE -> {
                    loggingService?.updateFile()
                    if (!LoggingService.isInstanceCreated() &&
                        appConfig.startAutoLoggingWhenIsMovingMore != 0f &&
                        appConfig.autoLog &&
                        (eucBleManager.eucData.value?.speed ?: 0.0) > appConfig.startAutoLoggingWhenIsMovingMore
                    ) {
                        toggleLoggingService()
                    }
                    if (appConfig.alarmsEnabled) {
                        checkAlarm((eucBleManager.eucData.value?.pwm ?: 0.0) / 100, applicationContext)
                    }
                }
                Constants.ACTION_LOGGING_SERVICE_TOGGLED -> {
                    val running = intent.getBooleanExtra(Constants.INTENT_EXTRA_IS_RUNNING, false)
                    if (intent.hasExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION)) {
                        val filepath = intent.getStringExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION)
                        val fileName = filepath!!.substring(filepath.lastIndexOf("\\") + 1)
                        if (running) {
                            showSnackBar(resources.getString(R.string.started_logging) + " " + fileName, 5000)
                        }
                    }
                    setMenuIconStates()
                    notifications.update()
                }
                Constants.ACTION_RAW_LOGGING_TOGGLED -> {
                    val running = intent.getBooleanExtra(Constants.INTENT_EXTRA_IS_RUNNING, false)
                    val paused = intent.getBooleanExtra("raw_logging_paused", false)
                    val resumed = intent.getBooleanExtra("raw_logging_resumed", false)
                    if (intent.hasExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION)) {
                        val filepath = intent.getStringExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION)
                        val fileName = filepath!!.substring(filepath.lastIndexOf("\\") + 1)
                        when {
                            paused   -> showSnackBar(resources.getString(R.string.paused_raw_logging), 5000)
                            resumed  -> showSnackBar(resources.getString(R.string.resumed_raw_logging) + " " + fileName, 5000)
                            running  -> showSnackBar(resources.getString(R.string.started_raw_logging) + " " + fileName, 5000)
                            else     -> showSnackBar(resources.getString(R.string.stopped_raw_logging) + " " + fileName, 5000)
                        }
                    }
                }
                Constants.NOTIFICATION_BUTTON_CONNECTION -> {
                    toggleConnectToWheel()
                    notifications.update()
                }
                Constants.NOTIFICATION_BUTTON_LOGGING -> {
                    toggleLogging()
                    notifications.update()
                }
                Constants.NOTIFICATION_BUTTON_BEEP -> playBeep()
                Constants.NO********ION_BUTTON_LIGHT -> {
                    // TODO: Implement with EucBleClient
                }
            }
        }
    }

    private fun toggleLogging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            toggleLoggingService()
        } else {
            checkExternalFilePermission(this, RESULT_REQUEST_PERMISSIONS_IO)
        }
    }

    private fun setMenuIconStates() {
        if (mMenu == null) return
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
        if (appConfig.pageGraph)  pages.add(R.layout.main_view_graph)
        if (appConfig.pageTrips)  pages.add(R.layout.main_view_trips)
        if (appConfig.pageEvents) pages.add(R.layout.main_view_events)
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

    private fun startBleService() {
        val intent = Intent(this, BleService::class.java)
        bindService(intent, mBleServiceConnection, BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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

        ElectroClub.instance.apply {
            errorListener = { method: String?, error: String? ->
                val message = "[ec] $method error: $error"
                Timber.i(message)
                runOnUiThread { showSnackBar(message, 4000) }
            }
            successListener = label@{ method: String?, success: Any? ->
                if (method == ElectroClub.GET_GARAGE_METHOD) return@label
                val message = "[ec] $method ok: $success"
                Timber.i(message)
                runOnUiThread { showSnackBar(message, 4000) }
            }
        }
        createPager()
        pipView = binding.pipView

        binding.textClock.typeface = ThemeManager.getTypeface(applicationContext)

        // NE PAS stocker de EUCDevice reconstruit depuis lastMac ici.
        // mSelectedDevice reste null jusqu'à un vrai scan.
        // La connexion depuis lastMac passe par eucBleManager.connectByAddress().

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        checkAndShowPrivatePolicyDialog(this)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
        } else if (!mBluetoothAdapter!!.isEnabled) {
            if (checkBlePermissions(this, RESULT_REQUEST_PERMISSIONS_BT)) {
                enableBleLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }

        try {
            unregisterReceiver(mCoreBroadcastReceiver)
        } catch (_: Exception) {}
        ContextCompat.registerReceiver(
            this,
            mCoreBroadcastReceiver,
            makeCoreIntentFilter(),
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

        startBleService()
        checkBatteryOptimizationsAndShowAlert(this)
    }

    private fun checkClockVisible() {
        binding.textClock.visibility = if (appConfig.showClock) View.VISIBLE else View.GONE
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    public override fun onResume() {
        super.onResume()
        isPaused = false

        bleStateJob = eucBleManager.isConnected
            .onEach { connected ->
                val state = if (connected) BLEConstants.ConnectionState.CONNECTED
                            else eucBleManager.getConnectionState()
                runOnUiThread { setConnectionState(state) }
            }
            .launchIn(lifecycleScope)

        if (eucBleManager.connectedDevice.value?.manufacturer?.toLegacyWheelType() ?: WHEEL_TYPE.Unknown != WHEEL_TYPE.Unknown) {
            pagerAdapter.configureSecondDisplay()
        }
        if (checkNotificationsPermissions(this)) {
            notifications.update()
        }
        try {
            ContextCompat.registerReceiver(
                this,
                mMainViewBroadcastReceiver,
                makeIntentFilter(),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
        pagerAdapter.updateScreen(true)
        pagerAdapter.updatePageOfTrips()
        checkClockVisible()
        DialogHelper.checkAndShowLocationDialog(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setMenuIconStates()
    }

    public override fun onPause() {
        super.onPause()
        isPaused = true
        bleStateJob?.cancel()
        bleStateJob = null
        try {
            unregisterReceiver(mMainViewBroadcastReceiver)
        } catch (e: Exception) {
            Timber.e(e)
        }
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
        outState.putInt(BLEConstants.ConnectionState::class.simpleName, mConnectionState.ordinal)
        outState.putBoolean(isWheelSearch::class.simpleName, isWheelSearch)
        mSelectedDevice?.let { outState.putString("selectedDeviceAddress", it.address) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val stateOrdinal = savedInstanceState.getInt(BLEConstants.ConnectionState::class.simpleName, BLEConstants.ConnectionState.DISCONNECTED.ordinal)
        mConnectionState = BLEConstants.ConnectionState.entries[stateOrdinal]
        isWheelSearch = savedInstanceState.getBoolean(isWheelSearch::class.simpleName, false)
        // On ne restaure PAS mSelectedDevice depuis le bundle : après une rotation par exemple,
        // si on était connecté le StateFlow le signalera ; si déconnecté on attendra le scan.
        setConnectionState(mConnectionState)
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
        eucBleManager.client.cleanup(); eucBleManager.client.initialize()

        @Suppress("MissingPermission")
        eucBleManager.cleanup()

        if (bleService != null) {
            try { unbindService(mBleServiceConnection) } catch (_: Exception) {}
        }
        if (loggingService != null) {
            try {
                unbindService(mLoggingServiceConnection)
            } catch (_: Exception) {}
        }
        ThemeManager.changeAppIcon(this@MainActivity)
        object : CountDownTimer((2 * 60 * 1000L), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!LoggingService.isInstanceCreated()) onFinish()
            }
            override fun onFinish() {
                notifications.close()
                Timber.uproot(eventsLoggingTree!!)
                eventsLoggingTree!!.close()
                eventsLoggingTree = null
                try {
                    unregisterReceiver(mCoreBroadcastReceiver)
                } catch (_: Exception) {}
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
            miSearch  = findItem(R.id.miSearch)
            miWheel   = findItem(R.id.miWheel)
            miLogging = findItem(R.id.miLogging)
        }
        if (appConfig.appTheme == R.style.AJDMTheme) {
            val miSettings = mMenu!!.findItem(R.id.miSettings)
            miSettings.setIcon(ThemeManager.getId(ThemeIconEnum.MenuSettings))
            miSearch!!.setIcon(ThemeManager.getId(ThemeIconEnum.MenuBluetooth))
        }
        return true
    }

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
                            val defaultButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
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
                // Reset handled by EucBleManager
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
                animate().alpha(1f).setDuration(300).setListener(null)
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
            KeyEvent.KEYCODE_BACK -> {
                if (binding.settingsView.visibility == View.VISIBLE) {
                    if (settingsNavHostController?.previousBackStackEntry == null) {
                        toggleSettings()
                    } else {
                        settingsNavHostController?.navigateUp()
                    }
                    return true
                }
                if (doubleBackToExitPressedOnce) {
                    finish()
                    return true
                }
                doubleBackToExitPressedOnce = true
                showSnackBar(R.string.back_to_exit)
                Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showSnackBar(msg: Int) = showSnackBar(getString(msg))

    private fun showSnackBar(msg: String?, timeout: Int = 2000) {
        if (!isPaused) {
            if (snackbar == null) {
                snackbar = Snackbar.make(binding.mainView, "", Snackbar.LENGTH_LONG).apply {
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

    // region services
    private fun stopLoggingService() {
        if (LoggingService.isInstanceCreated()) toggleLoggingService()
    }

    fun toggleLoggingService() {
        val dataLoggerServiceIntent = Intent(applicationContext, LoggingService::class.java)
        if (LoggingService.isInstanceCreated()) {
            unbindService(mLoggingServiceConnection)
            if (!onDestroyProcess) {
                lifecycleScope.launchWhenStarted {
                    pagerAdapter.updatePageOfTrips()
                }
            }
        } else if (mConnectionState == BLEConstants.ConnectionState.CONNECTED) {
            bindService(dataLoggerServiceIntent, mLoggingServiceConnection, BIND_AUTO_CREATE)
        }
    }

    private fun toggleConnectToWheel() {
        when (eucBleManager.getConnectionState()) {
            BLEConstants.ConnectionState.CONNECTED,
            BLEConstants.ConnectionState.CONNECTING -> {
                @Suppress("MissingPermission")
                eucBleManager.disconnect()
            }
            else -> {
                // Priorité 1 : device avec BluetoothDevice complet issu du scan (cette session)
                val device = mSelectedDevice
                if (device != null && device.bluetoothDevice != null) {
                    @Suppress("MissingPermission")
                    eucBleManager.connect(device)
                    return
                }
                // Priorité 2 : lastMac connu → connectByAddress() retrouve le BluetoothDevice
                // via l'adapter Android (fonctionne même sans pairing)
                val lastMac = appConfig.lastMac
                if (lastMac.isNotEmpty()) {
                    @Suppress("MissingPermission")
                    val connected = eucBleManager.connectByAddress(
                        address        = lastMac,
                        name           = eucBleManager.connectedDevice.value?.name ?: "" ?: "",
                        manufacturerId = 0
                    )
                    if (connected) return
                }
                // Priorité 3 : aucune info → lancer le scan
                startScanActivity()
            }
        }
    }

    private fun startScanActivity() {
        if (checkBlePermissions(this, RESULT_REQUEST_PERMISSIONS_BT)) {
            scanLauncher.launch(Intent(this@MainActivity, ScanActivity::class.java))
        } else if (isMaxBleReq) {
            showSnackBar(R.string.bluetooth_required)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RESULT_REQUEST_PERMISSIONS_BT) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                toggleConnectToWheel()
            }
        } else if (requestCode == RESULT_REQUEST_PERMISSIONS_IO) {
            toggleLoggingService()
        }
    }
    // endregion

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val mac  = result.data?.getStringExtra("MAC")  ?: return@registerForActivityResult
            val name = result.data?.getStringExtra("NAME") ?: ""
            val mfid = result.data?.getIntExtra("MANUFACTURER_ID", 0) ?: 0
            val rssi = result.data?.getIntExtra("RSSI", 0) ?: 0
            Timber.i("Device selected = %s (%s)", mac, name)
            // On cherche le EUCDevice complet (avec bluetoothDevice) dans la liste des devices
            // découverts par EucBleManager — il contient le vrai BluetoothDevice issu du scan.
            val discovered = eucBleManager.discoveredDevices.value.find { it.address == mac }
            mSelectedDevice = discovered ?: EUCDevice(
                bluetoothDevice  = null,  // fallback : sera résolu par connectByAddress
                name             = name,
                address          = mac,
                manufacturerId   = mfid,
                rssi             = rssi
            )
            appConfig.lastMac = mac
            eucBleManager.client.cleanup(); eucBleManager.client.initialize()
            eucBleManager.connectedDevice.value?.name ?: "" = name
            pagerAdapter.updateScreen(true)
            setMenuIconStates()
            toggleConnectToWheel()
            if (appConfig.autoUploadEc && appConfig.ecToken != null) {
                ElectroClub.instance.getAndSelectGarageByMacOrShowChooseDialog(mac, this) { }
            }
        } else {
            Timber.i("Scan device is failed.")
        }
    }

    private val enableBleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && mBluetoothAdapter!!.isEnabled) {
            toggleConnectToWheel()
        } else {
            Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    val getCsvResults = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
        uris?.let {
            lifecycleScope.launchWhenStarted {
                for (uri in it) {
                    contentResolver.apply {
                        query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
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
                if (::pagerAdapter.isInitialized) {
                    pagerAdapter.updatePageOfTrips()
                }
            }
        }
    }

    private fun makeIntentPipFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(Constants.ACTION_WHEEL_DATA_AVAILABLE)
            addAction(Constants.ACTION_WHEEL_MODEL_CHANGED)
        }
    }

    private fun makeIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(Constants.ACTION_WHEEL_DATA_AVAILABLE)
            addAction(Constants.ACTION_LOGGING_SERVICE_TOGGLED)
            addAction(Constants.ACTION_RAW_LOGGING_TOGGLED)
            addAction(Constants.ACTION_WHEEL_TYPE_RECOGNIZED)
            addAction(Constants.ACTION_WHEEL_MODEL_CHANGED)
            addAction(Constants.ACTION_ALARM_TRIGGERED)
            addAction(Constants.ACTION_WHEEL_TYPE_CHANGED)
            addAction(Constants.ACTION_WHEEL_NEWS_AVAILABLE)
            addAction(Constants.ACTION_WHEEL_IS_READY)
        }
    }

    private fun makeCoreIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(Constants.ACTION_WHEEL_DATA_AVAILABLE)
            addAction(Constants.ACTION_LOGGING_SERVICE_TOGGLED)
            addAction(Constants.ACTION_RAW_LOGGING_TOGGLED)
            addAction(Constants.ACTION_PREFERENCE_RESET)
            addAction(Constants.NOTIFICATION_BUTTON_CONNECTION)
            addAction(Constants.NOTIFICATION_BUTTON_LOGGING)
            addAction(Constants.NOTIFICATION_BUTTON_BEEP)
            addAction(Constants.NOTIFICATION_BUTTON_LIGHT)
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
