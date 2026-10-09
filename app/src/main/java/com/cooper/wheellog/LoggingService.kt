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
import io.github.tritbool.euc.ble.models.BMSData
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

    private var rawFileUtil: FileUtil? = null
    private var rawLoggingJob: Job? = null

    private var bmsFileUtil: FileUtil? = null
    private var lastBmsSignature: String? = null
    private var lastBmsWriteTimestamp: Long = 0L

    @Volatile
    private var latestBmsPacks: List<BMSData> = emptyList()

    fun updateConnectionState(connectionState: BLEConstants.ConnectionState) {
        if (connectionState != BLEConstants.ConnectionState.CONNECTED) {
            // Park logging: nothing is appended until fresh telemetry arrives again.
            lastLoggedTimestamp = null
            closeRawFile()
            closeBmsFile()
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
            observeBmsSnapshots()
            if (appConfig.enableRawData) observeRawFrames()
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


    private fun formatNullableDouble(
        value: Double?,
        decimals: Int,
    ): String {
        return value?.let {
            String.format(Locale.US, "%.${decimals}f", it)
        }.orEmpty()
    }

    private fun formatDoubleList(
        values: List<Double>?,
        decimals: Int,
    ): String {
        return "\"[" + values
            ?.joinToString(separator = ",") {
                String.format(Locale.US, "%.${decimals}f", it)
            }
            .orEmpty() + "]\""
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

    private fun observeBmsSnapshots() {
        ioState.launch {
            viewModel.bmsSnapshots.collect { packs ->
                latestBmsPacks = packs
            }
        }
    }


    private fun observeRawFrames() {
        rawLoggingJob?.cancel()

        rawLoggingJob = ioState.launch {
            viewModel.rawFrames.collect { frame ->
                if (!appConfig.enableRawData) {
                    closeRawFile()
                    return@collect
                }

                val rawFile = getOrCreateRawFile() ?: return@collect
                val hex = frame.joinToString(separator = "") { byte ->
                    "%02X".format(Locale.US, byte.toInt() and 0xFF)
                }

                rawFile.writeLine(
                    "${System.currentTimeMillis()},$hex"
                )
            }
        }
    }

    private fun closeRawFile() {
        rawFileUtil?.close()
        rawFileUtil = null
    }

    private fun closeBmsFile() {
        bmsFileUtil?.close()
        bmsFileUtil = null
        lastBmsSignature = null
        lastBmsWriteTimestamp = 0L
    }

    private fun getOrCreateRawFile(): FileUtil? {
        rawFileUtil?.let { return it }

        val rawFile = FileUtil(applicationContext)
        val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
        val filename = "${formatter.format(Date())}.raw.csv"

        if (!rawFile.prepareFile(filename, viewModel.mac)) {
            Timber.e("Unable to create RAW BLE log file")
            return null
        }

        rawFile.writeLine("timestamp_ms,hex")
        rawFileUtil = rawFile
        Timber.i("RAW BLE logging started: %s", rawFile.absolutePath)
        return rawFile
    }

    private fun bmsSignature(pack: BMSData): String {
        return listOf(
            pack.bmsIndex,
            pack.voltage,
            pack.current,
            pack.remainingCapacity,
            pack.factoryCapacity,
            pack.cycles,
            pack.isCharging,
            pack.temperatures?.joinToString(separator = ";"),
            pack.cellVoltages?.joinToString(separator = ";"),
        ).joinToString(separator = "|")
    }

    private fun formatBmsLine(
        timestampMillis: Long,
        pack: BMSData,
    ): String {
        return listOf(
            timestampMillis.toString(),
            pack.bmsIndex.toString(),
            formatNullableDouble(pack.voltage, 3),
            formatNullableDouble(pack.current, 3),
            pack.remainingCapacity?.toString().orEmpty(),
            pack.factoryCapacity?.toString().orEmpty(),
            pack.cycles?.toString().orEmpty(),
            pack.isCharging?.toString().orEmpty(),
            formatDoubleList(pack.temperatures, 2),
            formatDoubleList(pack.cellVoltages, 4),
        ).joinToString(separator = ";")
    }


    private fun getOrCreateBmsFile(): FileUtil? {
        bmsFileUtil?.let { return it }

        val file = FileUtil(applicationContext)
        val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
        val filename = "${formatter.format(Date())}.bms.csv"

        if (!file.prepareFile(filename, viewModel.mac)) {
            Timber.e("Unable to create BMS log file")
            return null
        }

        file.writeLine(
            "timestamp_ms;bms_index;voltage_v;current_a;" +
                    "remaining_capacity_mah;factory_capacity_mah;cycles;is_charging;" +
                    "temperatures_c;cell_voltages_v"
        )

        bmsFileUtil = file
        Timber.i("BMS logging started: %s", file.absolutePath)
        return file
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
        rawLoggingJob?.cancel()
        rawLoggingJob = null
        closeRawFile()
        closeBmsFile()
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

    private fun updateBmsFile() {
        if (!appConfig.enableBmsData) {
            closeBmsFile()
            return
        }

        val packs = latestBmsPacks.filter { pack ->
            pack.voltage != null ||
                    pack.current != null ||
                    !pack.temperatures.isNullOrEmpty() ||
                    !pack.cellVoltages.isNullOrEmpty()
        }
            ?.sortedBy { it.bmsIndex }
            .orEmpty()

        if (packs.isEmpty()) {
            return
        }

        val now = System.currentTimeMillis()
        val signature = packs.joinToString(separator = "|") { pack ->
            bmsSignature(pack)
        }

        val changed = signature != lastBmsSignature
        val intervalElapsed = now - lastBmsWriteTimestamp >= BMS_LOG_MIN_INTERVAL_MS

        if (!changed && !intervalElapsed) {
            return
        }

        val file = getOrCreateBmsFile() ?: return

        packs.forEach { pack ->
            file.writeLine(formatBmsLine(now, pack))
        }

        lastBmsSignature = signature
        lastBmsWriteTimestamp = now
    }

    fun updateFile() {
        val wd = viewModel
        val file = fileUtil ?: return
        val formatter = sdf ?: return
        file.writeLine(
            String.format(
                Locale.US,
                "%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%.1f,%.1f,%.2f,%.2f,%.2f,%.2f,%s,%d",
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
        if (appConfig.enableBmsData) updateBmsFile()
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

        private const val BMS_LOG_MIN_INTERVAL_MS = 1000L

        fun isInstanceCreated(): Boolean {
            return instance != null
        }
    }
}
