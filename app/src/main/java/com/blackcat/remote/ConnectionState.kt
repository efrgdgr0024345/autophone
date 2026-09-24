/* Derived from Linkpad by Devdas Kumar / Devdas-gupta (MIT). */
package com.blackcat.remote

import android.bluetooth.BluetoothDevice

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Idle : ConnectionState()
    data class Connecting(val device: BluetoothDevice) : ConnectionState()
    data class Connected(val device: BluetoothDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

val ConnectionState.isConnected: Boolean
    get() = this is ConnectionState.Connected

val ConnectionState.deviceName: String?
    get() = when (this) {
        is ConnectionState.Connected -> runCatching { device.name }.getOrNull()
        is ConnectionState.Connecting -> runCatching { device.name }.getOrNull()
        else -> null
    }
