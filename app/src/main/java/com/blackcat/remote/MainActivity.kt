package com.blackcat.remote

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.*
import android.widget.*
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

/** V2 HID manager/descriptor/report mapping stay frozen; AI is an optional reviewed adapter. */
class MainActivity : Activity() {
    private lateinit var hid: HidManager
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var diagnostics: Diagnostics
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var typingJob: Job? = null
    private var closeAssistant: (() -> Unit)? = null
    private var resumed = false
    @Volatile private var aiReady = false
    private val connectionEpoch = AtomicLong(0)

    private val commandTarget = object : CommandTarget {
        override fun current(): HostSession? {
            if (!resumed || !aiReady || !::hid.isInitialized || !hid.registered) return null
            if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
            val epoch = connectionEpoch.get()
            val device = hid.connected ?: return null
            val name = try { device.name ?: "Bluetooth host" } catch (_: SecurityException) { return null }
            val address = try { device.address } catch (_: SecurityException) { return null }
            if (connectionEpoch.get() != epoch) return null
            val safeName = name.filter { it.code in 32..126 }.take(48)
            return HostSession(address, epoch, safeName + " [" + address.takeLast(5) + "]")
        }

        override fun sendKey(session: HostSession, modifier: Int, usage: Int): Boolean {
            if (current() != session) return false
            return hid.sendKeyboard(modifier, usage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        diagnostics = Diagnostics { runOnUiThread { log.text = it } }
        hid = HidManager(this) { message ->
            connectionEpoch.incrementAndGet()
            aiReady = when {
                message.startsWith("PASS HID_CONNECTED") -> true
                message.contains("HID_DISCONNECT") ||
                    message.contains("HID_CONNECTING") ||
                    message.contains("unregistered") ||
                    message.contains("profile disconnected") ||
                    message.startsWith("FAIL") -> false
                else -> aiReady
            }
            event(message)
        }
        permissionsOrInit()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        connectionEpoch.incrementAndGet()
        closeAssistant?.invoke()
        closeAssistant = null
        typingJob?.cancel()
        hid.sendKeyboard(0, 0)
        super.onPause()
    }

    private fun permissionsOrInit() {
        if (Build.VERSION.SDK_INT >= 31) {
            val needed = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), 10)
                return
            }
        }
        status.text = "HID REGISTERING"
        event("INFO APP_START")
        if (!hid.init()) status.text = "BLUETOOTH/HID ERROR"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            event("PASS BLUETOOTH_PERMISSIONS")
            permissionsOrInit()
        } else {
            event("FAIL Bluetooth permission denied")
        }
    }

    @Suppress("DEPRECATION")
    private fun pair() {
        if (!hid.registered) {
            event("WAIT HID not registered")
            return
        }
        event("PASS HID registered before discoverability")
        try {
            startActivityForResult(hid.discoverableIntent(300), 20)
            event("INFO DISCOVERABILITY_REQUESTED")
        } catch (_: Exception) {
            event("FAIL discoverability request")
        }
    }

    @Deprecated("Compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 20) {
            if (resultCode > 0) {
                status.text = "DISCOVERABLE — ADD ON COMPUTER"
                event("PASS DISCOVERABLE seconds=" + resultCode)
            } else {
                event("WARN discoverability declined")
            }
        }
    }

    private fun openAssistant() {
        typingJob?.cancel()
        hid.sendKeyboard(0, 0)
        closeAssistant?.invoke()
        closeAssistant = AiEntry.open(this, commandTarget)
    }

    private fun button(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { click() }
    }

    private fun row(vararg buttons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEach { addView(it, LinearLayout.LayoutParams(0, dp(46), 1f)) }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        root.addView(TextView(this).apply {
            text = if (AiEntry.enabled) "BLACK CAT AI — TEST" else "BLACK CAT REMOTE"
            textSize = 22f
        })
        status = TextView(this).apply { text = "STARTING"; textSize = 17f }
        root.addView(status)
        root.addView(button("PAIR AS KEYBOARD/MOUSE") { pair() })
        root.addView(TextView(this).apply { text = "Computer: Bluetooth → Add device → select this phone → Pair" })

        log = TextView(this).apply { textSize = 10f }
        val logs = ScrollView(this).apply {
            addView(log)
            setOnClickListener {
                layoutParams.height = if (layoutParams.height == dp(90)) dp(320) else dp(90)
                requestLayout()
            }
        }
        root.addView(logs, LinearLayout.LayoutParams(-1, dp(90)))
        root.addView(row(
            button("COPY LOG") {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Black Cat diagnostics", diagnostics.snapshot()))
            },
            button("CLEAR") { diagnostics.clear() }
        ))

        if (AiEntry.enabled) root.addView(button("AI COMMAND ASSISTANT") { openAssistant() })

        val input = EditText(this).apply {
            hint = "Type text to send"
            isSaveEnabled = false
        }
        root.addView(input)
        root.addView(button("SEND") {
            sendText(input.text.toString())
            input.text.clear()
        })

        val pad = TextView(this).apply {
            text = "TOUCHPAD"
            gravity = Gravity.CENTER
            setBackgroundColor(0xffdddddd.toInt())
        }
        var x = 0f
        var y = 0f
        pad.setOnTouchListener { view, motion ->
            when (motion.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x = motion.x; y = motion.y }
                MotionEvent.ACTION_MOVE -> {
                    hid.sendMouse(0, (motion.x - x).toInt(), (motion.y - y).toInt())
                    x = motion.x
                    y = motion.y
                }
                MotionEvent.ACTION_UP -> view.performClick()
                MotionEvent.ACTION_CANCEL -> hid.sendMouse(0, 0, 0)
            }
            true
        }
        root.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f))

        fun mouse(label: String, mask: Int) = button(label) {
            hid.sendMouse(mask, 0, 0)
            hid.sendMouse(0, 0, 0)
        }
        root.addView(row(
            mouse("LEFT", HidReports.LEFT),
            mouse("MIDDLE", HidReports.MIDDLE),
            mouse("RIGHT", HidReports.RIGHT)
        ))

        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(button("FULL KEYBOARD") {
            keyboard.visibility = if (keyboard.visibility == View.GONE) View.VISIBLE else View.GONE
        })

        fun key(label: String, usage: Int, modifier: Int = 0) = button(label) { hid.sendKeyboard(modifier, usage) }
        keyboard.addView(row(key("ESC", 41), key("TAB", 43), key("BKSP", 42), key("DEL", 76), key("ENTER", 40)))
        keyboard.addView(row(key("CTRL", 0, HidReports.CTRL), key("SHIFT", 0, HidReports.SHIFT), key("ALT", 0, HidReports.ALT), key("GUI", 0, HidReports.GUI)))
        keyboard.addView(row(key("INS", 73), key("HOME", 74), key("END", 77), key("PGUP", 75), key("PGDN", 78)))
        keyboard.addView(row(key("←", 80), key("↑", 82), key("↓", 81), key("→", 79)))
        keyboard.addView(row(key("F1", 58), key("F2", 59), key("F3", 60), key("F4", 61), key("F5", 62), key("F6", 63)))
        keyboard.addView(row(key("F7", 64), key("F8", 65), key("F9", 66), key("F10", 67), key("F11", 68), key("F12", 69)))
        root.addView(keyboard)
        setContentView(root)
    }

    private fun sendText(value: String) {
        typingJob?.cancel()
        typingJob = scope.launch {
            val host = commandTarget.current() ?: return@launch
            for (character in value) {
                ensureActive()
                if (commandTarget.current() != host) break
                val report = HidReports.char(character) ?: continue
                if (!commandTarget.sendKey(host, report.second, report.first)) break
                delay(12)
            }
            event("INFO manual text sequence ended; content not logged")
        }
    }

    private fun event(message: String) {
        diagnostics.add(message)
        runOnUiThread {
            if (message.contains("HID_REGISTERED")) status.text = "READY TO PAIR"
            if (message.contains("HID_CONNECTED")) status.text = "READY"
            if (message.contains("HID_DISCONNECTED")) status.text = "DISCONNECTED"
            if (message.contains("Bluetooth off")) status.text = "BLUETOOTH OFF"
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        closeAssistant?.invoke()
        closeAssistant = null
        scope.cancel()
        hid.close()
        super.onDestroy()
    }
}
