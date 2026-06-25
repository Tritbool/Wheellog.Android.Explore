package com.cooper.wheellog.ble

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.PowerManager.WakeLock
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.utils.Constants
import com.cooper.wheellog.utils.NotificationUtil
import com.cooper.wheellog.utils.SomeUtil.playSound
import io.github.tritbool.euc.ble.core.BLEConstants
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask

/**
 * Thin foreground service that:
 *   1. Holds a WakeLock while the wheel is connected.
 *   2. Plays connect / disconnect / no-connection beep sounds.
 *   3. Stays in the foreground notification so Android doesn't kill the process.
 *
 * All BLE logic (scan, connect, decode, protocol handshake) lives in
 * [EucBleManager] / EucBleClient from the euc_ble_library.  This service
 * only reacts to connection-state broadcasts emitted by [EucBleManager].
 *
 * Lifecycle:
 *   - Started (and bound) by MainActivity when BLE is first needed.
 *   - Stays alive as long as it has at least one bound client.
 *   - Destroyed when MainActivity unbinds (app foreground lost) or on
 *     explicit stopService() call.
 */
class BleService : Service() {

    private val appConfig: AppConfig by inject()
    private val notifications: NotificationUtil by inject()

    private val binder = LocalBinder()

    private var mgr: PowerManager? = null
    private var wl: WakeLock? = null
    private val wakeLogTag = "WheelLog:BleServiceWakeLock"

    private var beepTimer: Timer? = null
    private var beepTicks = 0

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        mgr = getSystemService(POWER_SERVICE) as PowerManager
        Timber.i("BleService created")
    }

    override fun onBind(intent: Intent?): IBinder {
        startForeground(Constants.MAIN_NOTIFICATION_ID, notifications.notification)
        Timber.i("BleService bound")
        return binder
    }

    override fun onDestroy() {
        releaseWakeLock()
        stopBeepTimer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        Timber.i("BleService destroyed")
        super.onDestroy()
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        Timber.e("BleService timeout — stopping")
        stopSelf()
    }

    // ------------------------------------------------------------------
    // Public API (called by MainActivity via the Binder)
    // ------------------------------------------------------------------

    /** Call when a wheel connection is fully established (GATT + services). */
    fun onWheelConnected() {
        if (appConfig.connectionSound) {
            stopBeepTimer()          // stop the "no connection" beep if it was running
            acquireWakeLock(5 * 60 * 1000L)
            playSound(applicationContext, R.raw.sound_connect)
        }
        notifications.notificationMessageId = R.string.connected
        notifications.update()
    }

    /** Call when the wheel disconnects (user-requested or unexpected). */
    fun onWheelDisconnected(unexpected: Boolean) {
        if (appConfig.connectionSound) {
            playSound(applicationContext, R.raw.sound_disconnect)
            releaseWakeLock()
            val noConnectionSoundMs = appConfig.noConnectionSound * 1000
            if (unexpected && noConnectionSoundMs > 0) {
                startBeepTimer(noConnectionSoundMs.toLong())
            }
        }
        notifications.notificationMessageId = R.string.disconnected
        notifications.update()
    }

    /** Call while the service is scanning / connecting (CONNECTING state). */
    fun onWheelConnecting() {
        notifications.notificationMessageId = R.string.connecting
        notifications.update()
    }

    // ------------------------------------------------------------------
    // WakeLock helpers
    // ------------------------------------------------------------------

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        wl = mgr!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLogTag).apply {
            acquire(timeoutMs)
        }
    }

    private fun releaseWakeLock() {
        if (wl?.isHeld == true) wl?.release()
        wl = null
    }

    // ------------------------------------------------------------------
    // Beep timer (plays a sound every N seconds when disconnected)
    // ------------------------------------------------------------------

    private fun startBeepTimer(intervalMs: Long) {
        stopBeepTimer()
        acquireWakeLock(5 * 60 * 1000L)
        beepTicks = 0
        beepTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    beepTicks++
                    // Stop after 5 minutes total
                    if (beepTicks * intervalMs > 300_000L) {
                        stopBeepTimer()
                        return
                    }
                    playSound(applicationContext, R.raw.sound_no_connection)
                }
            }, intervalMs, intervalMs)
        }
    }

    private fun stopBeepTimer() {
        beepTimer?.cancel()
        beepTimer = null
        releaseWakeLock()
    }

    // ------------------------------------------------------------------
    // Binder
    // ------------------------------------------------------------------

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }
}
