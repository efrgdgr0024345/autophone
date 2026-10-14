package com.blackcat.remote

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Dialog
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.graphics.Rect
import android.os.SystemClock
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.ScrollView
import android.view.inputmethod.InputMethodManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
        var commandFixture: CommandPlan? = null
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Run screenshot fixtures only on a disposable emulator" }
        File(targetContext.filesDir, "ui11-rendered").listFiles()?.filter { it.extension == "png" }?.forEach { it.delete() }
        sendStatus(0, Bundle().apply { putString("stream", "UI11_SMOKE_START\n") })
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
                val parsed = PlanCodec.parsePlan("""{
                    "explanation":"Inspect before changing the system.", "assumptions":[],
                    "decision":{"mode":"commands","questions":[],"commands":[
                        {"title":"List failed services","command":"systemctl --failed",
                         "explanation":"Read-only synthetic test step.","risk":"low","requires_admin":false,
                         "expected_result":"Inspect the result yourself.","warnings":[]},
                        {"title":"Read service logs","command":"journalctl -n 20",
                         "explanation":"Read-only synthetic test step.","risk":"low","requires_admin":false,
                         "expected_result":"Inspect the result yourself.","warnings":[]}]}}
                """)
                check(parsed.commands.map { it.command } == listOf("systemctl --failed", "journalctl -n 20"))
                val clarify = PlanCodec.parsePlan("""{"explanation":"More context is needed.","assumptions":[],
                    "decision":{"mode":"clarify","questions":["Which service failed?"],"commands":[]}}""")
                check(clarify.commands.isEmpty() && clarify.questions.size == 1)
                var mixedRejected = false
                try {
                    PlanCodec.parsePlan("""{"explanation":"Mixed fixture.","assumptions":[],
                        "decision":{"mode":"commands","questions":["Which service?"],"commands":[]}}""")
                } catch (_: PlanException) { mixedRejected = true }
                check(mixedRejected)
                pass("Structured command and clarification decisions parse separately, while mixed output is rejected")
                commandFixture = parsed
                workspace().replace(clarify)
                setField(panel, "selected", -1)
                invoke(panel, "renderAll")
                check(texts(dialog).any { it.contains("Which service failed?") })
                check(descendants(dialog.window!!.decorView).filterIsInstance<Button>()
                    .none { it.isShown && it.text.startsWith("Start with step 1") })
                scrollBottom(dialog)
            }
            screenshot("plan-clarification")
            onMain {
                workspace().replace(commandFixture!!)
                setField(panel, "selected", 0)
                invoke(panel, "renderAll")
                descendants(dialog.window!!.decorView).filterIsInstance<ScrollView>()
                    .filter { it.isShown }.forEach { it.scrollTo(0, 0) }
            }
            screenshot("plan")
            onMain { scrollBottom(dialog) }
            screenshot("plan-steps")
            onMain {
                findButton(dialog, "Start with step 1").performClick()
                check(texts(dialog).any { it == "Step 1 of 2" })
                check(descendants(dialog.window!!.decorView).filterIsInstance<android.widget.ImageView>()
                    .none { it.isShown && it.contentDescription == "Black Cat" })
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
                val value = PhotoAnalysisCodec.parseAnalysis("""{
                    "summary":"Synthetic fixture: service needs inspection.",
                    "observations":["An error is visible."],
                    "decision":{"mode":"commands","questions":[],"commands":[
                        {"title":"Read error logs","command":"journalctl -u nginx -n 20",
                         "explanation":"Read-only synthetic test step.","risk":"low","requires_admin":false,
                         "expected_result":"Inspect the result yourself.","warnings":[]}]}}
                """)
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
                val retainedPlan = workspace().plan
                val proposal = workspace().proposal!!
                val navigationRevision = workspace().revision
                val retainedGoal = (field(panel, "goal") as EditText).text.toString()
                invoke(panel, "hideForNavigation")
                check(!dialog.window!!.decorView.isShown)
                check(workspace().plan === retainedPlan && workspace().proposal == null)
                check((field(panel, "voice") as VoiceFeedbackController).listening.not())
                check(!(field(panel, "busy") as Boolean))
                check(!workspace().stage(navigationRevision, proposal))
                invoke(panel, "show")
                check(dialog.window!!.decorView.isShown)
                check(workspace().plan === retainedPlan && workspace().proposal == null)
                check((field(panel, "goal") as EditText).text.toString() == retainedGoal)
                check(!(field(panel, "busy") as Boolean))
                check(workspace().confirmedSteps.isEmpty())
                pass("Leaving and reopening AI retains the editable draft while invalidating pending work without a request or replay")
                check(workspace().stage(workspace().touch(), proposal))
                invoke(panel, "suspendForBackground")
                check(workspace().proposal == null)
                check(workspace().plan != null)
                check((field(panel, "voice") as VoiceFeedbackController).listening.not())
                pass("Suspension invalidates pending work while retaining only the in-memory plan")
                invoke(panel, "dismiss")
            }
            result.putString("stream", "\nUI11_SMOKE_PASS ${checks.size}\n" + checks.joinToString("\n"))
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "\nUI11_SMOKE_FAIL\n" + error.stackTraceToString())
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
    private fun texts(root: View) = descendants(root).filterIsInstance<TextView>().map { it.text.toString() }
    private fun texts(dialog: Dialog) = texts(dialog.window!!.decorView)
    private fun findButton(root: View, text: String) = descendants(root)
        .filterIsInstance<Button>().first { it.isShown && it.text.toString().startsWith(text) }
    private fun findButton(dialog: Dialog, text: String) = findButton(dialog.window!!.decorView, text)
    private fun scrollBottom(dialog: Dialog) {
        descendants(dialog.window!!.decorView).filterIsInstance<ScrollView>()
            .filter { it.isShown }.forEach { it.fullScroll(View.FOCUS_DOWN) }
    }
    private fun captureRemoteScreens() {
        onMain {
            val nav = descendants(activity.window.decorView).filter {
                it.isShown && it.isClickable && it.contentDescription?.toString() in listOf("Home", "Keyboard", "AI", "Settings")
            }
            check(nav.map { it.contentDescription.toString() } == listOf("Home", "Keyboard", "AI", "Settings"))
            check(!serviceRunning()) { "Opening Home must not start a Bluetooth input session" }
        }
        screenshot("home", null)
        onMain {
            descendants(activity.window.decorView).filterIsInstance<Button>()
                .first { it.isShown && it.text.startsWith("Bluetooth") }.performClick()
        }
        screenshot("bluetooth", null)
        await("disposable emulator Bluetooth off") {
            activity.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == false
        }
        onMain {
            val info = activity.packageManager.getPackageInfo(activity.packageName,
                PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS)
            val permissions = info.requestedPermissions.orEmpty()
            check(permissions.contains("android.permission.FOREGROUND_SERVICE"))
            check(permissions.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"))
            check(!permissions.contains("android.permission.POST_NOTIFICATIONS"))
            val service = info.services.orEmpty().single { it.name == "com.blackcat.remote.BluetoothSessionService" }
            check(!service.exported)
            check(service.foregroundServiceType == ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }
        // Intercept only Android's enable prompt and return a cancellation. The app must
        // request it after the user's setup tap, while never enabling Bluetooth itself.
        val enablePrompt = addMonitor(IntentFilter(BluetoothAdapter.ACTION_REQUEST_ENABLE),
            ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            onMain {
                findButton(activity.window.decorView, "Set up Bluetooth").performClick()
                check(texts(activity.window.decorView).contains(BluetoothStatus.OFF))
            }
            await("explicit Android Bluetooth enable request") { enablePrompt.hits == 1 }
            onMain {
                check(texts(activity.window.decorView).contains(BluetoothStatus.OFF))
                check(!serviceRunning()) { "Cancelling Bluetooth setup must not start a session" }
                pass("Bluetooth-off failure is visible, an explicit setup tap requests Android enablement, and cancellation stays passive")
            }
            screenshot("bluetooth-off", null)
        } finally { removeMonitor(enablePrompt) }
        onMain {
            descendants(activity.window.decorView).single {
                it.isShown && it.isClickable && it.contentDescription?.toString() == "Keyboard"
            }.performClick()
            val root = activity.window.decorView
            check(findButton(root, "Phone keyboard").isShown)
            check(findButton(root, "Speak text").isShown)
            check(findButton(root, "Send Text").isShown)
            val input = descendants(root).filterIsInstance<EditText>().single()
            input.setText("Reviewed manual text")
            findButton(root, "Phone keyboard").performClick()
            check(input.hasFocus())
            (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)
            input.clearFocus()
            pass("Keyboard navigation exposes editable phone input, explicit Send Text and the original mouse controls")
        }
        screenshot("keyboard-text", null)
        onMain {
            val root = activity.window.decorView
            val pad = descendants(root).filterIsInstance<TextView>().single { it.text == "Drag here to move pointer" }
            check(pad.isShown)
            for (button in listOf("Left", "Middle", "Right")) check(findButton(root, button).isShown)
            scrollToView(root, pad)
        }
        screenshot("keyboard-mouse", null)
        onMain {
            val root = activity.window.decorView
            findButton(root, "Show special keys").performClick()
            check(findButton(root, "ESC").isShown)
            check(findButton(root, "ENTER").isShown)
            check(findButton(root, "F12").isShown)
            descendants(root).filterIsInstance<ScrollView>().filter { it.isShown }.forEach { it.fullScroll(View.FOCUS_DOWN) }
        }
        screenshot("keyboard-special-keys", null)
        onMain {
            descendants(activity.window.decorView).single {
                it.isShown && it.isClickable && it.contentDescription?.toString() == "Home"
            }.performClick()
        }
    }
    @Suppress("DEPRECATION")
    private fun serviceRunning() = activity.getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE).any { it.service.className == "com.blackcat.remote.BluetoothSessionService" }

    private fun scrollToView(root: View, view: View) {
        val scroll = descendants(root).filterIsInstance<ScrollView>().single { it.isShown }
        val rect = Rect()
        view.getDrawingRect(rect)
        scroll.offsetDescendantRectToMyCoords(view, rect)
        scroll.scrollTo(0, rect.top)
    }
    private fun captureStandaloneScreens() {
        var screen: Dialog? = null
        var settings: AiSettingsPanel? = null
        val checker = FixtureKeyChecker()
        onMain {
            val target = TargetSystemPanel(activity)
            target.show()
            screen = field(target, "dialog") as Dialog
        }
        screenshot("target-system", screen)
        onMain {
            screen!!.dismiss()
            settings = AiSettingsPanel(activity, checker)
            settings!!.show()
            screen = field(settings!!, "dialog") as Dialog
            check(checker.calls == 0) { "Opening Settings must not check an API key" }
            val content = texts(screen!!)
            check(content.contains("Target system"))
            check(content.contains("OpenAI API key"))
            check(content.contains("Model"))
            check(findButton(screen!!, "Diagnostics").isShown)
            check(descendants(screen!!.window!!.decorView).filterIsInstance<android.widget.ImageView>()
                .none { it.isShown && it.contentDescription == "Black Cat" })
        }
        screenshot("settings", screen)
        onMain {
            val input = field(settings!!, "keyInput") as EditText
            input.setText("sk-synthetic-ui11-fixture-not-a-real-key")
            (field(settings!!, "checkButton") as Button).performClick()
        }
        await("visible rejected-key response") {
            checker.calls == 1 && (field(settings!!, "keyState") as TextView).text.contains("Synthetic fixture: key rejected")
        }
        onMain { scrollToView(screen!!.window!!.decorView, field(settings!!, "keyState") as View) }
        screenshot("settings-key-rejected", screen)
        onMain {
            checker.nextResult = KeyCheckResult(KeyCheckStatus.VERIFIED,
                "Synthetic fixture: key accepted for the selected model.")
            check(!(field(settings!!, "rememberKey") as android.widget.CheckBox).isChecked)
            (field(settings!!, "checkButton") as Button).performClick()
        }
        await("visible verified-key response") {
            checker.calls == 2 && (field(settings!!, "keyState") as TextView).text.contains("held in memory")
        }
        onMain {
            check((field(settings!!, "keyInput") as EditText).text.isEmpty())
            pass("Settings opens without checking a key and displays injected rejection or verification only after an explicit check")
        }
        screenshot("settings-key-verified", screen)
        onMain {
            // Non-cancellable fixture work models a response already arriving when a person
            // edits the key. It contains no real credential and never leaves this process.
            checker.pending = CompletableDeferred()
            (field(settings!!, "keyInput") as EditText).setText("sk-late-synthetic-fixture-not-a-real-key")
            (field(settings!!, "checkButton") as Button).performClick()
        }
        await("pending synthetic verification") { checker.calls == 3 }
        onMain {
            (field(settings!!, "keyInput") as EditText).setText("sk-edited-synthetic-fixture-not-a-real-key")
            checker.pending!!.complete(KeyCheckResult(KeyCheckStatus.VERIFIED,
                "Late synthetic response must be ignored."))
        }
        await("late synthetic response completion") { checker.returned == 3 }
        onMain {
            check((field(settings!!, "keyState") as TextView).text.contains("API key changed"))
            check((field(settings!!, "keyInput") as EditText).text.contains("sk-edited-synthetic"))
            check((field(settings!!, "checkButton") as Button).isEnabled)
            pass("Editing the key invalidates an arriving verification result and preserves the person's new input")
            check(PendingApiKey.has(activity))
            (field(settings!!, "forgetButton") as Button).performClick()
            check(!PendingApiKey.has(activity))
            pass("Forgetting a checked temporary key clears it before asynchronous storage cleanup")
            findButton(screen!!, "Diagnostics").performClick()
            scrollBottom(screen!!)
        }
        screenshot("settings-diagnostics", screen)
        onMain { screen!!.dismiss() }
    }

    private class FixtureKeyChecker : ApiKeyChecker {
        var calls = 0
        var returned = 0
        var nextResult = KeyCheckResult(KeyCheckStatus.REJECTED_KEY, "Synthetic fixture: key rejected.")
        var pending: CompletableDeferred<KeyCheckResult>? = null
        override fun cancel() { /* The production coroutine/generation must discard the late fixture. */ }
        override suspend fun check(key: String, model: String): KeyCheckResult {
            check(key.startsWith("sk-") && key.contains("synthetic")) { "Only synthetic fixture credentials are accepted" }
            check(model in PlanCodec.MODELS)
            calls++
            val gate = pending
            val result = if (gate == null) nextResult else withContext(NonCancellable) { gate.await() }
            returned++
            return result
        }
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
        waitForIdleSync()
        val deadline = SystemClock.uptimeMillis() + 30000
        var lastFailure = "Window is not attached and laid out"
        while (SystemClock.uptimeMillis() < deadline) {
            var bitmap: Bitmap? = null
            var status = PixelCopy.ERROR_UNKNOWN
            val copied = CountDownLatch(1)
            onMain {
                // onMain may reacquire a recreated startup Activity. Resolve its Window only
                // afterwards: copying a previously retained Window races that recreation.
                val window = screen?.window ?: activity.window
                if (screen != null) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                val decor = window.decorView
                if (!decor.isAttachedToWindow || !decor.isLaidOut || !decor.isShown ||
                    decor.width <= 0 || decor.height <= 0) {
                    copied.countDown()
                    return@onMain
                }
                // A layout alone does not guarantee a backing surface. Wait for a real draw,
                // then post the request so that traversal can submit its frame first.
                val observer = decor.viewTreeObserver
                var frameRequested = false
                val listener = object : ViewTreeObserver.OnDrawListener {
                    override fun onDraw() {
                        if (frameRequested) return
                        frameRequested = true
                        decor.post {
                            if (observer.isAlive) observer.removeOnDrawListener(this)
                            if (!decor.isAttachedToWindow) {
                                lastFailure = "Window detached before capture"
                                copied.countDown()
                                return@post
                            }
                            bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                            try {
                                PixelCopy.request(window, bitmap!!, { result ->
                                    status = result
                                    lastFailure = "PixelCopy returned $result"
                                    copied.countDown()
                                }, Handler(Looper.getMainLooper()))
                            } catch (error: IllegalArgumentException) {
                                lastFailure = error.message ?: "PixelCopy rejected the Window"
                                // Only a transient startup surface is retried. Other argument
                                // errors still fail; no drawing fallback or mock image is used.
                                if (error.message?.contains("backing surface") != true) {
                                    status = Int.MIN_VALUE
                                }
                                copied.countDown()
                            }
                        }
                    }
                }
                observer.addOnDrawListener(listener)
                decor.postInvalidateOnAnimation()
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!frameRequested && copied.count > 0) {
                        if (observer.isAlive) observer.removeOnDrawListener(listener)
                        lastFailure = "Window did not draw within the capture attempt"
                        copied.countDown()
                    }
                }, 3000)
            }
            check(copied.await(5, TimeUnit.SECONDS)) { "App-window capture callback timed out: $name" }
            val captured = bitmap
            if (status == PixelCopy.SUCCESS && captured != null) {
                val output = File(targetContext.filesDir, "ui11-rendered").apply { mkdirs() }
                File(output, "$name.png").outputStream().use {
                    check(captured.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG capture failed: $name" }
                }
                captured.recycle()
                return
            }
            captured?.recycle()
            check(status != Int.MIN_VALUE) { "App-window capture failed: $name ($lastFailure)" }
            Thread.sleep(100)
        }
        error("App-window capture failed after waiting for a real surface: $name ($lastFailure)")
    }
}
