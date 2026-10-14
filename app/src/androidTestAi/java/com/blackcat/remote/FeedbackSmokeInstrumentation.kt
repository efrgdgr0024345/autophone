package com.blackcat.remote

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.graphics.Rect
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.ScrollView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Credential-free rendered UI checks. All goal/command/photo-analysis data below is synthetic. */
class FeedbackSmokeInstrumentation : Instrumentation() {
    private lateinit var activity: Activity
    private lateinit var startupMonitor: ActivityMonitor
    private lateinit var panel: Any
    private lateinit var dialog: Dialog
    private val checks = mutableListOf<String>()

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Run screenshot fixtures only on a disposable emulator" }
        File(targetContext.filesDir, "ui10-rendered").listFiles()?.filter { it.extension == "png" }?.forEach { it.delete() }
        sendStatus(0, Bundle().apply { putString("stream", "UI10_SMOKE_START\n") })
        try {
            startupMonitor = addMonitor("com.blackcat.remote.MainActivity", null, false)
            activity = startActivitySync(Intent().setClassName(targetContext,
                "com.blackcat.remote.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            Thread.sleep(900)
            await("visible Home screen") {
                descendants(activity.window.decorView).filterIsInstance<Button>().any {
                    it.isShown && it.text.startsWith("Bluetooth")
                }
            }
            captureRemoteScreens()
            captureStandaloneScreens()
            onMain {
                val manager = field(activity, "hid") as HidManager
                val type = Class.forName("com.blackcat.remote.AiPanel")
                panel = type.declaredConstructors.single().apply { isAccessible = true }
                    .newInstance(activity, manager, {})
                invoke(panel, "show")
                dialog = field(panel, "dialog") as Dialog
                // Only this seeded test dialog is captured; production screenshot protection stays enabled.
                dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                check(activity.getDrawable(R.drawable.black_cat_full) != null) { "Full-cat artwork must decode on Android" }
                check(findButton(dialog, "Get Plan").isEnabled.not())
                check((field(panel, "voice") as VoiceFeedbackController).listening.not())
                pass("Opening the planner does not start dictation or send a request")
                val goal = field(panel, "goal") as EditText
                goal.setText("Diagnose a Linux service error")
                workspace().replace(CommandPlan("Inspect before changing the system.", emptyList(), emptyList(), listOf(
                    command("List failed services", "systemctl --failed"), command("Read service logs", "journalctl -n 20"))))
                setField(panel, "selected", 0)
                invoke(panel, "renderAll")
            }
            screenshot("plan")
            onMain { scrollBottom(dialog) }
            screenshot("plan-steps")
            onMain {
                findButton(dialog, "Start with step 1").performClick()
                check(texts(dialog).any { it == "Step 1 of 2" })
                pass("The plan advances to the selected step without typing")
                findButton(dialog, "Edit this step").performClick()
            }
            waitForIdleSync()
            onMain {
                val editor = field(panel, "confirmation") as AlertDialog
                val fields = descendants(editor.window!!.decorView).filterIsInstance<EditText>()
                fields[1].setText("id\u202e")
                editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                check(workspace().plan!!.commands[0].command == "systemctl --failed")
                check(fields[1].error != null)
                fields[1].setText("systemctl --failed --no-pager")
                editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                check(workspace().plan!!.commands[0].risk == "high")
                pass("Editing rejects control characters and requires renewed risk review")
            }
            screenshot("step")
            onMain { findButton(dialog, "Edit this step").performClick() }
            screenshot("step-editor", field(panel, "confirmation") as Dialog)
            onMain { (field(panel, "confirmation") as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
            onMain {
                switchTab("PLAN")
                findButton(dialog, "Browse Offline Linux Library").performClick()
                val library = field(panel, "libraryPanel")!!
                val items = field(library, "items") as List<*>
                check(items.size == 212)
            }
            val library = field(panel, "libraryPanel")!!
            val libraryDialog = field(library, "dialog") as Dialog
            screenshot("library-categories", libraryDialog)
            onMain {
                val items = (field(library, "items") as List<*>).filterIsInstance<LinuxCommandItem>()
                val item = items.first { it.category.contains("Network", ignoreCase = true) }
                setField(library, "selectedCategory", item.category)
                setField(library, "selectedTopic", item.topic)
                setEnum(library, "level", "TOPICS")
                invoke(library, "render")
            }
            screenshot("library-topics", libraryDialog)
            onMain { setEnum(library, "level", "COMMANDS"); invoke(library, "render") }
            screenshot("library-commands", libraryDialog)
            onMain {
                val items = (field(library, "items") as List<*>).filterIsInstance<LinuxCommandItem>()
                val item = items.first { it.category == field(library, "selectedCategory") &&
                    it.topic == field(library, "selectedTopic") }
                setField(library, "selectedItem", item)
                setEnum(library, "level", "DETAIL")
                invoke(library, "render")
            }
            screenshot("library-detail", libraryDialog)
            onMain {
                val search = field(library, "search") as EditText
                setEnum(library, "level", "SEARCH")
                search.setText("failed services")
                invoke(library, "render")
            }
            screenshot("library-search", libraryDialog)
            onMain {
                val items = (field(library, "items") as List<*>).filterIsInstance<LinuxCommandItem>()
                setField(library, "selectedItem", items.first { it.category == field(library, "selectedCategory") &&
                    it.topic == field(library, "selectedTopic") })
                setEnum(library, "level", "DETAIL")
                invoke(library, "render")
                val item = field(library, "selectedItem") as LinuxCommandItem
                findButton(libraryDialog, "Add to current plan").performClick()
                check(workspace().plan!!.commands.size == 3)
                check(workspace().plan!!.commands.last().command == item.command)
                pass("The offline tree adds one local command without an AI request or typing")
                val token = workspace().touch()
                workspace().stage(token, CommandPlan("Recheck the service state.", emptyList(), emptyList(),
                    listOf(command("Inspect service", "systemctl status nginx --no-pager"))))
                switchTab("PLAN")
                check(workspace().plan!!.commands.size == 3)
                scrollBottom(dialog)
            }
            screenshot("proposed-update")
            onMain {
                findButton(dialog, "Apply updated plan").performClick()
                check(workspace().plan!!.commands.size == 1)
                check(workspace().confirmedSteps.isEmpty())
                pass("Applying a proposal replaces plan data without confirming a terminal result")
                switchTab("PREVIEW")
                check(findButton(dialog, "Connect Bluetooth first").isEnabled.not())
            }
            screenshot("preview")
            onMain {
                switchTab("FEEDBACK")
                setField(panel, "lastTypedCommand", workspace().plan!!.commands.single().command)
                setField(panel, "lastTypedIndex", 0)
                invoke(panel, "renderAll")
            }
            screenshot("result")
            onMain {
                scrollBottom(dialog)
                findButton(dialog, "Speak feedback").performClick()
                check((field(panel, "voice") as VoiceFeedbackController).listening.not())
            }
            screenshot("voice-consent", field(panel, "confirmation") as Dialog)
            onMain {
                val consent = field(panel, "confirmation") as AlertDialog
                consent.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                pass("Voice consent does not begin recording or submit feedback")
            }
            screenshot("result-feedback")
            onMain {
                findButton(dialog, "Take a photo of the result / error").performClick()
                val photo = field(panel, "photoPanel")!!
                check((field(photo, "goal") as EditText).text.contains("systemctl status nginx"))
                check(field(photo, "camera") == null)
            }
            val photo = field(panel, "photoPanel")!!
            val photoDialog = field(photo, "dialog") as Dialog
            screenshot("photo-capture", photoDialog)
            onMain {
                findButton(photoDialog, "Start camera").performClick()
            }
            await("Camera2 preview") { field(photo, "camera")?.let { field(it, "session") != null } == true }
            onMain { findButton(photoDialog, "Take photo").performClick() }
            await("Camera2 still image") { field(photo, "jpeg") != null }
            screenshot("photo-review", photoDialog)
            onMain {
                check((field(photo, "jpeg") as ByteArray).isNotEmpty())
                pass("Camera2 captures an emulated JPEG for exact review without an API request")
                val value = PhotoAnalysis("Synthetic fixture: service needs inspection.", listOf("An error is visible."),
                    emptyList(), listOf(command("Read error logs", "journalctl -u nginx -n 20")))
                setField(photo, "analysis", value)
                setEnum(photo, "state", "RESULT")
                invoke(photo, "render")
                check(texts(photoDialog).none { it == "Review & TYPE ONLY" })
            }
            screenshot("photo-analysis", photoDialog)
            onMain {
                findButton(photoDialog, "Use photo feedback in plan").performClick()
                check(workspace().proposal!!.commands.single().command == "journalctl -u nginx -n 20")
                check(workspace().plan!!.commands.single().command == "systemctl status nginx --no-pager")
                check(workspace().confirmedSteps.isEmpty())
                pass("Photo evidence stages a reviewed replacement without typing or assuming success")
                invoke(panel, "suspendForBackground")
                check(workspace().proposal == null)
                check(workspace().plan != null)
                check((field(panel, "voice") as VoiceFeedbackController).listening.not())
                pass("Suspension invalidates pending work while retaining only the in-memory plan")
                invoke(panel, "dismiss")
            }
            result.putString("stream", "\nUI10_SMOKE_PASS ${checks.size}\n" + checks.joinToString("\n"))
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "\nUI10_SMOKE_FAIL\n" + error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync {
            try {
                // A newly booted emulator may recreate the Activity as its display settles.
                // Follow startup only; a recreation during the planner workflow must still fail.
                if (!::panel.isInitialized && ::startupMonitor.isInitialized) {
                    startupMonitor.lastActivity?.takeUnless { it.isDestroyed }?.let { activity = it }
                }
                action()
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    private fun command(title: String, text: String) = SuggestedCommand(title, text,
        "Read-only synthetic test step.", "low", false, "Inspect the result yourself.", emptyList())
    private fun workspace() = field(panel, "workspace") as PlanWorkspace
    private fun pass(name: String) {
        checks.add("PASS: $name")
        sendStatus(0, Bundle().apply { putString("stream", "PASS: $name\n") })
    }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)
    private fun setField(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(owner, value)
    private fun invoke(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(owner)
    private fun setEnum(owner: Any, name: String, value: String) {
        val field = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
        field.set(owner, field.type.enumConstants.first { (it as Enum<*>).name == value })
    }
    private fun switchTab(name: String) {
        setEnum(panel, "activeTab", name)
        invoke(panel, "applyActiveTab")
        invoke(panel, "renderAll")
    }
    private fun descendants(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) } else emptyList()
    private fun texts(dialog: Dialog) = descendants(dialog.window!!.decorView)
        .filterIsInstance<TextView>().map { it.text.toString() }
    private fun findButton(dialog: Dialog, text: String) = descendants(dialog.window!!.decorView)
        .filterIsInstance<Button>().first { it.isShown && it.text.toString().startsWith(text) }
    private fun scrollBottom(dialog: Dialog) {
        descendants(dialog.window!!.decorView).filterIsInstance<ScrollView>()
            .filter { it.isShown }.forEach { it.fullScroll(View.FOCUS_DOWN) }
    }
    private fun captureRemoteScreens() {
        screenshot("home", null)
        onMain {
            descendants(activity.window.decorView).filterIsInstance<Button>()
                .first { it.isShown && it.text.startsWith("Bluetooth") }.performClick()
        }
        screenshot("bluetooth", null)
        for ((button, output) in listOf("Send Text" to "remote-send-text", "Touchpad" to "remote-touchpad", "Keyboard" to "remote-keyboard")) {
            onMain {
                descendants(activity.window.decorView).filterIsInstance<Button>()
                    .first { it.isShown && it.text.startsWith(button) }.performClick()
                descendants(activity.window.decorView).filterIsInstance<ScrollView>()
                    .filter { it.isShown }.forEach { it.fullScroll(View.FOCUS_DOWN) }
            }
            screenshot(output, null)
        }
        onMain {
            descendants(activity.window.decorView).filterIsInstance<Button>()
                .first { it.isShown && it.text == "Diagnostics" }.performClick()
            descendants(activity.window.decorView).filterIsInstance<ScrollView>()
                .filter { it.isShown }.forEach { it.fullScroll(View.FOCUS_DOWN) }
        }
        screenshot("diagnostics", null)
    }
    private fun captureStandaloneScreens() {
        var screen: Dialog? = null
        onMain {
            val target = TargetSystemPanel(activity)
            target.show()
            screen = field(target, "dialog") as Dialog
        }
        screenshot("target-system", screen)
        onMain {
            screen!!.dismiss()
            val settings = AiSettingsPanel(activity)
            settings.show()
            screen = field(settings, "dialog") as Dialog
        }
        screenshot("settings", screen)
        onMain { screen!!.dismiss() }
    }
    private fun await(name: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            onMain { ready = condition() }
            if (ready) { waitForIdleSync(); return }
            Thread.sleep(250)
        }
        error("Timed out waiting for $name")
    }
    private fun screenshot(name: String, screen: Dialog? = dialog) {
        if (screen != null) onMain { screen.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        waitForIdleSync()
        Thread.sleep(500)
        val window = screen?.window ?: activity.window
        var bitmap: Bitmap? = null
        var status = PixelCopy.ERROR_UNKNOWN
        val copied = CountDownLatch(1)
        onMain {
            bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(window, bitmap!!, { result -> status = result; copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        check(copied.await(20, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS) { "App-window capture failed ($status)" }
        val captured = bitmap!!
        val output = File(targetContext.filesDir, "ui10-rendered").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { captured.compress(Bitmap.CompressFormat.PNG, 100, it) }
        captured.recycle()
    }
}
