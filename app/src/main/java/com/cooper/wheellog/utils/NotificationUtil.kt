package com.cooper.wheellog.utils

import com.cooper.wheellog.ble.BleSessionViewModel

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
import io.github.tritbool.euc.ble.core.BLEConstants
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class NotificationUtil(private val context: Context) : KoinComponent {
    private val appConfig: AppConfig by inject()
    private val viewModel: BleSessionViewModel by inject()
    private val builder: NotificationCompat.Builder
    private var kostilTimer: Timer? = null
    private var customText = ""
    private var buildIsSucceed = false
    var notificationMessageId = R.string.disconnected
    var notification: Notification? = null
        private set
    var alarmText: String = ""

    init {
        builder = NotificationCompat.Builder(
            context,
            Constants.NO********ION_CHANNEL_ID_NO********ION
        )
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            Constants.NO********ION_CHANNEL_ID_NO********ION,
            Constants.notificationChannelName,
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = Constants.notificationChannelDescription
        }
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        with(NotificationManagerCompat.from(context)) {
            createNotificationChannel(channel)
        }
    }

    private fun build(): Notification {
        buildIsSucceed = false
        val notificationIntent = Intent(context, MainActivity::class.java)
        val notificationView = RemoteViews(context.packageName, R.layout.notification_base)
        val buttonSettings = appConfig.notificationButtons
        val intentFlag = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(context, 0, notificationIntent, intentFlag)

        notificationView.setViewVisibility(
            R.id.ib_actions_layout,
            if (buttonSettings.any()) View.VISIBLE
            else View.GONE
        )

        arrayOf(
            Triple(
                R.id.ib_connection,
                R.string.icon_connection,
                Constants.NO********ION_BUTTON_CONNECTION
            ),
            Triple(R.id.ib_logging, R.string.icon_logging, Constants.NO********ION_BUTTON_LOGGING),
            Triple(R.id.ib_watch, R.string.icon_watch, Constants.NO********ION_BUTTON_WATCH),
            Triple(R.id.ib_beep, R.string.icon_beep, Constants.NO********ION_BUTTON_BEEP),
            Triple(R.id.ib_light, R.string.icon_light, Constants.NO********ION_BUTTON_LIGHT),
        ).forEach {
            notificationView.setViewVisibility(
                it.first,
                if (buttonSettings.contains(context.getString(it.second))) View.VISIBLE
                else View.GONE
            )
            notificationView.setOnClickPendingIntent(
                it.first,
                PendingIntent.getBroadcast(context, 0, Intent(it.third), intentFlag)
            )
        }
        
        val connectionState = viewModel.sessionState.value.connectionState
        val batteryLevel = viewModel.batteryLevel
        val temperature = viewModel.temperature
        val distance = viewModel.distanceDouble
        val speed = viewModel.speedDouble
        val title = customText.ifEmpty { context.getString(notificationMessageId) }
        val titleRide = viewModel.rideTimeString

        notificationView.setTextViewText(R.id.text_title, context.getString(R.string.app_name))
        notificationView.setTextViewText(
            R.id.ib_actions_text,
            context.getString(R.string.notifications_actions_text)
        )
        if (connectionState == BLEConstants.ConnectionState.CONNECTED || distance + temperature + batteryLevel + speed > 0) {
            val template = when (appConfig.appTheme) {
                R.style.AJDMTheme -> R.string.notification_text_ajdm_theme
                else -> R.string.notification_text
            }
            notificationView.setTextViewText(
                R.id.text_message,
                context.getString(template, speed, batteryLevel, temperature, distance)
            )
            notificationView.setTextViewText(R.id.text_title, "$title - $titleRide")
        } else {
            notificationView.setTextViewText(R.id.text_title, title)
        }
        
        if (appConfig.appTheme == R.style.AJDMTheme) {
            notificationView.setImageViewResource(R.id.icon, R.drawable.ajdm_notification_icon)
            notificationView.setInt(
                R.id.status_bar_latest_event_content,
                "setBackgroundResource",
                R.color.ajdm_background
            )
            val textColor = Color.BLACK
            notificationView.setTextColor(R.id.text_title, textColor)
            notificationView.setTextColor(R.id.text_message, textColor)
            notificationView.setTextColor(R.id.ib_actions_text, textColor)
        }
        notificationView.setImageViewResource(
            R.id.ib_connection,
            when (connectionState) {
                BLEConstants.ConnectionState.CONNECTING -> ThemeManager.getId(ThemeIconEnum.NotificationConnecting)
                BLEConstants.ConnectionState.CONNECTED -> ThemeManager.getId(ThemeIconEnum.NotificationConnected)
                else -> ThemeManager.getId(ThemeIconEnum.NotificationDisconnected)
            }
        )
        notificationView.setImageViewResource(
            R.id.ib_logging,
            if (LoggingService.isInstanceCreated()) ThemeManager.getId(ThemeIconEnum.NotificationLogOn)
            else ThemeManager.getId(ThemeIconEnum.NotificationLogOff)
        )
        notificationView.setImageViewResource(
            R.id.ib_watch,
            ThemeManager.getId(ThemeIconEnum.NotificationWatchOff)
        )
        notificationView.setImageViewResource(
            R.id.ib_beep,
            ThemeManager.getId(ThemeIconEnum.NotificationHorn)
        )
        notificationView.setImageViewResource(
            R.id.ib_light,
            ThemeManager.getId(ThemeIconEnum.NotificationLight)
        )

        builder.setSmallIcon(ThemeManager.getId(ThemeIconEnum.NotificationIcon))
            .setContentIntent(pendingIntent)
            .setContent(notificationView)
            .setCustomBigContentView(notificationView)
            .setChannelId(Constants.NO********ION_CHANNEL_ID_NO********ION)
            .setOngoing(true)
            .priority = NotificationCompat.PRIORITY_MIN

        builder.setContentTitle(
            if (connectionState == BLEConstants.ConnectionState.CONNECTED && distance + temperature + batteryLevel + speed > 0)
                titleRide
            else
                title
        )

        buildIsSucceed = true
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun update() {
        notification = build()
        if (buildIsSucceed) {
            with(NotificationManagerCompat.from(context)) {
                notify(Constants.MAIN_NO********ION_ID, notification!!)
            }
        }
    }

    fun setCustomTitle(text: String) {
        customText = text
        update()
    }

    fun close() {
