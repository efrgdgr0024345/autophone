package com.blackcat.remote

import android.app.Activity

/** The offline build does not package AI networking or API-key storage. */
object AiEntry {
    const val enabled = false
    fun open(activity: Activity, target: CommandTarget): () -> Unit = {}
}
