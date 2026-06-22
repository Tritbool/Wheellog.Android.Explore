package com.cooper.wheellog.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cooper.wheellog.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class NotificationUtil(private val context: Context): KoinComponent {
    private val appConfig: AppConfig by inject()
    private val builder: NotificationCompat.Builder
    private var kostilTimer: Timer? = null
    private var customText = ""
    private var buildIsSucceed = false
    var notificationMessageId = R.string.disconnected
    var notification: Notification? = null
        private set
    var alarmText: String = ""

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID_NOTIFICATION,
                Constants.notificationChannelName,
                NotificationManager.IMPORTANCE_MIN).apply {
            description = Constants.notificationChannelDescription
        }
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        with(NotificationManagerCompat.from(context)) {
            createNotificationChannel(channel)
        }
    }


    @SuppressLint("MissingPermission")
    fun update() {
        notification = null //build()
        if (buildIsSucceed) {
            with(NotificationManagerCompat.from(context)) {
                notify(Constants.MAIN_NOTIFICATION_ID, notification!!)
            }
        }
    }

    fun setCustomTitle(text: String) {
        customText = text
        update()
    }

    fun close() {
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                deleteNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID_NOTIFICATION)
            }
            cancelAll()
        }
        kostilTimer?.cancel()
        kostilTimer = null
    }

    // Fix Me
    // https://github.com/Wheellog/Wheellog.Android/pull/249
    fun updateKostilTimer() {

            kostilTimer?.cancel()
            kostilTimer = null

    }

    init {
        createNotificationChannel()
        builder = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID_NOTIFICATION)
        updateKostilTimer()
// for test
//        Timer().scheduleAtFixedRate(object : TimerTask() {
//            override fun run() {
//                val wd = WheelData.getInstance() ?: return
//                wd.batteryLevel = ((Math.random() * 100).toInt())
//                wd.temperature = (Math.random() * 10000).toInt()
//                wd.totalDistance = (Math.random() * 10000).toLong()
//                wd.speed = (Math.random() * 5000).toInt()
//                update()
//                val intent = Intent(Constants.ACTION_WHEEL_DATA_AVAILABLE)
//                context.sendBroadcast(intent)
//            }
//        }, 1000, 1000)
    }
}