package com.blackcat.remote

import android.app.Activity
import android.widget.LinearLayout
import android.widget.Toast

/** Offline variant: no AI UI, credentials or networking; V2 HID behaviour is unchanged. */
object AiEntry {
    fun attach(activity: Activity, row: LinearLayout, manager: () -> HidManager) {}
    fun openAssistant(activity: Activity, manager: () -> HidManager, onClosed: () -> Unit = {}) {
        Toast.makeText(activity, "AI Assistant is not included in the offline build.", Toast.LENGTH_LONG).show()
        onClosed()
    }
    fun openPhoto(activity: Activity, manager: () -> HidManager, onClosed: () -> Unit = {}) {
        Toast.makeText(activity, "Photo Feedback is not included in the offline build.", Toast.LENGTH_LONG).show()
        onClosed()
    }
    fun openSettings(activity: Activity, onClosed: () -> Unit = {}) {
        Toast.makeText(activity, "OpenAI settings are not included in the offline build.", Toast.LENGTH_LONG).show()
        onClosed()
    }
}
