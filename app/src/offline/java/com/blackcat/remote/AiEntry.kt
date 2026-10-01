package com.blackcat.remote

import android.app.Activity
import android.widget.LinearLayout

/** Offline variant: no AI UI, credentials or networking; V2 behaviour is unchanged. */
object AiEntry {
    fun attach(activity: Activity, row: LinearLayout, manager: () -> HidManager) {}
}
