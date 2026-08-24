package com.cooper.wheellog
import com.cooper.wheellog.ble.BleSessionViewModel

import android.app.Service
import android.content.*
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoggingService : Service() {
    private val appConfig: AppConfig by inject()
    private val viewModel: BleSessionViewModel by inject()
    private val notifications: NotificationUtil by inject()
    private val dao: TripDao by inject()
    private var sdf: SimpleDateFormat? = null
    private lateinit var fileUtil: FileUtil
    private var ioState = CoroutineScope(Dispatchers.IO + Job())

    fun updateConnectionState(connectionState: BLEConstants.ConnectionState) {}

    private val mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder? {
        //     stopSelf()
        //     return null
        // }
        instance = this
        fileUtil = FileUtil(applicationContext)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!checkExternalFilePermission(this)) {
                showToast(R.string.logging_error_no_storage_permission)
                stopSelf()
                return mBinder
            }
            if (!isExternalStorageReadable || !isExternalStorageWritable) {
                showToast(R.string.logging_error_storage_unavailable)
                stopSelf()
                return mBinder
            }
        }
        sdf = SimpleDateFormat("yyyy-MM-dd,HH:mm:ss.SSS", Locale.US)
        var writeToLastLog = false
        val mac = viewModel.mac
        if (appConfig.continueThisDayLog &&
            appConfig.continueThisDayLogMacException != mac
        ) {
            val lastFileUtil = FileUtil.getLastLog(applicationContext)
            if (lastFileUtil?.file?.path?.contains(mac.replace(':', '_')) == true
            ) {
                fileUtil = lastFileUtil
                // parse prev log for filling session state - TODO: Implement EUCData parser
                // val parser = ParserLogToWheelData()
                // parser.parseFile(fileUtil)
                fileUtil.prepareStream()
                writeToLastLog = true
                // reset trip duration for recalculation in trip list
                ioState.launch {
                    dao.getTripByFileName(fileUtil.file!!.name)?.apply {
                        duration = 0
                        dao.update(this)
                    }
                }
            }
        }
        if (!writeToLastLog) {
            val sdFormatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
            val filename = sdFormatter.format(Date()) + ".csv"
            if (!fileUtil.prepareFile(filename, viewModel.mac)) {
                stopSelf()
                return mBinder
            }
            appConfig.continueThisDayLogMacException = ""
        }
        if (!writeToLastLog) {
            fileUtil.writeLine("date,time,speed,voltage,phase_current,current,power,torque,pwm,battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert")
        }
        val serviceIntent = Intent(Constants.ACTION_LOGGING_SERVICE_TOGGLED)
        serviceIntent.putExtra(
            Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION,
            fileUtil.absolutePath
        )
        serviceIntent.putExtra(Constants.INTENT_EXTRA_IS_RUNNING, true)
        sendBroadcast(serviceIntent)
        Timber.i("DataLogger Started")

        return mBinder
    }

    private fun isNullOrEmpty(s: String?): Boolean {
        return s == null || s.trim { it <= ' ' }.isEmpty()
    }

    override fun onDestroy() {
        var isBusy = false
        val path = fileUtil.absolutePath
        fileUtil.close()
        Timber.wtf("DataLogger Stopping...")
        notifications.setCustomTitle("Uploading tack...")

        if (!isBusy) {
            reallyDestroy(path)
        }
    }

    private fun reallyDestroy(path: String?) {
        val serviceIntent = Intent(Constants.ACTION_LOGGING_SERVICE_TOGGLED)
        if (!isNullOrEmpty(path)) {
            serviceIntent.putExtra(Constants.INTENT_EXTRA_LOGGING_FILE_LOCATION, path)
        }
        serviceIntent.putExtra(Constants.INTENT_EXTRA_IS_RUNNING, false)
        sendBroadcast(serviceIntent)
        instance = null
        Timber.wtf("DataLogger Stopped")
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
        fileUtil.writeLine(
            String.format(
                Locale.US, "%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%s,%d",
                sdf!!.format(System.currentTimeMillis()),
                wd.speedDouble,
                wd.voltageDouble,
                wd.phaseCurrentDouble,
                wd.currentDouble,
                wd.powerDouble,
                wd.torque,
                wd.calculatedPwm,
                wd.batteryLevel,
                (wd.distanceDouble * 1000).toInt(),
                (wd.totalDistanceDouble * 1000).toInt(),
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
        for (i in 0..3) Toast.makeText(this, messageId, Toast.LENGTH_LONG).show()
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