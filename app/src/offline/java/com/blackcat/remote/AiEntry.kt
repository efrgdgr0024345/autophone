package com.blackcat.remote

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
        val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(BlackCatStyle.label(activity, "Settings", 22f, Color.BLACK, true).apply {
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
        })
        val scroll = ScrollView(activity)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), 0, dp(activity, 12), dp(activity, 12))
        }
        val note = BlackCatStyle.label(activity,
            "CatBT uses the local Linux command library. Commands explain their compatible systems. No API key, camera or app microphone access is used.",
            14f, Color.WHITE).apply {
            setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14))
            background = BlackCatStyle.round(activity, 0xff101714.toInt(), 16)
        }
        body.addView(note)
        fun action(title: String, run: () -> Unit) {
            body.addView(Button(activity).apply {
                text = title
                BlackCatStyle.styleButton(activity, this, false, true)
                setOnClickListener { dialog.dismiss(); run() }
            }, LinearLayout.LayoutParams(-1, dp(activity, 52)).apply { topMargin = dp(activity, 8) })
        }
        val main = activity as? MainActivity
        action("Set up Bluetooth") { main?.openBluetoothSetup() }
        action("Disconnect computer") { main?.endBluetoothSession() }
        main?.let { body.addView(it.createDiagnosticsView()) }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        main?.let { root.addView(it.createBottomNavigation("Settings") { dialog.dismiss() }) }
        dialog.setContentView(root)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    fun dictateManualText(activity: Activity, onTranscript: (String) -> Unit, onStatus: (String) -> Unit): () -> Unit {
        onStatus("Use your phone keyboard's microphone for voice typing, review the text, then press Send Text.")
        return { }
    }

    private fun dp(activity: Activity, value: Int) = BlackCatStyle.dp(activity, value)

    fun leaveAssistantForNavigation(activity: Activity) { }

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
