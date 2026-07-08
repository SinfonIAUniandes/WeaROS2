package com.jros2.wearos2.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jros2.wearos2.R
import com.jros2.wearos2.presentation.MainActivity
import com.jros2.wearos2.ros.RosBridgeHolder

/**
 * Foreground service that owns the running ROS 2 bridge so it keeps publishing while the
 * app is minimized / the screen is off. It is tied to the app's task: swiping the app away
 * (closing it) triggers [onTaskRemoved], which stops the bridge and the service, so the
 * bridge does NOT outlive the app.
 *
 * The bridge itself is the process singleton from [RosBridgeHolder], so the Activity UI and
 * this service drive the exact same instance. This service only manages the foreground
 * lifecycle, the Wi-Fi multicast lock (for DDS discovery) and a partial wake lock (so the
 * CPU keeps servicing sensor callbacks with the screen off).
 */
class BridgeService : Service() {

    private val bridge by lazy { RosBridgeHolder.get(this) }
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        // ACTION_START, or a null intent from a system-initiated restart: (re)start.
        startForegroundBridge()
        return START_STICKY
    }

    private fun startForegroundBridge() {
        acquireLocks()
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), foregroundServiceType())
        bridge.start()
    }

    private fun stopEverything() {
        bridge.stop()
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** User swiped the app away — close means the bridge dies with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopEverything()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Defensive: make sure nothing is left running if we are torn down another way.
        bridge.stop()
        releaseLocks()
        super.onDestroy()
    }

    private fun acquireLocks() {
        if (multicastLock == null) {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("wear_ros2_multicast_lock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wakeLock == null) {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WeaROS2:bridge").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /**
     * The foreground types we actually use, restricted to what the user has granted so
     * `startForeground` never throws for a type we can't back. `connectedDevice` is always
     * included (the bridge talks to a robot over the network).
     */
    private fun foregroundServiceType(): Int {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (granted(Manifest.permission.RECORD_AUDIO)) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        val hasHealthBacking = granted(Manifest.permission.BODY_SENSORS) ||
            granted(Manifest.permission.ACTIVITY_RECOGNITION) ||
            granted("android.permission.health.READ_HEART_RATE")
        if (Build.VERSION.SDK_INT >= 34 && hasHealthBacking) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }
        return type
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ROS 2 bridge",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Keeps the sensor bridge running in the background" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WeaROS2 bridge running")
            .setContentText("Publishing sensors to ROS 2")
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "com.jros2.wearos2.action.START"
        const val ACTION_STOP = "com.jros2.wearos2.action.STOP"
        private const val CHANNEL_ID = "ros_bridge_service"
        private const val NOTIF_ID = 1001

        fun startIntent(context: Context): Intent =
            Intent(context, BridgeService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, BridgeService::class.java).setAction(ACTION_STOP)
    }
}
