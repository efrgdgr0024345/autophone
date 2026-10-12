package com.blackcat.remote

import android.app.Activity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/** Offline variant: local Linux reference only; no AI credentials or networking. */
object AiEntry {
    fun openAssistant(activity: Activity, manager: () -> HidManager) {
        try {
            LinuxCommandLibraryPanel(activity, manager()).show()
        } catch (_: Exception) {
            Toast.makeText(
                activity,
                "Could not open the offline Linux library. Bluetooth controls are unchanged.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openPhotoFeedback(activity: Activity, manager: () -> HidManager) {
        Toast.makeText(
            activity,
            "Photo analysis requires the CatAI build. The offline command library remains available.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun openTargetSystem(activity: Activity) {
        Toast.makeText(
            activity,
            "Offline commands show compatibility on each detail page; no target profile is required.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun openSettings(activity: Activity) {
        Toast.makeText(
            activity,
            "Offline mode uses no API key, account, or network settings.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun attach(activity: Activity, row: LinearLayout, manager: () -> HidManager) {
        val library = Button(activity).apply {
            text = "Offline Linux Library"
            isAllCaps = false
            setOnClickListener { openAssistant(activity, manager) }
        }
        row.addView(
            library,
            LinearLayout.LayoutParams(0, BlackCatStyle.dp(activity, 56), 1f)
        )
    }
}
