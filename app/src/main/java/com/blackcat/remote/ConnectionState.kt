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

/* Device display names are intentionally resolved only by permission-aware UI/service code. */
