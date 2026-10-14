package com.blackcat.remote

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** User-initiated setup around the unchanged Activity-owned HID manager. */
internal class BluetoothSetupController(
    private val activity: Activity,
    private val manager: () -> HidManager,
    private val onStatus: (String) -> Unit,
    private val onEvent: (String) -> Unit
) {
    private var closed = false
    private var permissionPending = false
    private var enablePending = false
    private var profileRequested = false
    private var sessionEnded = false
    private val serviceListener: (String) -> Unit = { event ->
        if (!closed) onEvent(event)
    }

    init { BluetoothSessionService.addListener(serviceListener) }

    fun setup() {
        if (closed || permissionPending || enablePending) return
        sessionEnded = false
        val needed = missingPermissions()
        if (needed.isNotEmpty()) {
            permissionPending = true
            onStatus("Allow Nearby devices to use Bluetooth keyboard and mouse.")
            activity.requestPermissions(needed.toTypedArray(), PERMISSION_REQUEST)
            return
        }
        beginSetup()
    }

    /** A readiness check cannot pair the phone, make it discoverable, or send input. */
    fun readyForDiscoverability(): Boolean {
        if (closed) return false
        if (sessionEnded || missingPermissions().isNotEmpty() || !bluetoothEnabled() || !manager().registered) {
            setup()
            return false
        }
        if (!BluetoothSessionService.start(activity)) {
            report("FAIL Bluetooth session could not start")
            return false
        }
        return true
    }

    fun onPermissionResult(requestCode: Int, grants: IntArray): Boolean {
        if (requestCode != PERMISSION_REQUEST) return false
        if (closed || !permissionPending) return true
        permissionPending = false
        if (grants.isEmpty() || grants.any { it != PackageManager.PERMISSION_GRANTED } || missingPermissions().isNotEmpty()) {
            report("FAIL Bluetooth permission denied")
        } else {
            onEvent("PASS BLUETOOTH_PERMISSIONS")
            beginSetup()
        }
        return true
    }

    fun onActivityResult(requestCode: Int, result: Int): Boolean {
        if (requestCode != ENABLE_REQUEST && requestCode != SETTINGS_REQUEST) return false
        if (closed || !enablePending) return true
        enablePending = false
        if (missingPermissions().isEmpty() && bluetoothEnabled() && (requestCode == SETTINGS_REQUEST || result == Activity.RESULT_OK)) {
            beginSetup()
        } else {
            report("FAIL Bluetooth off")
        }
        return true
    }

    fun onResume() {
        if (closed) return
        // Only complete a setup the user explicitly started; opening/resuming the app is passive.
        if (enablePending && missingPermissions().isEmpty() && bluetoothEnabled()) {
            enablePending = false
            beginSetup()
            return
        }
        when {
            missingPermissions().isNotEmpty() -> onStatus("Bluetooth permission required. Tap Set up Bluetooth.")
            adapter() == null -> onStatus("Failed: this phone has no Bluetooth adapter.")
            !bluetoothEnabled() -> {
                profileRequested = false
                BluetoothSessionService.stop(activity)
                onStatus(BluetoothStatus.OFF)
            }
            sessionEnded -> onStatus(if (manager().registered && manager().connected != null)
                BluetoothStatus.CONNECTED_SESSION_STOPPED else BluetoothStatus.SESSION_ENDED)
            manager().registered && manager().connected != null -> onStatus(BluetoothStatus.CONNECTED)
            manager().registered -> onStatus(BluetoothStatus.READY)
            profileRequested -> onStatus("Setting up Bluetooth keyboard and mouse…")
            else -> onStatus("Bluetooth is not set up. Tap Set up Bluetooth.")
        }
    }

    fun observeEvent(event: String) {
        if (closed) return
        if (event == "FAIL Bluetooth off") {
            profileRequested = false
            BluetoothSessionService.stop(activity)
        } else if (event == "FAIL HID unregistered" || event == "WARN HID_DEVICE profile disconnected" ||
            event.startsWith("FAIL HID ") || event.startsWith("FAIL Bluetooth session")) {
            profileRequested = false
        }
        if (sessionEnded && (event.startsWith("PASS HID_CONNECTED") || event.startsWith("PASS HID_REGISTERED") ||
            event == "INFO HID_DISCONNECTED" || event == "FAIL HID unregistered")) {
            onStatus(if (event.startsWith("PASS HID_CONNECTED")) BluetoothStatus.CONNECTED_SESSION_STOPPED
                else BluetoothStatus.SESSION_ENDED)
        } else BluetoothStatus.message(event)?.let(onStatus)
    }

    private fun beginSetup() {
        if (closed) return
        if (missingPermissions().isNotEmpty()) { report("FAIL Bluetooth permission missing"); return }
        if (adapter() == null) { report("FAIL Bluetooth unavailable"); return }
        if (!bluetoothEnabled()) {
            report("FAIL Bluetooth off")
            Toast.makeText(activity, "Failed: Bluetooth is off", Toast.LENGTH_LONG).show()
            requestEnable()
            return
        }
        // Preserve the existing core ownership and init path; a separate service maintains the
        // process importance that Android requires for a registered Bluetooth HID application.
        if (!BluetoothSessionService.start(activity)) {
            report("FAIL Bluetooth session could not start")
            return
        }
        if (manager().registered) {
            onStatus(if (manager().connected != null) BluetoothStatus.CONNECTED else BluetoothStatus.READY)
            return
        }
        if (profileRequested) {
            onStatus("Setting up Bluetooth keyboard and mouse…")
            return
        }
        profileRequested = true
        onStatus("Setting up Bluetooth keyboard and mouse…")
        onEvent("INFO APP_START")
        val initiated = try {
            manager().init()
        } catch (_: SecurityException) {
            // Grants can be revoked after readiness checks but before the frozen core reads them.
            report("FAIL Bluetooth permission missing")
            false
        }
        if (!initiated) {
            profileRequested = false
            BluetoothSessionService.stop(activity)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestEnable() {
        if (closed || enablePending) return
        enablePending = true
        try {
            activity.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_REQUEST)
        } catch (_: ActivityNotFoundException) {
            openBluetoothSettings()
        } catch (_: SecurityException) {
            openBluetoothSettings()
        }
    }

    @Suppress("DEPRECATION")
    private fun openBluetoothSettings() {
        try {
            activity.startActivityForResult(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), SETTINGS_REQUEST)
        } catch (_: ActivityNotFoundException) {
            enablePending = false
            onStatus("Bluetooth is off. Turn it on in Android Settings, then tap Set up Bluetooth.")
        } catch (_: SecurityException) {
            enablePending = false
            onStatus("Bluetooth is off. Turn it on in Android Settings, then tap Set up Bluetooth.")
        }
    }

    private fun adapter(): BluetoothAdapter? = try {
        activity.getSystemService(BluetoothManager::class.java)?.adapter
    } catch (_: SecurityException) {
        null
    }

    private fun bluetoothEnabled(): Boolean = try {
        adapter()?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    private fun missingPermissions(): List<String> = if (Build.VERSION.SDK_INT < 31) emptyList() else
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE).filter {
            activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

    private fun report(event: String) {
        onEvent(event)
        // Also useful when the controller is embedded in a UI whose log handler is passive.
        BluetoothStatus.message(event)?.let(onStatus)
    }

    /** Stops only foreground-session support; MainActivity handles its existing disconnect action. */
    fun endSession() {
        if (closed) return
        sessionEnded = true
        permissionPending = false
        enablePending = false
        profileRequested = false
        BluetoothSessionService.stop(activity)
        onStatus(if (manager().registered && manager().connected != null)
            BluetoothStatus.CONNECTED_SESSION_STOPPED else BluetoothStatus.SESSION_ENDED)
    }

    fun close() {
        if (closed) return
        closed = true
        permissionPending = false
        enablePending = false
        BluetoothSessionService.removeListener(serviceListener)
        BluetoothSessionService.stop(activity)
    }

    private companion object {
        const val PERMISSION_REQUEST = 81
        const val ENABLE_REQUEST = 82
        const val SETTINGS_REQUEST = 83
    }
}
