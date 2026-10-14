package com.blackcat.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothStatusTest {
    @Test fun bluetoothOffIsAnActionableFailure() {
        assertEquals(BluetoothStatus.OFF, BluetoothStatus.message("FAIL Bluetooth off"))
    }

    @Test fun permissionDenialDoesNotSuggestTheRadioIsBroken() {
        assertEquals(BluetoothStatus.PERMISSION_DENIED, BluetoothStatus.message("FAIL Bluetooth permission denied"))
    }

    @Test fun disconnectedCannotBeMistakenForConnected() {
        val message = BluetoothStatus.message("INFO HID_DISCONNECTED")!!
        assertFalse(message.contains("ready", ignoreCase = true))
        assertEquals("Disconnected. Connect again from the computer's Bluetooth settings.", message)
    }

    @Test fun registrationLossRemovesReadyStatus() {
        for (event in listOf("FAIL HID unregistered", "WARN HID_DEVICE profile disconnected")) {
            assertEquals("Bluetooth input is not ready. Tap Set up Bluetooth to try again.", BluetoothStatus.message(event))
        }
    }

    @Test fun actualConnectedEventShowsInputReadiness() {
        assertEquals(BluetoothStatus.CONNECTED, BluetoothStatus.message("PASS HID_CONNECTED — READY"))
    }

    @Test fun registrationMeansReadyToPairOnly() {
        assertEquals(BluetoothStatus.READY, BluetoothStatus.message("PASS HID_REGISTERED — ready for computer-side pairing"))
    }

    @Test fun deviceDetailsInAnExceptionDoNotEnterNormalStatus() {
        assertEquals("Failed: Bluetooth keyboard and mouse setup is unavailable. Try again.",
            BluetoothStatus.message("FAIL HID profile: private system details"))
    }

    @Test fun contentAndUnrelatedEventsNeverBecomeStatus() {
        assertNull(BluetoothStatus.message("PASS text submitted chars=8 unsupported=0 content-not-logged"))
        assertNull(BluetoothStatus.message("INFO APP_START"))
        assertNull(BluetoothStatus.message("INFO Bluetooth session stopped"))
    }
}
