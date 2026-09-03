package com.cooper.wheellog
import com.cooper.wheellog.ble.BleSessionViewModel

import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.widget.Toast
import com.cooper.wheellog.data.TripDao
import com.cooper.wheellog.utils.Constants
import com.cooper.wheellog.utils.FileUtil
import com.cooper.wheellog.utils.NotificationUtil
// import com.cooper.wheellog.utils.ParserLogToWheelData - REMOVED: Use EUCData parser
import com.cooper.wheellog.utils.PermissionsUtil.checkExternalFilePermission
import io.github.tritbool.euc.ble.core.BLEConstants
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoggingService : Service() {
    private val appConfig: AppConfig by inject()
    private val viewModel: BleSessionViewModel by inject()
    private val notifications: NotificationUtil by inject()
    private val dao: TripDao by inject()
    private var sdf: SimpleDateFormat? = null
    private var fileUtil: FileUtil? = null
    private var ioState = CoroutineScope(Dispatchers.IO + Job())

    // Guards against re-running the (file creating) initialization when onStartCommand is
    // redelivered, and against writing the same telemetry sample twice.
    private var logStarted = false
    private var lastLoggedTimestamp: Long? = null

    fun updateConnectionState(connectionState: BLEConstants.ConnectionState) {
        if (connectionState != BLEConstants.ConnectionState.CONNECTED) {
            // Park logging: nothing is appended until fresh telemetry arrives again.
            lastLoggedTimestamp = null
        }
    }

    private val mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = mBinder

    override fun onCreate() {
        super.onCreate()
        instance = this
        sdf = SimpleDateFormat("yyyy-MM-dd,HH:mm:ss.SSS", Locale.US)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen within a few seconds of startForegroundService(), otherwise Android
        // kills the service with ForegroundServiceDidNotStartInTimeException.
        startAsForeground()

        if (!logStarted) {
            if (!startLogging()) {
                stopSelf()
                return START_NOT_STICKY
            }
            logStarted = true
            observeTelemetry()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        if (notifications.notification == null) {
            notifications.update()
        }
        val notification = notifications.notification ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.MAIN_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(Constants.MAIN_NOTIFICATION_ID, notification)
        }
    }

    /**
     * Prepares (or reopens) the CSV file. Returns false when logging cannot be started.
     */
    private fun startLogging(): Boolean {
        var file = FileUtil(applicationContext)
        fileUtil = file
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!checkExternalFilePermission(this)) {
                showToast(R.string.logging_error_no_storage_permission)
                return false
            }
            if (!isExternalStorageReadable || !isExternalStorageWritable) {
                showToast(R.string.logging_error_storage_unavailable)
                return false
            }
        }
        var writeToLastLog = false
        val mac = viewModel.mac
        if (appConfig.continueThisDayLog &&
            appConfig.continueThisDayLogMacException != mac
        ) {
            val lastFileUtil = FileUtil.getLastLog(applicationContext)
            if (lastFileUtil?.file?.path?.contains(mac.replace(':', '_')) == true
            ) {
                file = lastFileUtil
                fileUtil = file
                // parse prev log for filling session state - TODO: Implement EUCData parser
                // val parser = ParserLogToWheelData()
                // parser.parseFile(fileUtil)
                file.prepareStream()
                writeToLastLog = true
                // reset trip duration for recalculation in trip list
                ioState.launch {
                    dao.getTripByFileName(file.file!!.name)?.apply {
                        duration = 0
                        dao.update(this)
                    }
                }
            }
        }
        if (!writeToLastLog) {
            val sdFormatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
            val filename = sdFormatter.format(Date()) + ".csv"
            if (!file.prepareFile(filename, mac)) {
                return false
            }
            appConfig.continueThisDayLogMacException = ""
            file.writeLine("date,time,speed,voltage,phase_current,current,power,torque,pwm,battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert")
        }
        broadcastState(file.absolutePath, true)
        Timber.i("DataLogger Started")
        return true
    }

    /**
     * Logging is driven from the service itself so that it keeps running while the app is in
     * the background or the screen is off (it used to be pushed by MainActivity, which stopped
     * as soon as the activity was paused).
     */
    private fun observeTelemetry() {
        ioState.launch {
            viewModel.sessionState.collect { state ->
                if (!state.isConnected) {
                    lastLoggedTimestamp = null
                    return@collect
                }
                val timestamp = state.lastDataTimestamp ?: return@collect
                if (timestamp == lastLoggedTimestamp) return@collect
                lastLoggedTimestamp = timestamp
                updateFile()
            }
        }
    }

    private fun broadcastState(path: String?, isRunning: Boolean) {
        val serviceIntent = Intent(Constants.ACTION_LOGGING_SERVICE_TOGGLED).apply {
            // Keep the broadcast internal to the app.
            setPackage(packageName)
            if (!isNullOrEmpty(path)) {
                putExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION, path)
            }
            putExtra(Constants.INTENT_EXTRA_IS_RUNNING, isRunning)
        }
        sendBroadcast(serviceIntent)
    }

    private fun isNullOrEmpty(s: String?): Boolean {
        return s == null || s.trim { it <= ' ' }.isEmpty()
    }

    override fun onDestroy() {
        ioState.cancel()
        val path = fileUtil?.absolutePath
        fileUtil?.close()
        fileUtil = null
        Timber.wtf("DataLogger Stopping...")
        notifications.setCustomTitle("Uploading tack...")
        broadcastState(path, false)
        instance = null
        Timber.wtf("DataLogger Stopped")
        super.onDestroy()
    }

    private val isExternalStorageWritable: Boolean
        /* Checks if external storage is available for read and write */
        get() {
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED == state
        }
    private val isExternalStorageReadable: Boolean
        /* Checks if external storage is available to at least read */
        get() {
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state
        }

    fun updateFile() {
        val wd = viewModel
        val file = fileUtil ?: return
        val formatter = sdf ?: return
        file.writeLine(
            String.format(
                Locale.US,
                "%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%s,%d",
                formatter.format(System.currentTimeMillis()),
                wd.speedDouble,
                wd.voltageDouble,
                wd.phaseCurrentDouble,
                wd.currentDouble,
                wd.powerDouble,
                wd.torque,
                wd.calculatedPwm,
                wd.batteryLevel,
                wd.distanceDouble,
                wd.totalDistanceDouble,
                wd.temperatureDouble,
                wd.motorTemperature / 100.0,
                wd.angle,
                wd.roll,
                wd.modeStr,
                0
            )
        )
    }

    private fun showToast(messageId: Int) {
        Toast.makeText(this, messageId, Toast.LENGTH_LONG).show()
    }

    inner class LocalBinder : Binder() {
        fun getService(): LoggingService {
            return this@LoggingService
        }
    }

    companion object {
        private var instance: LoggingService? = null
        fun isInstanceCreated(): Boolean {
            return instance != null
        }
    }
}
