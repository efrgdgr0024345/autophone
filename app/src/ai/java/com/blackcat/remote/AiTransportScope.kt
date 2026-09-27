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
 * Observes the EXISTING V2 connection only while the AI panel is open.
 * Does not initialise, register, pair, connect, disconnect or close a HID manager.
 * Normal V2 input never passes through this adapter.
 */
internal class AiTransportScope(
    private val activity: Activity,
    private val manager: HidManager,
    private val onLeave: () -> Unit
) : CommandTarget {
    private var watching = false
    private var active = true
    private var blocked = false
    private var epoch = 0L
    private var lastDevice: BluetoothDevice? = null

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothHidDevice.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // These protected Bluetooth system broadcasts can originate from the Bluetooth UID.
        // No application-supplied commands are accepted by this receiver.
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                activity.registerReceiver(receiver, filter)
            }
            watching = true
        } catch (_: SecurityException) {
            blocked = true // Fail closed for AI transmission, not for normal V2 controls.
        }
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
    }

    override fun current(): HostSession? {
        if (!active || !watching || blocked || !manager.registered) return null
        if (Build.VERSION.SDK_INT >= 31 && activity.checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter?.isEnabled != true) return null
            val device = manager.connected ?: return null
            if (lastDevice !== device) {
                lastDevice = device
                epoch++
            }
            val label = (device.name ?: "Bluetooth host").filter { it.code in 32..126 }.take(48)
            HostSession(device.address, epoch, label + " [" + device.address.takeLast(5) + "]")
        } catch (_: SecurityException) {
            null
        }
    }

    override fun sendKey(session: HostSession, modifier: Int, usage: Int): Boolean {
        if (current() != session) return false
        return manager.sendKeyboard(modifier, usage)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!active || intent == null) return
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state != BluetoothAdapter.STATE_ON) { epoch++; blocked = true }
                return
            }
            val device = remoteDevice(intent) ?: return
            val current = manager.connected ?: lastDevice
            if (current != null && device != current) return
            when (intent.action) {
                BluetoothHidDevice.ACTION_CONNECTION_STATE_CHANGED -> {
                    epoch++
                    blocked = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> { epoch++; blocked = true }
            }
        }
    }

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPaused(a: Activity) {
            if (a === activity && active) { active = false; epoch++; onLeave() }
        }
        override fun onActivityDestroyed(a: Activity) {
            if (a === activity) { onLeave(); close() }
        }
        override fun onActivityCreated(a: Activity, state: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityResumed(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}
    }

    fun close() {
        active = false
        epoch++
        activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
        if (watching) {
            try { activity.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }
            watching = false
        }
        // Critically: do not touch manager lifecycle here.
    }

    @Suppress("DEPRECATION")
    private fun remoteDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
