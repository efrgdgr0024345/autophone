package com.blackcat.remote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Keeps an explicitly started Bluetooth input session foreground while Android system screens
 * cover the Activity. The Activity still owns the existing Bluetooth manager and final cleanup.
 * This service never pairs, reconnects, registers a profile, or sends input.
 */
class BluetoothSessionService : Service() {
    private var watching = false

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Bluetooth protected broadcasts may originate from the Bluetooth system UID.
                registerReceiver(stateReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(stateReceiver, filter)
            }
            watching = true
        } catch (_: SecurityException) {
            publish("FAIL Bluetooth session monitoring unavailable")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!watching) {
            publish("FAIL Bluetooth session monitoring unavailable")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!bluetoothReady(this)) {
            publish(if (hasConnectPermission(this)) "FAIL Bluetooth off" else "FAIL Bluetooth permission missing")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Bluetooth input session", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows when the Bluetooth keyboard and mouse session is active." })
            val open = Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_SETTINGS
                this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_cat)
                .setContentTitle("Black Cat Bluetooth session")
                .setContentText("Open Black Cat to use keyboard and mouse controls.")
                .setContentIntent(pending)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: RuntimeException) {
            // No exception text is retained; it can contain system/device details.
            publish("FAIL Bluetooth session could not start")
            stopSelf()
        }
        // Android must not restart an input session after process death without a user action.
        return START_NOT_STICKY
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                publish("FAIL Bluetooth off")
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (watching) {
            try { unregisterReceiver(stateReceiver) } catch (_: IllegalArgumentException) { }
            watching = false
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.blackcat.remote.OPEN_SETTINGS"
        private const val CHANNEL_ID = "blackcat_bluetooth_session"
        private const val NOTIFICATION_ID = 31
        private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
        private val mainHandler = Handler(Looper.getMainLooper())

        /** Call only from a visible setup action, after the needed Bluetooth grants. */
        fun start(context: Context): Boolean {
            if (!bluetoothReady(context)) return false
            return try {
                context.startForegroundService(Intent(context, BluetoothSessionService::class.java))
                true
            } catch (_: RuntimeException) {
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BluetoothSessionService::class.java))
        }

        internal fun addListener(listener: (String) -> Unit) { listeners.add(listener) }
        internal fun removeListener(listener: (String) -> Unit) { listeners.remove(listener) }

        private fun publish(event: String) {
            // A local callback avoids an exported status receiver accepting another app's events.
            val currentListeners = listeners.toList()
            mainHandler.post { currentListeners.forEach { it(event) } }
        }

        private fun hasConnectPermission(context: Context) = Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        private fun bluetoothReady(context: Context): Boolean {
            if (!hasConnectPermission(context)) return false
            return try {
                context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
            } catch (_: SecurityException) {
                false
            }
        }
    }
}
