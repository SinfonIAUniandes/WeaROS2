package com.jros2.wearos2.ros.sensors

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jros2.wearos2.ros.BaseWearSensor
import com.jros2.wearos2.ros.reliableQos
import std_msgs.String as RosString
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Subscription
import us.ihmc.jros2.ROS2SubscriptionCallback
import us.ihmc.jros2.ROS2Topic

/**
 * Subscribes to a [std_msgs/String][RosString] topic and pops up an Android
 * notification for every message received. The published string becomes the
 * notification text; an empty payload falls back to a generic message.
 *
 * Trigger it from a ROS 2 machine with, e.g.:
 *   ros2 topic pub --once /watch/notify std_msgs/String "{data: 'Hello watch'}"
 */
class NotificationSensor : BaseWearSensor("notification", "Notify", "notify", "Waiting") {
    private var subscription: ROS2Subscription<RosString>? = null

    @Volatile
    private var notificationId = 1

    override fun start(node: ROS2Node, context: Context) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        _displayValue.value = "Listening"

        val callback = ROS2SubscriptionCallback<RosString> { reader ->
            try {
                val received = reader.read() ?: return@ROS2SubscriptionCallback
                if (!enabled.value) return@ROS2SubscriptionCallback
                val text = received.dataAsString.ifBlank { "Notification from ROS 2" }
                showNotification(appContext, text)
                _messageCount.value++
                _displayValue.value = text.take(20)
            } catch (t: Throwable) {
                _displayValue.value = "Err: ${t.javaClass.simpleName}"
            }
        }
        subscription = node.createSubscription(
            ROS2Topic(resolvedTopicName, RosString::class.java),
            callback,
            reliableQos()
        )
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ROS 2 Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pop-up notifications triggered from a ROS 2 topic"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun showNotification(context: Context, text: String) {
        // On API 33+ posting is a no-op without POST_NOTIFICATIONS; skip quietly.
        // Pre-33 doesn't have this runtime permission, so notifications are always allowed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _displayValue.value = "No notif permission"
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("WeaROS2")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        // Unique id per message so each one pops up instead of replacing the last.
        NotificationManagerCompat.from(context).notify(notificationId++, notification)
    }

    override fun stop() {
        subscription = null
        _displayValue.value = "Waiting"
    }

    private companion object {
        const val CHANNEL_ID = "wearos2_ros_notifications"
    }
}
