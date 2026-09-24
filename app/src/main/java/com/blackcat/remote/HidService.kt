/* Derived from Linkpad by Devdas Kumar / Devdas-gupta (MIT). */
package com.blackcat.remote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HidService : Service() {

    val reportSender = HidReportSender()

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isForeground = false
    @Volatile
    private var lastNotificationText = ""

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _appRegistered = MutableStateFlow(false)
    val appRegistered: StateFlow<Boolean> = _appRegistered.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    @Volatile private var hidDevice: BluetoothHidDevice? = null
    @Volatile private var pendingTarget: BluetoothDevice? = null

    // BUG 2 — Auto-reconnect: track last connected address and whether disconnect was user-initiated
    @Volatile private var lastConnectedAddress: String? = null
    @Volatile private var userInitiatedDisconnect = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    private val executor = Executors.newSingleThreadExecutor()

    private var btStateReceiverRegistered = false

    @android.annotation.SuppressLint("MissingPermission")
    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_ON -> {
                            Log.i(TAG, "BT STATE_ON — registering HID proxy")
                            if (hidDevice == null) registerHidProxy()
                        }
                        BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                            _appRegistered.value = false
                            _connectionState.value = ConnectionState.Disconnected
                            reportSender.attach(null, null)
                            hidDevice = null
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device ?: return
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                    Log.i(TAG, "Bond state changed for ${device.address}: prev=$prevBondState new=$bondState")

                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        val state = _connectionState.value
                        if (state is ConnectionState.Connecting && state.device.address == device.address) {
                            Log.i(TAG, "Device bonded successfully. Connecting HID...")
                            val proxy = hidDevice
                            if (proxy != null && _appRegistered.value && hasBluetoothConnectPermission()) {
                                try {
                                    proxy.connect(device)
                                } catch (t: Throwable) {
                                    _connectionState.value = ConnectionState.Error("connect post-bond: ${t.message}")
                                }
                            }
                        }
                    } else if (bondState == BluetoothDevice.BOND_NONE && prevBondState == BluetoothDevice.BOND_BONDING) {
                        val state = _connectionState.value
                        if (state is ConnectionState.Connecting && state.device.address == device.address) {
                            _connectionState.value = ConnectionState.Disconnected
                        }
                    }
                }
                // BUG 6 — ACTION_ACL_DISCONNECTED for immediate disconnect detection
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device ?: return
                    val connState = _connectionState.value
                    if (connState is ConnectionState.Connected && connState.device.address == device.address) {
                        Log.i(TAG, "ACL_DISCONNECTED from ${device.address} — immediate disconnect")
                        _connectionState.value = ConnectionState.Disconnected
                        reportSender.attach(null, null)
                        updateNotification("Disconnected")
                        // BUG 2 — Trigger auto-reconnect if not user-initiated
                        if (!userInitiatedDisconnect) {
                            scheduleAutoReconnect(device)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager
        bluetoothAdapter = manager?.adapter
        createNotificationChannel()
        if (!hasBluetoothConnectPermission()) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted; stopping HidService")
            stopSelf()
            return
        }

        lastNotificationText = "Disconnected"

        startForegroundServiceNow()

        registerBtStateReceiver()
        if (bluetoothAdapter?.isEnabled == true) {
            registerHidProxy()
        } else {
            Log.i(TAG, "Bluetooth off — waiting for STATE_ON")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasBluetoothConnectPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    @android.annotation.SuppressLint("MissingPermission")
    override fun onDestroy() {
        serviceScope.cancel()
        try {
            hidDevice?.unregisterApp()
        } catch (t: Throwable) {
            Log.w(TAG, "unregisterApp: ${t.message}")
        }
        try {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (t: Throwable) {
            Log.w(TAG, "closeProfileProxy: ${t.message}")
        }
        if (btStateReceiverRegistered) {
            try { unregisterReceiver(btStateReceiver) } catch (_: Throwable) {}
            btStateReceiverRegistered = false
        }
        hidDevice = null
        executor.shutdownNow()
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        fun getService(): HidService = this@HidService
    }

    // BUG 1 — Connection timeout: wrap connect in withTimeout(15s)
    @android.annotation.SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothConnectPermission()) {
            _connectionState.value = ConnectionState.Error("Missing BLUETOOTH_CONNECT permission")
            return
        }
        userInitiatedDisconnect = false
        reconnectAttempts = 0
        val bond = device.bondState
        if (bond != BluetoothDevice.BOND_BONDED) {
            ensureDiscoverable()
        }
        val proxy = hidDevice
        if (proxy == null) {
            pendingTarget = device
            _connectionState.value = ConnectionState.Connecting(device)
            if (bluetoothAdapter?.isEnabled == true) registerHidProxy()
            return
        }
        if (!_appRegistered.value) {
            pendingTarget = device
            _connectionState.value = ConnectionState.Connecting(device)
            return
        }
        serviceScope.launch {
            try {
                Log.i(TAG, "connectToDevice: addr=${device.address} bond=$bond")
                _connectionState.value = ConnectionState.Connecting(device)
                if (bond != BluetoothDevice.BOND_BONDED) {
                    Log.i(TAG, "Device not bonded. Creating bond first...")
                    val success = device.createBond()
                    if (!success) {
                        _connectionState.value = ConnectionState.Error("createBond returned false")
                    }
                } else {
                    // Fire connect — result arrives via onConnectionStateChanged callback (async)
                    proxy.connect(device)
                    // FIX: Watch state flow for 15s; if still Connecting, surface a timeout error
                    // (withTimeout around proxy.connect is wrong — it's fire-and-forget)
                    serviceScope.launch {
                        delay(15_000L)
                        val cur = _connectionState.value
                        if (cur is ConnectionState.Connecting && cur.device.address == device.address) {
                            Log.w(TAG, "Connection timed out for ${device.address}")
                            _connectionState.value = ConnectionState.Error("Connection timed out")
                        }
                    }
                }
            } catch (t: Throwable) {
                _connectionState.value = ConnectionState.Error("connect: ${t.message}")
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun disconnectCurrent() {
        val proxy = hidDevice ?: return
        val state = _connectionState.value
        if (state is ConnectionState.Connected) {
            userInitiatedDisconnect = true
            try {
                proxy.disconnect(state.device)
            } catch (t: Throwable) {
                Log.w(TAG, "disconnect: ${t.message}")
            }
        }
    }

    // BUG 2 — Auto-reconnect with exponential backoff
    private fun scheduleAutoReconnect(device: BluetoothDevice) {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.i(TAG, "Max reconnect attempts reached for ${device.address}")
            return
        }
        val delayMs = when (reconnectAttempts) {
            0 -> 3_000L
            1 -> 6_000L
            2 -> 12_000L
            3 -> 24_000L
            else -> 48_000L
        }
        reconnectAttempts++
        Log.i(TAG, "Scheduling auto-reconnect attempt $reconnectAttempts in ${delayMs}ms")
        serviceScope.launch {
            delay(delayMs)
            if (_connectionState.value !is ConnectionState.Connected && !userInitiatedDisconnect) {
                Log.i(TAG, "Auto-reconnecting to ${device.address}")
                connectToDevice(device)
            }
        }
    }

    private fun registerBtStateReceiver() {
        if (btStateReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            // BUG 6 — Listen for immediate ACL disconnect
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(btStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(btStateReceiver, filter)
        }
        btStateReceiverRegistered = true
    }

    private fun ensureDiscoverable() {
        val adapter = bluetoothAdapter ?: return
        try {
            if (adapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ensureDiscoverable: ${t.message}")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun registerHidProxy() {
        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = ConnectionState.Error("Bluetooth not available")
            return
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "registerHidProxy: adapter disabled")
            return
        }
        if (!hasBluetoothConnectPermission()) {
            _connectionState.value = ConnectionState.Error("Missing BLUETOOTH_CONNECT permission")
            return
        }
        try {
            adapter.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (t: Throwable) {
            _connectionState.value = ConnectionState.Error("Profile proxy failed: ${t.message}")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as? BluetoothHidDevice ?: return
            hidDevice = hid
            registerApp(hid)
        }

        // BUG 26 — Reset state and attempt rebind on service disconnected
        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _appRegistered.value = false
                _connectionState.value = ConnectionState.Disconnected
                reportSender.attach(null, null)
                // Attempt to rebind
                serviceScope.launch {
                    delay(2_000L)
                    Log.i(TAG, "Attempting to rebind HID profile after service disconnect")
                    if (bluetoothAdapter?.isEnabled == true) registerHidProxy()
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun registerApp(proxy: BluetoothHidDevice) {
        // BUG 21 fix: BluetoothHidDeviceAppSdpSettings takes exactly 5 params
        // (name, description, provider, subclass, descriptor) — no COUNTRY arg in Android API
        val sdp = BluetoothHidDeviceAppSdpSettings(
            HidDescriptors.SDP_NAME,
            HidDescriptors.SDP_DESCRIPTION,
            HidDescriptors.SDP_PROVIDER,
            HidDescriptors.SUBCLASS,
            HidDescriptors.COMBINED_DESCRIPTOR
        )
        // QoS values match the commercial Bluetooth Keyboard & Mouse APK known to work
        // on Pixel/Samsung. Null QoS works on AOSP samples but some skinned Android
        // builds drop sendReport silently without populated QoS.
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            /* tokenRate */ 6000,
            /* tokenBucketSize */ 1024,
            /* peakBandwidth */ 10000,
            /* latency */ 11250,
            /* delayVariation */ 11250
        )
        try {
            proxy.registerApp(sdp, qos, qos, executor, callback)
        } catch (t: Throwable) {
            Log.e(TAG, "registerApp failed: ${t.message}", t)
            _connectionState.value = ConnectionState.Error("registerApp: ${t.message}")
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.i(TAG, "onAppStatusChanged: registered=$registered plugged=${pluggedDevice?.address}")
            _appRegistered.value = registered
            if (!registered) {
                reportSender.attach(null, null)
                return
            }
            // Prefer plugged device from system, fall back to pending target, then last device
            // BUG 3 — Only clear pendingTarget after connect succeeds (in onConnectionStateChanged)
            val target = pluggedDevice ?: pendingTarget ?: run {
                // BUG 11 — Try auto-connect to last saved device
                val lastAddr = lastConnectedAddress
                if (!lastAddr.isNullOrBlank()) {
                    try { bluetoothAdapter?.getRemoteDevice(lastAddr) } catch (_: Throwable) { null }
                } else null
            }
            if (target != null) {
                val proxy = hidDevice
                if (proxy != null) {
                    val curState = try { proxy.getConnectionState(target) } catch (_: Throwable) { BluetoothProfile.STATE_DISCONNECTED }
                    Log.i(TAG, "post-register state=$curState for ${target.address}")
                    if (curState == BluetoothProfile.STATE_CONNECTED) {
                        lastNotificationText = "Connected: ${safeName(target)}"
                        _connectionState.value = ConnectionState.Connected(target)
                        reportSender.attach(proxy, target)
                        updateNotification(lastNotificationText)
                        // BUG 3 — Only null pendingTarget after confirmed connected
                        if (pendingTarget?.address == target.address) pendingTarget = null
                    } else if (curState != BluetoothProfile.STATE_CONNECTING) {
                        try {
                            proxy.connect(target)
                        } catch (t: Throwable) {
                            _connectionState.value = ConnectionState.Error("connect: ${t.message}")
                            // BUG 3 — Don't null pendingTarget here; leave for retry
                        }
                    }
                }
                // Don't null pendingTarget unconditionally here (BUG 3 fix)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            device ?: return
            Log.i(TAG, "onConnectionStateChanged: ${device.address} state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    lastNotificationText = "Connected: ${safeName(device)}"
                    _connectionState.value = ConnectionState.Connected(device)
                    reportSender.attach(hidDevice, device)
                    updateNotification(lastNotificationText)
                    reconnectAttempts = 0
                    // BUG 3 — Null pendingTarget only after confirmed STATE_CONNECTED
                    if (pendingTarget?.address == device.address) pendingTarget = null}
                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = ConnectionState.Connecting(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    lastNotificationText = "Disconnected"
                    if (_connectionState.value is ConnectionState.Connected ||
                        _connectionState.value is ConnectionState.Connecting) {
                        _connectionState.value = ConnectionState.Disconnected
                        // BUG 2 — Auto-reconnect if not user-initiated
                        if (!userInitiatedDisconnect) {
                            scheduleAutoReconnect(device)
                        }
                    }
                    reportSender.attach(null, null)
                    updateNotification(lastNotificationText)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            // BUG 5 — Return ERROR_RSP_INVALID_RPT_ID for unknown report IDs
            val size = when (id) {
                HidDescriptors.REPORT_ID_MOUSE -> 5
                HidDescriptors.REPORT_ID_KEYBOARD -> 8
                HidDescriptors.REPORT_ID_CONSUMER -> 2
                else -> {
                    try {
                        hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                    } catch (t: Throwable) {
                        Log.w(TAG, "reportError: ${t.message}")
                    }
                    return
                }
            }
            try {
                hidDevice?.replyReport(device, type, id, ByteArray(size))
            } catch (t: Throwable) {
                Log.w(TAG, "replyReport: ${t.message}")
            }
        }

        // BUG 4 — Acknowledge onSetReport to prevent Windows HID stall
        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            try {
                hidDevice?.replyReport(device, type, id, data ?: ByteArray(0))
            } catch (t: Throwable) {
                Log.w(TAG, "onSetReport replyReport: ${t.message}")
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            super.onVirtualCableUnplug(device)
            Log.i(TAG, "onVirtualCableUnplug: ${device?.address}")
            lastNotificationText = "Disconnected"
            _connectionState.value = ConnectionState.Disconnected
            reportSender.attach(null, null)
            updateNotification(lastNotificationText)
        }
    }

    private fun safeName(device: BluetoothDevice): String = runCatching { device.name }.getOrNull() ?: "(device)"
    private fun startForegroundServiceNow() {
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, buildNotification(lastNotificationText))
            isForeground = true
        }
    }
    private fun updateNotification(text: String) {
        lastNotificationText = text
        if (isForeground) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(text))
    }
    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Black Cat Remote").setContentText(text).setOngoing(true).build()
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bluetooth HID", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    companion object {
        private const val TAG = "HidService"
        private const val CHANNEL_ID = "blackcat_hid"
        private const val NOTIFICATION_ID = 0xB7E
    }
}
