package com.blackcat.remote

import android.Manifest
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

/**
 * Read-only adapter over the already-registered HID manager.
 *
 * It observes connection changes so a one-use approval cannot silently continue after the host,
 * Bluetooth state, or foreground activity changes. It never registers, pairs, connects,
 * disconnects, or closes the HID manager.
 */
internal class LibraryHidTarget(
    private val activity: Activity,
    private val manager: HidManager,
    private val onConnectionChanged: () -> Unit,
    private val onLeave: () -> Unit
) : CommandTarget {
    private var active = true
    private var watching = false
    private var blocked = false
    private var epoch = 0L
    private var lastDevice: BluetoothDevice? = null

    fun start() {
        if (!active || watching) return
        val filter = IntentFilter().apply {
            addAction(BluetoothHidDevice.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                activity.registerReceiver(receiver, filter)
            }
            watching = true
        } catch (_: SecurityException) {
            blocked = true
        }
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
    }

    override fun current(): HostSession? {
        if (!active || !watching || blocked || !manager.registered) return null
        if (Build.VERSION.SDK_INT >= 31 &&
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return null

        return try {
            val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter?.isEnabled != true) return null
            val device = manager.connected ?: return null
            if (lastDevice !== device) {
                lastDevice = device
                epoch++
            }
            val safeName = (device.name ?: "Bluetooth host")
                .filter { it.code in 32..126 }
                .take(48)
                .ifBlank { "Bluetooth host" }
            HostSession(
                id = device.address,
                epoch = epoch,
                label = "$safeName [${device.address.takeLast(5)}]"
            )
        } catch (_: SecurityException) {
            null
        }
    }

    override fun sendKey(session: HostSession, modifier: Int, usage: Int): Boolean {
        if (current() != session) return false
        return manager.sendKeyboard(modifier, usage)
    }

    fun close() {
        if (!active && !watching) return
        active = false
        epoch++
        activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
        if (watching) {
            try {
                activity.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered by Android or another close path.
            }
            watching = false
        }
    }

    private fun invalidateConnection() {
        epoch++
        blocked = true
        onConnectionChanged()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!active || intent == null) return
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    if (state != BluetoothAdapter.STATE_ON) invalidateConnection()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = remoteDevice(intent) ?: return
                    if (lastDevice == null || device == lastDevice) invalidateConnection()
                }
                BluetoothHidDevice.ACTION_CONNECTION_STATE_CHANGED -> {
                    val device = remoteDevice(intent) ?: return
                    if (lastDevice != null && device != lastDevice) return
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    epoch++
                    blocked = state != BluetoothProfile.STATE_CONNECTED
                    if (!blocked) lastDevice = device
                    onConnectionChanged()
                }
            }
        }
    }

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPaused(candidate: Activity) {
            if (candidate === activity && active) {
                active = false
                epoch++
                onLeave()
            }
        }

        override fun onActivityDestroyed(candidate: Activity) {
            if (candidate === activity) close()
        }

        override fun onActivityCreated(activity: Activity, state: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    }

    @Suppress("DEPRECATION")
    private fun remoteDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
