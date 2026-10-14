package com.blackcat.remote

/** Human-readable, content-free state for the normal Bluetooth setup screens. */
internal object BluetoothStatus {
    const val OFF = "Failed: Bluetooth is off. Turn it on to continue."
    const val PERMISSION_DENIED = "Bluetooth permission was denied. Tap Set up Bluetooth to try again."
    const val READY = "Ready to pair — add this phone on your computer."
    const val CONNECTED = "Connected — keyboard and mouse ready."
    const val SESSION_ENDED = "Disconnected. Bluetooth background session stopped."
    const val CONNECTED_SESSION_STOPPED = "Connected — background session stopped. Tap Set up Bluetooth to keep it active."

    fun message(event: String): String? = when {
        event == "FAIL Bluetooth off" -> OFF
        event == "FAIL Bluetooth unavailable" -> "Failed: this phone has no Bluetooth adapter."
        event.startsWith("FAIL Bluetooth permission") -> PERMISSION_DENIED
        event.startsWith("PASS HID_CONNECTED") -> CONNECTED
        event.startsWith("PASS HID_REGISTERED") -> READY
        event == "INFO HID_CONNECTING" -> "Connecting to the computer…"
        event == "INFO HID_DISCONNECTING" -> "Disconnecting from the computer…"
        event == "INFO HID_DISCONNECTED" -> "Disconnected. Connect again from the computer's Bluetooth settings."
        event == "FAIL HID unregistered" || event == "WARN HID_DEVICE profile disconnected" ->
            "Bluetooth input is not ready. Tap Set up Bluetooth to try again."
        event == "FAIL HID registerApp rejected" -> "Failed: this phone rejected Bluetooth keyboard and mouse setup."
        event.startsWith("FAIL HID profile:") || event.startsWith("FAIL HID registerApp:") ->
            "Failed: Bluetooth keyboard and mouse setup is unavailable. Try again."
        event.startsWith("FAIL Bluetooth session") ->
            "Failed: Android could not keep the Bluetooth session active. Tap Set up Bluetooth to try again."
        event.startsWith("FAIL discoverability:") -> "Failed: could not make this phone discoverable. Try again."
        event == "WARN discoverability declined" -> "Discoverability was cancelled. Tap Make Discoverable to try again."
        event == "WAIT HID not registered" -> "Bluetooth is not ready. Tap Set up Bluetooth first."
        else -> null
    }
}
