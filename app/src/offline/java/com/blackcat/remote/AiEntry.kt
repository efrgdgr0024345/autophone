package com.blackcat.remote

import android.app.Activity
import android.widget.LinearLayout
import android.widget.Toast

/** Offline variant: no AI credentials or networking; Bluetooth V2 behavior is unchanged. */
object AiEntry {
    private fun unavailable(activity: Activity) {
        Toast.makeText(activity, "AI features are available in the CatAI build.", Toast.LENGTH_SHORT).show()
    }

    fun openAssistant(activity: Activity, manager: () -> HidManager) = unavailable(activity)
    fun openPhotoFeedback(activity: Activity, manager: () -> HidManager) = unavailable(activity)
    fun openTargetSystem(activity: Activity) = unavailable(activity)
    fun openSettings(activity: Activity) = unavailable(activity)

    fun attach(activity: Activity, row: LinearLayout, manager: () -> HidManager) {}
}
