package com.blackcat.remote

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Dialog
import android.app.Instrumentation
import org.json.JSONObject
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
import kotlinx.coroutines.delay
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
    private var startupSettled = false
    private lateinit var panel: Any
    private lateinit var dialog: Dialog
    private val checks = mutableListOf<String>()
    private var autoOnly = false
    private var imageAuto = false
    private var layoutOnly = false
    private var injectedImages = false
    private var imageRunId = ""
    private var timeoutOnly = false
    private var learningOnly = false
    private var workflowsOnly = false
    private var searchStreamOnly = false
    private var evidenceOnly = false
    private var updatesOnly = false
    private var liveUpdatesOnly = false
    private var oldFeedUrl = ""

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        autoOnly = arguments?.getString("scenario") == "auto"
        injectedImages = arguments?.getString("scenario") == "image-injected"
        imageRunId = arguments?.getString("demoRunId").orEmpty()
        learningOnly = arguments?.getString("scenario") == "learning"
        workflowsOnly = arguments?.getString("scenario") == "workflows"
        searchStreamOnly = arguments?.getString("scenario") == "search-stream"
        evidenceOnly = arguments?.getString("scenario") == "evidence"
        updatesOnly = arguments?.getString("scenario") == "updates"
        liveUpdatesOnly = arguments?.getString("scenario") == "updates-live"
        oldFeedUrl = arguments?.getString("oldFeedUrl").orEmpty()
        timeoutOnly = arguments?.getString("scenario") == "timeout"
        imageAuto = arguments?.getString("scenario") == "image-auto" || injectedImages
        layoutOnly = arguments?.getString("scenario") == "layout"
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var commandFixture: CommandPlan? = null
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Run screenshot fixtures only on a disposable emulator" }
        File(targetContext.filesDir, "ui12-rendered").listFiles()?.filter { it.extension == "png" }?.forEach { it.delete() }
        sendStatus(0, Bundle().apply { putString("stream", "UI12_SMOKE_START\n") })
        try {
            startupMonitor = addMonitor("com.blackcat.remote.MainActivity", null, false)
            activity = startActivitySync(Intent().setClassName(targetContext,
                "com.blackcat.remote.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            Thread.sleep(900)
            await("visible Home screen") {
                if (imageAuto) activity.window.decorView.isShown && activity.window.decorView.isLaidOut
                else descendants(activity.window.decorView).filterIsInstance<Button>().any {
                    it.isShown && it.text.startsWith("Bluetooth")
                }
            }
            var stableActivity: Activity? = null
            var stableConfiguration = ""
            var stableSince = SystemClock.uptimeMillis()
            await("stable attached Activity and display configuration") {
                val decor = activity.window.decorView
                val signature = "${decor.width}:${decor.height}:${activity.resources.configuration}"
                if (stableActivity !== activity || signature != stableConfiguration || !decor.isAttachedToWindow || !decor.isLaidOut) {
                    stableActivity = activity
                    stableConfiguration = signature
                    stableSince = SystemClock.uptimeMillis()
                }
                decor.isShown && decor.isAttachedToWindow && decor.isLaidOut && SystemClock.uptimeMillis() - stableSince >= 2000
            }
            startupSettled = true
            if (liveUpdatesOnly) {
                captureLiveUpdates()
                result.putString("stream", "\nUI20_LIVE_UPDATES_PASS ${checks.size}\n" + checks.joinToString("\n"))
                finish(Activity.RESULT_OK, result)
                return
            }
            if (updatesOnly) {
                captureUpdates()
                result.putString("stream", "\nUI20_UPDATES_PASS ${checks.size}\n" + checks.joinToString("\n"))
                finish(Activity.RESULT_OK, result)
                return
            }
            if (evidenceOnly) {
                captureEvidenceAuto()
                result.putString("stream", "\nUI19_EVIDENCE_PASS ${checks.size}\n" + checks.joinToString("\n"))
                finish(Activity.RESULT_OK, result)
                return
            }
            if (searchStreamOnly) {
                captureSearchAndStreaming()
                result.putString("stream", "\nUI17_SEARCH_STREAM_PASS ${checks.size}\n" + checks.joinToString("\n"))
                finish(Activity.RESULT_OK, result)
                return
            }
            if (workflowsOnly) {
                captureFullKaliWorkflows()
                result.putString("stream", "\nUI16_WORKFLOWS_PASS ${checks.size}\n" + checks.joinToString("\n"))
                finish(Activity.RESULT_OK, result)
                return
            }
            if (learningOnly) {
                captureOfflineLearning()
                result.putString("stream", "\nUI15_LEARNING_PASS ${checks.size}\n" + checks.joinToString("\n"))
                finish(Activity.RESULT_OK, result)
                return
            }
            if (timeoutOnly) {
                captureAutoTimeout()
                result.putString("stream", "\nUI14_TIMEOUT_PASS ${checks.size}\n" + checks.joinToString("\n"))
                finish(Activity.RESULT_OK, result)
                return
            }
            if (imageAuto) {
                AutoImageDemo(this, activity, injectedImages, imageRunId).run()
                result.putString("stream", "\nIMAGE_AUTO_DEMO_PASS\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (layoutOnly) {
                captureFlowMatrix()
                result.putString("stream", "\nUI14_LAYOUT_PASS ${checks.size}\n" + checks.joinToString("\n"))
                finish(Activity.RESULT_OK, result)
                return
            }
            if (!autoOnly) {
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
                    "explanation":"Inspect before changing the system.", "assumptions":[],"roadmap":[],
                    "decision":{"mode":"commands","questions":[],"commands":[
                        {"title":"List failed services","command":"systemctl --failed",
                         "explanation":"Read-only synthetic test step.","risk":"low","requires_admin":false,
                         "expected_result":"Inspect the result yourself.","warnings":[]},
                        {"title":"Read service logs","command":"journalctl -n 20",
                         "explanation":"Read-only synthetic test step.","risk":"low","requires_admin":false,
                         "expected_result":"Inspect the result yourself.","warnings":[]}]}}
                """)
                check(parsed.commands.map { it.command } == listOf("systemctl --failed", "journalctl -n 20"))
                val clarify = PlanCodec.parsePlan("""{"explanation":"More context is needed.","assumptions":[],"roadmap":[],
                    "decision":{"mode":"clarify","questions":["Which service failed?"],"commands":[]}}""")
                check(clarify.commands.isEmpty() && clarify.questions.size == 1)
                var mixedRejected = false
                try {
                    PlanCodec.parsePlan("""{"explanation":"Mixed fixture.","assumptions":[],"roadmap":[],
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
                switchTab("FEEDBACK")
                setField(panel, "busy", true)
                invoke(panel, "renderAll")
                check((field(panel, "goalStage") as View).visibility == View.GONE)
            }
            screenshot("long-request-cancel")
            onMain {
                val cancel = findButton(field(panel, "manualActionBar") as View, "Cancel request / typing")
                fullyVisible(cancel, 48)
                cancel.performClick()
                check(field(panel, "busy") == false)
                check(workspace().plan === commandFixture)
                pass("A long feedback request has a visible pinned Cancel even with its goal collapsed; cancellation retains the plan")
                switchTab("PLAN")
            }
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
            }
            val library = field(panel, "libraryPanel")!!
            await("expanded offline catalogue loaded", timeoutMillis = 60000) { (field(library, "items") as List<*>).size == 467 }
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
            }
            captureDetailedAndAuto()
            result.putString("stream", "\nUI12_SMOKE_PASS ${checks.size}\n" + checks.joinToString("\n"))
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "\nUI12_SMOKE_FAIL\n" + error.stackTraceToString())
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
                    startupMonitor.lastActivity?.takeUnless { it.isDestroyed }?.let {
                        if (startupSettled) check(it === activity) { "Activity recreated after startup settled" }
                        else activity = it
                    }
                }
                action()
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    private fun command(title: String, text: String) = SuggestedCommand(title, text,
        "Read-only synthetic test step.", "low", false, "Inspect the result yourself.", emptyList())

    @Suppress("DEPRECATION")
    private fun captureFlowMatrix() {
        val original = android.content.res.Configuration(activity.resources.configuration)
        val metrics = android.util.DisplayMetrics().apply { setTo(activity.resources.displayMetrics) }
        val long = "journalctl --no-pager -n 40 -u demo-service-" + "a".repeat(180)
        val plan = CommandPlan("A synthetic detailed service diagnosis. All command selection is passive.", emptyList(), emptyList(),
            listOf(command("Read the service log", long)) + (2..18).map { command("Diagnostic $it", "id") },
            listOf(PlanPhase("Inspect service state", "Collect evidence before changing the system.", listOf("Preserve access."))))
        try {
            for (scale in listOf(1f, 1.3f, 2f)) for (width in listOf(320, 360, 390, 412)) {
                onMain {
                    val config = android.content.res.Configuration(original).apply { fontScale = scale }
                    val display = android.util.DisplayMetrics().apply { setTo(metrics); scaledDensity = density * scale }
                    activity.resources.updateConfiguration(config, display)
                    panel = Class.forName("com.blackcat.remote.AiPanel").declaredConstructors.single().apply { isAccessible = true }
                        .newInstance(activity, field(activity, "hid") as HidManager, {})
                    invoke(panel, "show")
                    dialog = field(panel, "dialog") as Dialog
                    dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    dialog.window!!.setLayout((width * display.density).toInt(), ((if (width == 320) 640 else 780) * display.density).toInt())
                    (field(panel, "goal") as EditText).setText("Diagnose this Linux service while preserving access and collecting all relevant evidence")
                    workspace().replace(plan)
                    setField(panel, "selected", 0)
                    invoke(panel, "renderAll")
                }
                await("matrix window layout $width/$scale") { dialog.window!!.decorView.isLaidOut }
                onMain {
                    check((field(panel, "goalStage") as View).visibility == View.GONE) { "Goal form buried the ready command list" }
                    fullyVisible(findButton(dialog, "▶ Auto Play"), 48)
                    fullyVisible(findButton(dialog, "Voice ·"), 48)
                    check(texts(dialog).contains(long)) { "Exact long command missing from plan" }
                    findButton(dialog, "Start with step 1").performClick()
                }
                screenshot("matrix-$width-${(scale * 100).toInt()}-step")
                onMain {
                    val footer = field(panel, "manualActionBar") as View
                    fullyVisible(footer, 48)
                    fullyVisible(findButton(dialog, "Connect Bluetooth first"), 48)
                    fullyVisible(findButton(dialog, "▶ Auto Play"), 48)
                    val commandView = descendants(dialog.window!!.decorView).filterIsInstance<TextView>()
                        .first { it.isShown && it.text.toString() == long }
                    val available = commandView.width - commandView.totalPaddingLeft - commandView.totalPaddingRight
                    check(available > 0)
                    for (line in 0 until commandView.layout.lineCount) {
                        check(commandView.layout.getLineMax(line) <= available + 1) { "Visible command overflows at width $width / font $scale: ${commandView.layout.getLineMax(line)} > $available" }
                        check(commandView.layout.getEllipsisCount(line) == 0) { "Command was silently truncated" }
                    }
                    check((field(panel, "contentHost") as View).height >= (80 * metrics.density).toInt()) { "Pinned controls leave no readable task space" }
                    pass("$width dp / font $scale: exact command wraps, goal collapses, Auto/Voice and single send action remain visible")
                    switchTab("PLAN")
                }
                screenshot("matrix-$width-${(scale * 100).toInt()}-plan")
                if (scale == 1f && width == 320) onMain {
                    findButton(dialog, "Start with step 1").performClick()
                    dialog.onBackPressed()
                    check((field(panel, "planView") as View).visibility == View.VISIBLE)
                    dialog.onBackPressed()
                    check(field(panel, "panelVisible") == false && workspace().plan === plan)
                    invoke(panel, "show")
                    check(workspace().plan === plan)
                    pass("System Back returns Step to Plan, then Home, while preserving the draft without input or replay")
                }
                onMain { invoke(panel, "dismiss") }
                var setup: AutoAssistPanel? = null
                onMain {
                    setup = AutoAssistPanel(activity, field(activity, "hid") as HidManager, plan,
                        "Diagnose the synthetic service", "Linux", PlanCodec.DEFAULT_MODEL, 0, { 1L }, {}, { 2L }, {})
                    setup!!.show()
                    dialog = field(setup!!, "dialog") as Dialog
                    dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    dialog.window!!.setLayout((width * displayDensity()).toInt(), ((if (width == 320) 640 else 780) * displayDensity()).toInt())
                }
                screenshot("matrix-$width-${(scale * 100).toInt()}-auto")
                onMain {
                    fullyVisible(field(setup!!, "autoToggle") as View, 48)
                    fullyVisible(field(setup!!, "voiceToggle") as View, 48)
                    check(((field(setup!!, "body") as View).parent as ScrollView).height >= (80 * displayDensity()).toInt())
                    check(field(setup!!, "session") == null && field(setup!!, "key") == "")
                    pass("$width dp / font $scale: Auto controls stay visible and opening setup sends no request/input")
                    setup!!.dismiss()
                }
            }
            onMain {
                val icon = activity.packageManager.getApplicationIcon(activity.packageName)
                val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                icon.setBounds(0, 0, 432, 432); icon.draw(canvas)
                val top = bitmap.getPixel(216, 65)
                val output = File(targetContext.filesDir, "ui12-rendered").apply { mkdirs() }
                File(output, "launcher-icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                check(android.graphics.Color.red(top) >= 235 && android.graphics.Color.green(top) >= 235 && android.graphics.Color.blue(top) >= 235) {
                    "Launcher background must be white, including inside its adaptive mask: $top"
                }
                bitmap.recycle()
                pass("Packaged adaptive launcher icon is drawn by Android; no reference mock substituted")
            }
        } finally {
            onMain { activity.resources.updateConfiguration(original, metrics) }
        }
    }

    private fun fullyVisible(view: View, minimumDp: Int) {
        val rect = Rect()
        check(view.isShown && view.getGlobalVisibleRect(rect) && rect.height() >= view.height - 1 && rect.width() >= view.width - 1) {
            "Control clipped: ${view.contentDescription ?: (view as? TextView)?.text}"
        }
        check(view.height >= minimumDp * activity.resources.displayMetrics.density - 1) { "Touch target below $minimumDp dp" }
    }
    private fun displayDensity() = activity.resources.displayMetrics.density

    private fun captureDetailedAndAuto() {
        onMain {
            val manager = field(activity, "hid") as HidManager
            val type = Class.forName("com.blackcat.remote.AiPanel")
            panel = type.declaredConstructors.single().apply { isAccessible = true }.newInstance(activity, manager, {})
            invoke(panel, "show")
            dialog = field(panel, "dialog") as Dialog
            dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            (field(panel, "goal") as EditText).setText("Lock down my Ubuntu system while preserving local access and recovery")
            val roadmap = listOf("Inventory and threat model", "Backups and recovery", "OS support and patches",
                "Accounts and privileges", "Authentication and SSH", "Services and firewall", "Logs and monitoring", "Verification and maintenance")
                .map { PlanPhase(it, "Explain the purpose, safe order and stopping conditions for this phase.",
                    listOf("Check current state before changing it", "Verify the expected result and preserve recovery access")) }
            val detailed = CommandPlan("A comprehensive hardening roadmap, beginning with read-only checks. Changes require verified facts and a recovery path.",
                listOf("Synthetic UI fixture; no real model, HID or shell execution."), emptyList(),
                (1..18).map { command("Security check $it", if (it % 2 == 0) "whoami" else "id") }, roadmap)
            workspace().replace(detailed)
            setField(panel, "selected", 0)
            invoke(panel, "renderAll")
            check(texts(dialog).contains("Full roadmap"))
            val pinnedAuto = findButton(dialog, "▶ Auto Play")
            check(pinnedAuto.isEnabled)
            val pinnedVoice = findButton(dialog, "Voice ·")
            val voiceBefore = pinnedVoice.text.toString()
            pinnedVoice.performClick()
            check(pinnedVoice.text.toString() != voiceBefore)
            pinnedVoice.performClick()
        }
        await("pinned Auto and Voice controls laid out") {
            findButton(dialog, "▶ Auto Play").let { it.isLaidOut && it.height > 0 }
        }
        onMain {
            scrollBottom(dialog)
            val visible = android.graphics.Rect()
            val pinnedAuto = findButton(dialog, "▶ Auto Play")
            val pinnedVoice = findButton(dialog, "Voice ·")
            check(pinnedAuto.getGlobalVisibleRect(visible) && visible.height() == pinnedAuto.height)
            check(pinnedVoice.getGlobalVisibleRect(visible) && visible.height() == pinnedVoice.height)
        }
        screenshot("detailed-plan-18-steps")
        onMain {
            findButton(dialog, "Show all phase details").performClick()
            check(texts(dialog).any { it.contains("preserve recovery access") })
            scrollToView(dialog.window!!.decorView, findButton(dialog, "Hide phase details"))
        }
        screenshot("detailed-roadmap-expanded")
        onMain {
            findButton(dialog, "Start with step 1").performClick()
            check(field(panel, "confirmation") == null)
            check(texts(dialog).contains("Step 1 of 18"))
            check(findButton(dialog, "Connect Bluetooth first").isEnabled.not())
            pass("A detailed 18-step roadmap remains visible; selection opens one approval screen without a second modal or HID input")
        }
        screenshot("single-approval-step")
        onMain { invoke(panel, "dismiss") }

        val steps = listOf(command("Check identity", "id"), command("Check account name", "whoami"))
        val autoPlan = CommandPlan("Two synthetic terminal checks.", emptyList(), emptyList(), steps)
        var auto: AutoAssistPanel? = null
        var autoDialog: Dialog? = null
        val host = HostSession("synthetic-fixture", 1, "Synthetic fixture computer")
        val fakeTarget = FixtureCommandTarget(host)
        val fakeObserver = FixtureAutoObserver().apply { firstResultGate = CompletableDeferred() }
        var autoRevision = 1L
        val publishedPlans = mutableListOf<CommandPlan>()
        val fakePlanner = object : AutoPlanClient {
            override fun cancel() {}
            override suspend fun propose(goal: String, target: String, model: String, key: String): CommandPlan {
                check(goal.contains("FIRST PAGE") && goal.contains("SECOND PAGE"))
                check(goal.contains("Inspect this disposable fixture"))
                return CommandPlan("Collected evidence needs a kernel check instead of the old account-name step.",
                    emptyList(), emptyList(), listOf(command("Check kernel from feedback", "uname -r")))
            }
        }
        onMain {
            activity.getSharedPreferences(TargetSystemPanel.PREFS, Activity.MODE_PRIVATE).edit()
                .putString(AssistantNarrator.VOICE_KEY, NarrationVoice.TEXT.name).apply()
            PendingApiKey.put(activity, "sk-ui12-synthetic-fixture-not-a-real-key")
            auto = AutoAssistPanel(activity, field(activity, "hid") as HidManager, autoPlan,
                "Inspect this disposable fixture", "Ubuntu Linux / Bash", PlanCodec.DEFAULT_MODEL, 0,
                { autoRevision }, {}, { publishedPlans.add(it); ++autoRevision }, {}, fakeObserver, fakeTarget, fakePlanner)
            auto!!.show()
            autoDialog = field(auto!!, "dialog") as Dialog
            autoDialog!!.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            check(fakeTarget.reports.isEmpty() && fakeObserver.calls == 0)
            findButton(autoDialog!!, "Start reviewed session").performClick()
            check(fakeTarget.reports.isEmpty() && fakeObserver.calls == 0)
            check((field(auto!!, "session") as AutoRunSession?) == null)
            pass("Opening Auto and Start without session consent cause no API/HID requests")
            (field(auto!!, "consentChoice") as android.widget.CheckBox).isChecked = true
            findButton(autoDialog!!, "Start live camera preview").performClick()
        }
        await("real Auto camera preview frames") { field(auto!!, "cameraStarted") == true && (field(auto!!, "previewFrames") as Int) >= 3 }
        screenshot("auto-reviewed-session", autoDialog)
        onMain { findButton(autoDialog!!, "Start reviewed session").performClick() }
        await("Auto idle-terminal preflight and typing countdown") {
            fakeObserver.calls == 1 && (field(auto!!, "session") as AutoRunSession?)?.stage == AutoStage.TYPING
        }
        onMain {
            check(autoDialog!!.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
            val voice = field(auto!!, "voiceToggle") as Button
            check(voice.text.toString().contains("OFF")) // No paid narration in the fixture.
            check((field(auto!!, "autoToggle") as Button).text.toString().contains("ON"))
        }
        screenshot("auto-countdown", autoDialog)
        await("first automatic Enter and its actual photo-check request") {
            (field(auto!!, "session") as AutoRunSession?)?.stage == AutoStage.ANALYZING &&
                fakeObserver.calls == 2 && fakeTarget.reports.count { it.second == 40 } == 1
        }
        screenshot("auto-checking-output", autoDialog)
        onMain { fakeObserver.firstResultGate!!.complete(Unit) }
        await("automatic pages and revised diagnostic checked by the synthetic observer", timeoutMillis = 300000) {
            (field(auto!!, "session") as AutoRunSession?)?.stage == AutoStage.FINISHED
        }
        onMain {
            check(fakeTarget.reports.count { it.second == 40 } == 7)
            check(fakeObserver.calls == 8 && fakeObserver.jpegCaptures == 8)
            check(field(auto!!, "key") == "" && field(auto!!, "camera") == null)
            check(autoDialog!!.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0)
            check(publishedPlans.any { p -> p.commands.any { it.title.contains("page 2") } })
            check(publishedPlans.any { p -> p.commands.any { it.title.contains("kernel from feedback") } && p.commands.none { it.command == "whoami" } })
            check((field(auto!!, "session") as AutoRunSession).transmitted.contains("uname -r"))
            pass("Auto captures two numbered pages, reconciles their evidence, replaces the remaining plan with a new diagnostic and finishes without manual approval; real Camera2 photos, synthetic vision/planner/HID")
        }
        screenshot("auto-plan-finished", autoDialog)
        onMain { auto!!.dismiss() }

        val stoppedTarget = FixtureCommandTarget(host)
        val stoppedObserver = FixtureAutoObserver()
        var stoppedRevision = 1L
        onMain {
            PendingApiKey.put(activity, "sk-ui12-synthetic-stop-not-a-real-key")
            auto = AutoAssistPanel(activity, field(activity, "hid") as HidManager, autoPlan,
                "Inspect this disposable fixture", "Ubuntu Linux / Bash", PlanCodec.DEFAULT_MODEL, 0,
                { stoppedRevision }, {}, { ++stoppedRevision }, {}, stoppedObserver, stoppedTarget)
            auto!!.show()
            autoDialog = field(auto!!, "dialog") as Dialog
            (field(auto!!, "consentChoice") as android.widget.CheckBox).isChecked = true
            findButton(autoDialog!!, "Start live camera preview").performClick()
        }
        await("second Auto preview") { field(auto!!, "cameraStarted") == true && (field(auto!!, "previewFrames") as Int) >= 3 }
        onMain { findButton(autoDialog!!, "Start reviewed session").performClick() }
        await("second preflight before countdown") { (field(auto!!, "session") as AutoRunSession?)?.stage == AutoStage.TYPING }
        onMain { (field(auto!!, "autoToggle") as Button).performClick() }
        Thread.sleep(3500)
        onMain {
            check(stoppedTarget.reports.isEmpty())
            check((field(auto!!, "session") as AutoRunSession).stage == AutoStage.STOPPED)
            check(field(auto!!, "key") == "" && field(auto!!, "camera") == null)
            pass("Stopping during the Auto countdown sends no fake-target keys or Enter and clears camera/key state without replay")
        }

        val failedTarget = FixtureCommandTarget(host)
        var handoff: AutoTaskHandoff? = null
        val failedObserver = object : AutoObservationClient {
            override fun cancel() {}
            override suspend fun observe(goal: String, step: SuggestedCommand, target: String, model: String,
                key: String, jpeg: ByteArray, preflight: Boolean, collectionContext: String): AutoObservation {
                check(preflight && jpeg.size > 100 && key == "sk-ui14-inline-fixture-not-a-real-key")
                throw PlanException("Synthetic quota failure: check API billing before starting again.")
            }
        }
        onMain {
            auto!!.dismiss()
            auto = AutoAssistPanel(activity, field(activity, "hid") as HidManager, autoPlan,
                "Inspect this disposable fixture", "Linux", PlanCodec.DEFAULT_MODEL, 0, { 1L }, {}, { 2L }, {},
                failedObserver, failedTarget, onTaskHandoff = { handoff = it })
            auto!!.show()
            autoDialog = field(auto!!, "dialog") as Dialog
            autoDialog!!.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            findButton(autoDialog!!, "Use a temporary API key here").performClick()
            (field(auto!!, "sessionKeyInput") as EditText).setText("sk-ui14-inline-fixture-not-a-real-key")
            (field(auto!!, "consentChoice") as android.widget.CheckBox).isChecked = true
            findButton(autoDialog!!, "Start live camera preview").performClick()
        }
        await("error fixture preview") { field(auto!!, "cameraStarted") == true && (field(auto!!, "previewFrames") as Int) >= 3 }
        onMain { findButton(autoDialog!!, "Start reviewed session").performClick() }
        await("useful preflight error without a command") {
            (field(auto!!, "session") as AutoRunSession?)?.stage == AutoStage.STOPPED
        }
        onMain {
            check((field(auto!!, "status") as TextView).text.contains("Synthetic quota failure"))
            check((field(auto!!, "sessionKeyInput") as EditText).text.isEmpty())
            check(field(auto!!, "key") == "" && field(auto!!, "camera") == null)
            check(failedTarget.reports.isEmpty())
            check(handoff!!.remaining.map { it.command } == listOf("id", "whoami") && handoff!!.attemptedCount == 0)
            pass("Inline session key clears after approval; preflight provider failure stays visible, sends no keys, and hands off the canonical plan")
        }
        screenshot("auto-initial-error", autoDialog)
        onMain { auto!!.dismiss() }
    }

    private fun captureLiveUpdates() {
        val authority = ProductionUpdateTrust.configured() ?: error("Real authority required")
        val installed = AndroidUpdateSecurity.installed(targetContext)
        val v = UpdateVerifier(authority, installed, AndroidUpdateSecurity.packageInfo(targetContext).requestedPermissions.orEmpty().toSet(),
            AndroidUpdateLedger(targetContext), UpdateApkInspector { AndroidUpdateSecurity.inspect(targetContext, it) }, UpdateHttps())
        val release = v.check()
        check(release.version >= installed.version && release.signer == installed.signer)
        pass("Real HTTPS feed authenticates with the owner-approved public key and installed APK identity")
        if (oldFeedUrl.isNotBlank()) {
            check(oldFeedUrl.matches(Regex("https://raw\\.githubusercontent\\.com/efrgdgr0024345/autophone/[0-9a-f]{40}/updates/ai\\.json")))
            val replay = UpdateTrust(oldFeedUrl, authority.metadataHosts, authority.apkHosts, authority.keys, authority.channel)
            val old = UpdateVerifier(replay, installed, AndroidUpdateSecurity.packageInfo(targetContext).requestedPermissions.orEmpty().toSet(),
                AndroidUpdateLedger(targetContext), UpdateApkInspector { AndroidUpdateSecurity.inspect(targetContext, it) }, UpdateHttps())
            var rejected = false
            try { old.check() } catch (_: Exception) { rejected = true }
            check(rejected) { "Authentic older metadata must fail against the persistent floor" }
            pass("Authentic older published metadata is rejected after a newer release raises the persistent rollback floor")
        }
        val monitor = addMonitor(UpdateActivity::class.java.name, null, false)
        onMain { activity.startActivity(Intent(activity, UpdateActivity::class.java)) }
        val updates = waitForMonitorWithTimeout(monitor, 15000) ?: error("Update Activity unavailable")
        waitForIdleSync()
        runOnMainSync {
            check(field(updates, "verifier") == null)
            findButton(updates.window.decorView, "Check for updates").performClick()
        }
        var remaining = 180000L
        while (remaining > 0) {
            var complete = false
            runOnMainSync { complete = (field(updates, "checkButton") as Button).isEnabled }
            if (complete) break
            Thread.sleep(250); remaining -= 250
        }
        check(remaining > 0)
        runOnMainSync {
            val message = (field(updates, "status") as TextView).text.toString()
            check(!message.contains("failed", true)) { message }
        }
        if (release.version > installed.version) {
            captureUpdateWindow(updates, "ui20-live-update-offer")
            runOnMainSync {
                val download = findButton(updates.window.decorView, "Download & verify APK")
                check(download.isEnabled); download.performClick()
            }
            remaining = 240000
            while (remaining > 0) {
                var ready = false
                runOnMainSync { ready = (field(updates, "saveButton") as Button).isEnabled }
                if (ready) break
                Thread.sleep(250); remaining -= 250
            }
            check(remaining > 0) { "Real APK verification did not enable export" }
            pass("Actual newer hosted APK passes hash/size/package/version/permission and cryptographic signer checks")
            captureUpdateWindow(updates, "ui20-live-apk-verified")
            val uri = android.net.Uri.parse(UpdateExportFixtureProvider.URI)
            val picker = addMonitor(IntentFilter(Intent.ACTION_CREATE_DOCUMENT),
                ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
            try {
                runOnMainSync { findButton(updates.window.decorView, "Save verified APK…").performClick() }
                remaining = 180000
                while (remaining > 0) {
                    var saved = false
                    runOnMainSync { saved = (field(updates, "status") as TextView).text.toString().startsWith("Verified APK saved.") }
                    if (saved) break
                    Thread.sleep(250); remaining -= 250
                }
                check(remaining > 0)
                check(picker.hits == 1)
                val bytes = targetContext.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                check(bytes.size.toLong() == release.size && SignedUpdateMetadata.sha256(bytes) == release.hash)
                pass("Explicit simulated picker selection exports exact authenticated APK bytes through Android ContentResolver; no installer is opened")
                runOnMainSync {
                    check(field(updates, "release") == null && field(updates, "verifier") == null)
                    check(!(field(updates, "saveButton") as Button).isEnabled)
                }
                pass("Export clears the private offer and session; it cannot silently replay or install")
                captureUpdateWindow(updates, "ui20-live-apk-saved")
            } finally {
                removeMonitor(picker)
                targetContext.contentResolver.delete(uri, null, null)
            }
        } else {
            runOnMainSync {
                check(!(field(updates, "downloadButton") as Button).isEnabled)
                check(!(field(updates, "saveButton") as Button).isEnabled)
                check((field(updates, "status") as TextView).text.toString().contains("already installed"))
            }
            pass("Current signed version reports already installed without downloading or enabling export")
            captureUpdateWindow(updates, "ui20-live-current-version")
        }
        runOnMainSync { updates.finish() }
        removeMonitor(monitor)
    }

    private fun captureUpdates() {
        val installed = AndroidUpdateSecurity.installed(targetContext)
        check(installed.signer == "fc5c0b42b9da40c2507d8f2e77ed92db9e023740aa1a44d1a3b2c14a6e9f4764")
        val snapshot = AndroidUpdateSecurity.inspect(targetContext, File(AndroidUpdateSecurity.packageInfo(targetContext).applicationInfo!!.sourceDir))
        check(snapshot.packageName == installed.packageName && snapshot.version == installed.version && snapshot.signers == setOf(installed.signer))
        pass("On-device apksig and PackageManager agree on installed APK identity and original signer")
        val directory = File(targetContext.cacheDir, "ui20-ledger-fixture")
        check(!directory.exists() || directory.listFiles().isNullOrEmpty())
        directory.mkdirs()
        val context = object : android.content.ContextWrapper(targetContext) {
            override fun getNoBackupFilesDir() = directory
        }
        val now = System.currentTimeMillis() / 1000
        val release = UpdateRelease(10, now - 60, now + 3600, installed.version + 1, "fixture-only", installed.packageName,
            "ai", "testing", 1, "a".repeat(64), installed.signer, "https://example.test/unused.apk", "b".repeat(64))
        val state = File(directory, "signed-updates/floor-v1")
        fun refused(action: () -> Unit) {
            var failed = false
            try { action() } catch (_: Exception) { failed = true }
            check(failed)
        }
        try {
            AndroidUpdateLedger(context).accept(release, installed, now)
            AndroidUpdateLedger(context).accept(release, installed, now)
            refused { AndroidUpdateLedger(context).accept(release.copy(sequence = 9), installed, now) }
            refused { AndroidUpdateLedger(context).accept(release.copy(payloadHash = "c".repeat(64)), installed, now) }
            pass("Atomic rollback floor survives ledger recreation and rejects older or equivocal metadata")
            state.writeText("damaged")
            refused { AndroidUpdateLedger(context).accept(release, installed, now) }
            check(state.readText() == "damaged")
            pass("Corrupt on-device rollback state fails closed without resetting the floor")
        } finally {
            // Only files within this explicitly owned disposable fixture are removed.
            state.parentFile!!.listFiles()?.forEach { check(it.isFile); it.delete() }
            state.parentFile!!.delete(); directory.delete()
        }
        val monitor = addMonitor(UpdateActivity::class.java.name, null, false)
        onMain { activity.startActivity(Intent(activity, UpdateActivity::class.java)) }
        val updates = waitForMonitorWithTimeout(monitor, 15000) ?: error("Update screen did not open")
        waitForIdleSync()
        runOnMainSync {
            val root = updates.window.decorView
            check(ProductionUpdateTrust.configured() != null)
            check(descendants(root).filterIsInstance<TextView>().none { it.text.toString() == UpdateActivity.NOT_PROVISIONED })
            check(field(updates, "verifier") == null && field(updates, "release") == null)
            check(!findButton(root, "Download & verify APK").isEnabled)
            check(!findButton(root, "Save verified APK…").isEnabled)
        }
        pass("Provisioned production screen opens without a network request; download/save remain disabled until an explicit authenticated check")
        captureUpdateWindow(updates, "ui20-updates-configured")
        pass("Actual update Activity window rendered and captured with the approved real public authority")
        runOnMainSync { updates.finish() }
        removeMonitor(monitor)
    }

    private fun captureUpdateWindow(updates: Activity, name: String) {
        // Capture the real Activity Window with PixelCopy; only this fixture clears FLAG_SECURE.
        val copied = CountDownLatch(1)
        var code = -1
        lateinit var bitmap: Bitmap
        runOnMainSync {
            val window = updates.window
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val decor = window.decorView
            bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.postDelayed({ PixelCopy.request(window, bitmap, { code = it; copied.countDown() }, Handler(Looper.getMainLooper())) }, 500)
        }
        check(copied.await(10, TimeUnit.SECONDS) && code == PixelCopy.SUCCESS)
        File(targetContext.filesDir, "ui12-rendered").apply { mkdirs() }.let { dir ->
            File(dir, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        bitmap.recycle()
    }

    private fun captureEvidenceAuto() {
        val vault = ApiKeyVault(activity.applicationContext)
        check(!vault.exists()) { "Disposable fixture must start without a remembered key" }
        val fixtureKey = "sk-ui19-keystore-fixture-not-a-real-key"
        vault.save(fixtureKey) // Instrumentation worker thread; never an actual credential.
        val host = HostSession("synthetic-fixture", 1, "Disposable evidence test target")
        val target = FixtureCommandTarget(host)
        var revision = 1L
        var auto: AutoAssistPanel? = null
        var screen: Dialog? = null
        var photos = 0
        var revisions = 0
        val observer = object : AutoObservationClient {
            override fun cancel() {}
            override suspend fun observe(goal: String, step: SuggestedCommand, target: String, model: String,
                key: String, jpeg: ByteArray, preflight: Boolean, collectionContext: String): AutoObservation {
                check(key == fixtureKey && jpeg.size > 100 && jpeg[0] == 0xff.toByte())
                photos++
                if (preflight) return AutoObservation("", "shell_prompt", "appears_complete", "An idle test terminal is visible.", listOf("Idle prompt."), false)
                val context = JSONObject(collectionContext)
                val marker = context.getString("expected_marker")
                val page = context.getInt("page")
                val overflow = context.getString("original_command").contains("bc-ui19-overflow")
                val state = when { page < 0 -> "cleaned"; page == 0 && overflow -> "truncated"; page == 0 -> "captured"; else -> "done" }
                return AutoObservation("", "shell_prompt", if (state == "truncated") "unclear" else "appears_complete",
                    if (state == "truncated") "The output is incomplete. I'll use a focused diagnostic before drawing a conclusion."
                    else "The account information gives us the context we need for the next decision.",
                    listOf("Synthetic complete operation marker and fresh prompt."), state == "truncated", marker, state,
                    if (page > 0) "uid=1000(test)" else "", "0")
            }
        }
        val planner = object : AutoPlanClient {
            override fun cancel() {}
            override suspend fun propose(goal: String, target: String, model: String, key: String): CommandPlan {
                check(key == fixtureKey && goal.contains("INCOMPLETE"))
                revisions++
                return CommandPlan("The long capture was incomplete. Check account context with a focused query.", emptyList(), emptyList(),
                    listOf(command("Check account context", "id")))
            }
        }
        try {
            onMain {
                PendingApiKey.clear(activity)
                activity.getSharedPreferences(TargetSystemPanel.PREFS, Activity.MODE_PRIVATE).edit()
                    .putString(AssistantNarrator.VOICE_KEY, NarrationVoice.TEXT.name).apply()
                val plan = CommandPlan("Inspect the diagnostic evidence.", emptyList(), emptyList(),
                    listOf(command("Read diagnostic output", "cat /tmp/bc-ui19-overflow"), command("Old next task", "whoami")))
                auto = AutoAssistPanel(activity, field(activity, "hid") as HidManager, plan, "Inspect this disposable Linux fixture",
                    "Linux / POSIX sh", PlanCodec.DEFAULT_MODEL, 0, { revision }, { error("Expected automatic diagnostic revision") },
                    { ++revision }, {}, observer, target, planner)
                auto!!.show(); screen = field(auto!!, "dialog") as Dialog
                screen!!.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                check(field(auto!!, "sessionKeyInput") == null)
                check(descendants(screen!!.window!!.decorView).filterIsInstance<Button>().none { it.text == "Use a temporary API key here" })
                pass("A remembered encrypted key removes temporary-key controls from Auto; opening it makes no API/HID request")
                check(photos == 0 && target.reports.isEmpty())
                (field(auto!!, "consentChoice") as android.widget.CheckBox).isChecked = true
                findButton(screen!!, "Start live camera preview").performClick()
            }
            await("UI19 real camera preview", 90000) { field(auto!!, "cameraStarted") == true && (field(auto!!, "previewFrames") as Int) >= 3 }
            screenshot("ui19-saved-key-auto-setup", screen)
            onMain { findButton(screen!!, "Start reviewed session").performClick() }
            await("UI19 diagnostic overflow and revision", 300000) { (field(auto!!, "session") as AutoRunSession?)?.stage == AutoStage.FINISHED }
            onMain {
                val session = field(auto!!, "session") as AutoRunSession
                check(session.history.tasks.map { it.id } == listOf(1, 2, 3))
                check(session.history.tasks.map { it.state } == listOf(AutoTaskState.UNCERTAIN, AutoTaskState.REPLACED, AutoTaskState.OBSERVED))
                check(revisions == 1 && photos == 6 && target.reports.count { it.second == 40 } == 5)
                check(session.transmitted.none { it == "whoami" })
                pass("Truncated evidence cannot become success: cleanup and a focused diagnostic replace the unstarted next task automatically")
                pass("Task identities survive helper operations and revision; uncertain, replaced and evidence-checked states remain distinct")
                check(field(auto!!, "key") == "" && field(auto!!, "camera") == null)
                check((field(auto!!, "phaseStatus") as TextView).text.toString().contains("finished"))
                check((field(auto!!, "autoToggle") as Button).text.toString().contains("OFF"))
                pass("Finished Auto reports OFF with cleared session key/camera and a truthful phase label")
            }
            check(vault.read() == fixtureKey)
            pass("Stopping the session preserves the remembered Keystore-encrypted key for later plans")
            screenshot("ui19-truncated-evidence-history", screen)
        } finally {
            onMain { auto?.dismiss() }
            vault.forget()
        }
    }

    private fun captureAutoTimeout() {
        val host = HostSession("synthetic-fixture", 1, "Synthetic timeout test target")
        val target = FixtureCommandTarget(host)
        var revision = 1L
        var handoff: AutoTaskHandoff? = null
        var calls = 0
        var cancelled = false
        val observer = object : AutoObservationClient {
            override fun cancel() { cancelled = true }
            override suspend fun observe(goal: String, step: SuggestedCommand, target: String, model: String,
                key: String, jpeg: ByteArray, preflight: Boolean, collectionContext: String): AutoObservation {
                check(jpeg.size > 100 && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte())
                calls++
                if (!preflight) return kotlinx.coroutines.withTimeout(1) {
                    delay(60000)
                    error("The synthetic provider must time out")
                }
                return AutoObservation("", "shell_prompt", "appears_complete", "An idle synthetic prompt.", listOf("Synthetic idle prompt."), false)
            }
        }
        lateinit var auto: AutoAssistPanel
        onMain {
            activity.getSharedPreferences(TargetSystemPanel.PREFS, Activity.MODE_PRIVATE).edit()
                .putString(AssistantNarrator.VOICE_KEY, NarrationVoice.TEXT.name).apply()
            PendingApiKey.put(activity, "sk-ui14-timeout-fixture-not-a-real-key")
            auto = AutoAssistPanel(activity, field(activity, "hid") as HidManager,
                CommandPlan("Synthetic timeout recovery.", emptyList(), emptyList(), listOf(command("Inspect account", "id"), command("Check account name", "whoami"))),
                "Inspect this disposable fixture", "Linux", PlanCodec.DEFAULT_MODEL, 0, { revision }, {}, { ++revision }, {},
                observer, target, onTaskHandoff = { handoff = it })
            auto.show()
            dialog = field(auto, "dialog") as Dialog
            dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            (field(auto, "consentChoice") as android.widget.CheckBox).isChecked = true
            findButton(dialog, "Start live camera preview").performClick()
        }
        await("timeout fixture camera preview") { field(auto, "cameraStarted") == true && (field(auto, "previewFrames") as Int) >= 3 }
        onMain { findButton(dialog, "Start reviewed session").performClick() }
        await("provider timeout stops Auto rather than leaving ON", timeoutMillis = 180000) {
            (field(auto, "session") as AutoRunSession?)?.stage == AutoStage.STOPPED
        }
        onMain {
            check(calls == 2 && cancelled)
            check(target.reports.count { it.second == 40 } == 1)
            check((field(auto, "status") as TextView).text.contains("timed out"))
            check((field(auto, "autoToggle") as Button).text.contains("OFF"))
            check(field(auto, "key") == "" && field(auto, "camera") == null)
            check(handoff!!.remaining.single().command == "whoami" && handoff!!.attemptedCount == 1)
            pass("A real TimeoutCancellationException during feedback turns Auto OFF, explains the error, clears camera/key state and never queues the attempted task again")
        }
        screenshot("auto-feedback-timeout", dialog)
        onMain { auto.dismiss() }
    }

    private class FixtureCommandTarget(private val host: HostSession) : CommandTarget {
        val reports = mutableListOf<Pair<Int, Int>>()
        override fun current() = host
        override fun sendKey(session: HostSession, modifier: Int, usage: Int): Boolean {
            check(session == host && host.id == "synthetic-fixture")
            reports.add(modifier to usage)
            return true
        }
    }

    private class FixtureAutoObserver : AutoObservationClient {
        var calls = 0
        var jpegCaptures = 0
        var firstResultGate: CompletableDeferred<Unit>? = null
        override fun cancel() {}
        override suspend fun observe(goal: String, step: SuggestedCommand, target: String, model: String,
                                     key: String, jpeg: ByteArray, preflight: Boolean, collectionContext: String): AutoObservation {
            check(key.startsWith("sk-ui12-synthetic") && jpeg.size > 100 && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte())
            calls++
            jpegCaptures++
            if (!preflight && calls == 2) firstResultGate?.await()
            delay(300)
            if (collectionContext.isNotEmpty()) {
                val context = org.json.JSONObject(collectionContext)
                val page = context.getInt("page")
                val original = context.getString("original_command")
                val state = if (page < 0) "cleaned" else if (page == 0) "captured" else if (original == "id" && page == 1) "more" else "done"
                return AutoObservation("", "shell_prompt", "appears_complete", "Synthetic numbered output page.",
                    listOf("Synthetic page markers and idle prompt"), original == "id" && page == 2,
                    context.getString("expected_marker"), state, if (page == 1) "FIRST PAGE" else if (page == 2) "SECOND PAGE" else "", "0")
            }
            return AutoObservation(if (preflight) "" else step.command, "shell_prompt", "appears_complete",
                "Synthetic observation: the fixture terminal appears ready.", listOf("Synthetic visible evidence fixture."), false)
        }
    }
    private fun captureFullKaliWorkflows() {
        val data = OfflineLearningCatalog.load(targetContext)
        check(data.workflows.kaliToolIds.size == 820 && data.workflows.kaliEntryIds.size == 3055)
        for (id in data.workflows.kaliEntryIds) {
            val entry = data.entryById[id]!!
            val guide = OfflineWorkflows.commandGuide(data, entry)
            check(guide.commands.size in 3..4 && guide.commands.all { CommandPolicy.problem(it.command) == null })
            check(OfflineWorkflows.prepare(entry, listOf("literal argument")).risk == LinuxCommandRisk.HIGH)
        }
        for (id in data.workflows.kaliToolIds) {
            val tool = data.toolById[id]!!
            check(tool.packages.isNotEmpty())
            check(OfflineWorkflows.packageGuide(tool, tool.packages.first()).commands.size == 3)
        }
        pass("Android production parser and preparation cover all 820 Kali tools and 3055 commands without executing inventoried programs")
        lateinit var library: LinuxCommandLibraryPanel
        lateinit var screen: Dialog
        var selections = 0
        onMain {
            library = LinuxCommandLibraryPanel(activity, field(activity, "hid") as HidManager, onSelectForPlan = { selections++ })
            library.show(); screen = field(library, "dialog") as Dialog
        }
        await("full workflow library") { (field(library, "items") as List<*>).size == 467 }
        fun nav(title: String) = descendants(screen.window!!.decorView).first {
            it.isShown && it.isClickable && it.contentDescription?.toString() == title
        }.performClick()
        onMain { nav("Kali Linux"); nav("All Kali command workflows") }
        screenshot("workflows-01-kali-command-list", screen)
        onMain {
            scrollBottom(screen); findButton(screen, "Next page").performClick()
            check(field(library, "cliPage") == 1)
            (field(library, "search") as EditText).setText("git add")
            check((field(library, "level") as Enum<*>).name == "CLI_LIST")
            check(field(library, "cliPage") == 0)
        }
        await("asynchronous scoped command search") { descendants(screen.window!!.decorView).any { it.isShown && it.contentDescription == "git add" } }
        onMain {
            nav("git add")
            check(selections == 0)
            pass("The complete Kali command directory paginates and filters in scope; selecting a subcommand sends nothing")
            findButton(screen, "Learn this command").performClick()
            val step = (field(library, "selectedLesson") as OfflineLesson).steps.first()
            check((field(library, "guideCommands") as Map<*, *>)[step.commandId].let { (it as LinuxCommandItem).command } == "command -v git")
        }
        screenshot("workflows-02-subcommand-guide", screen)
        onMain { findButton(screen, "Build a task command").performClick() }
        val composer = field(library, "composer") as OfflineCommandComposer
        val editor = field(composer, "dialog") as Dialog
        onMain {
            findButton(editor, "Add argument").performClick()
            (field(composer, "arguments") as List<*>).let { (it[0] as EditText).setText("a file's name") }
            findButton(editor, "Add argument").performClick()
            (field(composer, "arguments") as List<*>).let { (it[1] as EditText).setText("\$(id); echo literal") }
            check((field(composer, "preview") as TextView).text.toString() == "git add 'a file'\\''s name' '\$(id); echo literal'")
            check(field(library, "busy") == false && field(library, "confirmation") == null && selections == 0)
            pass("Task builder preserves spaces, apostrophes and shell symbols as literal arguments without approval or input")
        }
        screenshot("workflows-03-literal-argument-builder", editor)
        onMain {
            val input = (field(composer, "arguments") as List<*>)[1] as EditText
            input.setText("x".repeat(513))
            check(!(field(composer, "review") as Button).isEnabled)
            input.setText("literal")
            findButton(editor, "Review exact command").performClick()
            check((field(library, "selectedItem") as LinuxCommandItem).risk == LinuxCommandRisk.HIGH)
            check(!findButton(screen, "Connect Bluetooth first").isEnabled)
            check((field(composer, "arguments") as List<*>).isEmpty())
            check(selections == 0)
            pass("Oversized tasks are blocked; preparing a valid task clears editor fields and opens HIGH-risk TYPE ONLY review")
        }
        screenshot("workflows-04-prepared-command", screen)
        onMain {
            (field(library, "search") as EditText).text.clear()
            setField(library, "selectedTool", data.toolById["bettercap-ui"]!!)
            setEnum(library, "level", "TOOL"); invoke(library, "render")
            findButton(screen, "Inspect package").performClick()
            nav("bettercap-ui")
            check((field(library, "selectedLesson") as OfflineLesson).steps.size == 3)
            check((field(library, "guideCommands") as Map<*, *>).values.all { (it as LinuxCommandItem).command.startsWith("dpkg-query ") })
            check(selections == 0)
            pass("A package-only collection has a complete installed-version/status/files guide without an invented executable")
        }
        screenshot("workflows-05-package-only-guide", screen)
        onMain {
            library.dismiss()
            panel = Class.forName("com.blackcat.remote.AiPanel").declaredConstructors.single().apply { isAccessible = true }
                .newInstance(activity, field(activity, "hid") as HidManager, {})
            invoke(panel, "show"); dialog = field(panel, "dialog") as Dialog
            invoke(panel, "openCommandTree")
            library = field(panel, "libraryPanel") as LinuxCommandLibraryPanel
            screen = field(library, "dialog") as Dialog
        }
        await("AI guide selection library") { (field(library, "items") as List<*>).size == 467 }
        onMain {
            setField(library, "selectedTool", data.toolById["bettercap-ui"]!!)
            setEnum(library, "level", "TOOL"); invoke(library, "render")
            findButton(screen, "Inspect package").performClick(); nav("bettercap-ui")
            findButton(screen, "Add complete guide").performClick()
            check(workspace().plan!!.commands.map { it.command } == listOf("dpkg-query -W -- bettercap-ui", "dpkg-query -s -- bettercap-ui | head -n 40", "dpkg-query -L -- bettercap-ui | head -n 40"))
            check(workspace().confirmedSteps.isEmpty())
            check((field(panel, "activeTab") as Enum<*>).name == "PLAN")
            pass("A complete guide enters the real AI plan in order through one atomic local edit with no confirmed results")
            invoke(panel, "openCommandTree")
            library = field(panel, "libraryPanel") as LinuxCommandLibraryPanel
            screen = field(library, "dialog") as Dialog
        }
        screenshot("workflows-06-guide-in-plan", dialog)
        await("stale guide library") { (field(library, "items") as List<*>).size == 467 }
        onMain {
            check(field(panel, "libraryPanel") === library) { "A late close callback cleared the newly opened library" }
            setField(library, "selectedTool", data.toolById["bettercap-ui"]!!)
            setEnum(library, "level", "TOOL"); invoke(library, "render")
            findButton(screen, "Inspect package").performClick(); nav("bettercap-ui")
            workspace().touch()
            findButton(screen, "Add complete guide").performClick()
            check(workspace().plan!!.commands.size == 3)
            pass("A stale guide callback cannot change a revised plan or create a typing approval")
            invoke(panel, "dismiss")
        }
        val original = android.content.res.Configuration(activity.resources.configuration)
        val metrics = activity.resources.displayMetrics
        for (width in listOf(320, 412)) for (scale in listOf(1f, 2f)) {
            lateinit var editorPanel: OfflineCommandComposer
            lateinit var editorWindow: Dialog
            onMain {
                val config = android.content.res.Configuration(original).apply { fontScale = scale }
                activity.resources.updateConfiguration(config, metrics)
                val entry = if (width == 320 && scale == 2f)
                    data.entries.filter { it.id in data.workflows.kaliEntryIds }.maxBy { it.invocation.length }
                    else data.entries.first { it.invocation == "git add" }
                editorPanel = OfflineCommandComposer(activity, entry, {}, {})
                editorPanel.show(); editorWindow = field(editorPanel, "dialog") as Dialog
                editorWindow.window!!.setLayout((width * metrics.density).toInt(), (500 * metrics.density).toInt())
                findButton(editorWindow, "Add argument").performClick()
                ((field(editorPanel, "arguments") as List<*>)[0] as EditText).setText("sample file.txt")
            }
            screenshot("workflows-matrix-$width-${(scale * 100).toInt()}", editorWindow)
            onMain {
                fullyVisible(field(editorPanel, "review") as Button, 48)
                check((field(editorPanel, "review") as Button).isEnabled)
                editorPanel.dismiss()
                check((field(editorPanel, "arguments") as List<*>).isEmpty())
                pass("Command builder $width dp / font $scale keeps exact review visible in a reduced-height viewport and clears drafts on close")
            }
        }
        onMain { activity.resources.updateConfiguration(original, metrics) }
    }

    private fun captureOfflineLearning() {
        lateinit var library: LinuxCommandLibraryPanel
        lateinit var screen: Dialog
        var selected = 0
        val started = SystemClock.elapsedRealtime()
        onMain {
            library = LinuxCommandLibraryPanel(activity, field(activity, "hid") as HidManager,
                onSelectForPlan = { selected++ })
            library.show()
            screen = field(library, "dialog") as Dialog
        }
        try {
            await("cold offline learning data", timeoutMillis = 60000) { (field(library, "items") as List<*>).size == 467 }
        } catch (error: IllegalStateException) {
            // Report a packaged-asset/parser failure directly if the UI dismissed
            // its loading view. Do not warm the cache before measuring cold open.
            OfflineLearningCatalog.load(targetContext)
            LinuxCommandCatalog.load(targetContext)
            throw error
        }
        val loadMillis = SystemClock.elapsedRealtime() - started
        onMain {
            val data = field(library, "learning") as OfflineLearningData
            check(data.tools.size == 824 && data.lessons.size == 35)
            check(data.entries.count { it.kind in setOf("binary", "subcommand", "shell_builtin") } == 3448)
            check(data.commands.take(212).map { it.id }.distinct().size == 212)
            check(selected == 0)
            pass("Cold bundled catalogue loads 467 examples, 824 tool groups and 3448 CLI references without network or selection")
        }
        screenshot("learning-01-home", screen)
        fun nav(title: String) = descendants(screen.window!!.decorView).first {
            it.isShown && it.isClickable && it.contentDescription?.toString() == title
        }.performClick()
        onMain { nav("Kali Linux") }
        screenshot("learning-02-kali", screen)
        onMain {
            check(texts(screen).any { it == "Kali Linux tool directory" })
            nav("Kali Linux tool directory")
            check(field(library, "kaliToolsOnly") == true)
            check((field(library, "level") as Enum<*>).name == "TOOLS")
            check(selected == 0)
            pass("Kali has a dedicated offline menu and source-filtered tool directory")
        }
        screenshot("learning-03-tools", screen)
        onMain {
            scrollBottom(screen)
            findButton(screen, "Next page").performClick()
            check(field(library, "toolPage") == 1)
            pass("Tool browsing is paginated instead of creating thousands of phone views")
        }
        screenshot("learning-04-page-two", screen)
        onMain {
            val data = field(library, "learning") as OfflineLearningData
            setField(library, "selectedTool", data.toolById["nmap"]!!)
            setField(library, "entryPage", 0)
            setEnum(library, "level", "TOOL")
            invoke(library, "render")
        }
        screenshot("learning-05-tool-source", screen)
        onMain { findButton(screen, "Learn this tool").performClick() }
        screenshot("learning-06-tool-guide", screen)
        onMain {
            val lesson = field(library, "selectedLesson") as OfflineLesson
            check(lesson.steps.size in 3..4)
            nav("1. Locate nmap")
            check((field(library, "selectedItem") as LinuxCommandItem).command == "command -v nmap")
            check(!findButton(screen, "Connect Bluetooth first").isEnabled)
            findButton(screen, "Next explanation").performClick()
            check((field(library, "selectedItem") as LinuxCommandItem).command == "man -f -- nmap")
            check(selected == 0)
            pass("Tool discovery links availability to local documentation without scanning or typing")
        }
        screenshot("learning-07-tool-next", screen)
        onMain {
            val data = field(library, "learning") as OfflineLearningData
            setField(library, "selectedCli", data.entries.first { it.invocation == "nmap" })
            setEnum(library, "level", "CLI")
            invoke(library, "render")
            check(texts(screen).contains("Documented options"))
            check(descendants(screen.window!!.decorView).filterIsInstance<Button>().none {
                it.isShown && it.text.toString().startsWith("Approve & type")
            })
            check(selected == 0)
            pass("CLI references show documented options while creating no command approval")
        }
        screenshot("learning-08-command-reference", screen)
        onMain {
            (field(library, "guideCommands") as MutableMap<*, *>).clear()
            val data = field(library, "learning") as OfflineLearningData
            setField(library, "selectedLesson", data.lessonById["workspace"]!!)
            setEnum(library, "level", "LESSON")
            invoke(library, "render")
        }
        screenshot("learning-09-lesson-overview", screen)
        onMain {
            nav("1. Check practice path is unused")
            val before = (field(library, "selectedItem") as LinuxCommandItem).command
            findButton(screen, "Next explanation").performClick()
            val after = (field(library, "selectedItem") as LinuxCommandItem).command
            check(before == "test ! -e /tmp/blackcat-offline-lab" && after == "mkdir -m 700 /tmp/blackcat-offline-lab")
            check(selected == 0 && field(library, "busy") == false)
            check(texts(screen).contains("If the result differs"))
            check(texts(screen).any { it.contains("not proof this command is installed") })
            pass("Lesson Next changes only the explanation; expected evidence and stop guidance remain visible")
        }
        screenshot("learning-10-next-explanation", screen)
        val metrics = activity.resources.displayMetrics
        val originalConfig = android.content.res.Configuration(activity.resources.configuration)
        for (width in listOf(320, 412)) for (scale in listOf(1f, 2f)) {
            onMain {
                val config = android.content.res.Configuration(originalConfig).apply { fontScale = scale }
                activity.resources.updateConfiguration(config, metrics)
                library.dismiss()
                library = LinuxCommandLibraryPanel(activity, field(activity, "hid") as HidManager)
                library.show(); screen = field(library, "dialog") as Dialog
                screen.window!!.setLayout((width * metrics.density).toInt(), (780 * metrics.density).toInt())
            }
            await("learning layout data $width/$scale") { (field(library, "items") as List<*>).size == 467 }
            onMain {
                val data = field(library, "learning") as OfflineLearningData
                setField(library, "selectedLesson", data.lessonById["kali-readiness"]!!)
                setEnum(library, "level", "LESSON"); invoke(library, "render")
            }
            screenshot("learning-matrix-$width-${(scale * 100).toInt()}-overview", screen)
            onMain {
                val data = field(library, "learning") as OfflineLearningData
                val first = (field(library, "selectedLesson") as OfflineLesson).steps.first()
                nav("1. ${data.commandById[first.commandId]!!.title}")
            }
            screenshot("learning-matrix-$width-${(scale * 100).toInt()}-step", screen)
            onMain {
                check((field(library, "headerHero") as View).visibility == View.GONE)
                check((field(library, "contentScroll") as View).height >= (80 * metrics.density).toInt())
                scrollBottom(screen)
            }
            await("lesson approval scroll settled $width/$scale") {
                val type = findButton(screen, "Connect Bluetooth first")
                val visible = Rect()
                type.getGlobalVisibleRect(visible) && visible.height() >= type.height
            }
            screenshot("learning-matrix-$width-${(scale * 100).toInt()}-approval", screen)
            onMain {
                val type = findButton(screen, "Connect Bluetooth first")
                fullyVisible(type, 48)
                check(!type.isEnabled)
                val body = field(library, "contentBody") as ViewGroup
                val rect = Rect()
                body.getGlobalVisibleRect(rect)
                for (view in descendants(body).filterIsInstance<TextView>().filter { it.isShown && it.width > 0 }) {
                    val layout = view.layout ?: continue
                    for (line in 0 until layout.lineCount) check(layout.getEllipsisCount(line) == 0)
                }
                pass("Offline lesson $width dp / font $scale: no cat above task, readable viewport and visible single disabled approval")
            }
        }
        onMain {
            library.dismiss()
            activity.resources.updateConfiguration(originalConfig, metrics)
            val cancelled = LinuxCommandLibraryPanel(activity, field(activity, "hid") as HidManager)
            cancelled.show(); cancelled.dismiss()
            check(!(field(cancelled, "dialog") as Dialog).isShowing)
            check(selected == 0)
            pass("Closing a loading library cancels its UI scope without selecting, typing or reopening")
        }
        File(targetContext.filesDir, "ui15-learning-metrics.json").writeText(
            org.json.JSONObject().put("cold_open_ms_software_emulator", loadMillis).put("checks", checks.size)
                .put("phone_network_used", false).put("physical_hid_tested", false).toString(2))
    }

    private fun captureSearchAndStreaming() {
        lateinit var library: LinuxCommandLibraryPanel
        lateinit var screen: Dialog
        var selections = 0
        onMain {
            activity.getSharedPreferences("blackcat_ai_ui", Activity.MODE_PRIVATE).edit().putString("target_system", "Ubuntu Linux / Bash").apply()
            library = LinuxCommandLibraryPanel(activity, field(activity, "hid") as HidManager, onSelectForPlan = { selections++ })
            library.show(); screen = field(library, "dialog") as Dialog
        }
        await("offline search index", timeoutMillis = 60000) { (field(library, "items") as List<*>).size == 467 }
        onMain { (field(library, "search") as EditText).setText("I want to check all internet connections") }
        await("natural language topic results") { texts(screen).any { it.contains("matching topics") } }
        screenshot("search-stream-01-topics", screen)
        onMain {
            val hits = field(library, "searchHits") as List<*>
            check(hits.isNotEmpty() && selections == 0)
            val topic = (hits.first() as OfflineCommandSearch.Hit).topic
            descendants(screen.window!!.decorView).first { it.isClickable && it.contentDescription == topic }.performClick()
            check(field(library, "searchTopic") == topic)
            pass("Natural language returns OS-scoped topics and opening a topic creates no command approval")
        }
        screenshot("search-stream-02-topic-commands", screen)
        onMain {
            invoke(library, "navigateBack")
            (field(library, "search") as EditText).setText("nmap")
        }
        await("Ubuntu scope nmap search") { (field(library, "resultKey") as String).endsWith("\nnmap") }
        onMain {
            check((field(library, "searchHits") as List<*>).none { (it as OfflineCommandSearch.Hit).title == "nmap" })
            findButton(screen, "Search OS:").performClick()
        }
        // Exercise the visible OS chooser, not a synthetic preference write.
        onMain {
            val chooser = field(library, "osChooser") as AlertDialog
            chooser.listView.performItemClick(chooser.listView.getChildAt(0), 0, 0)
        }
        await("Kali scope nmap search") { (field(library, "resultKey") as String) == "Kali Linux\nnmap" }
        screenshot("search-stream-03-kali-scope", screen)
        onMain {
            check((field(library, "searchHits") as List<*>).any { (it as OfflineCommandSearch.Hit).title == "nmap" })
            check(selections == 0)
            pass("Visible OS selector changes compatibility scope; Kali references appear without network, typing or plan edits")
            library.dismiss()
            val type = Class.forName("com.blackcat.remote.AiPanel")
            panel = type.declaredConstructors.single().apply { isAccessible = true }.newInstance(activity, field(activity, "hid") as HidManager, {})
            invoke(panel, "show"); dialog = field(panel, "dialog") as Dialog
            dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            (field(panel, "goal") as EditText).setText("Check local internet connections")
            workspace().replace(CommandPlan("Previous plan", emptyList(), emptyList(), listOf(
                SuggestedCommand("Previous step", "id", "Read account.", "low", false, "Account facts", emptyList()))))
            setField(panel, "busy", true); setField(panel, "liveDraft", PlanStreamDraft()); setField(panel, "liveUpdating", "step 1: local network")
            (field(panel, "livePlan") as LivePlanView).reset()
            (field(panel, "livePlan") as LivePlanView).update(PlanStreamDraft(), "step 1: local network")
            invoke(panel, "renderAll")
        }
        val operation = field(panel, "operationToken") as Long
        val revision = workspace().revision
        val showDraft = panel.javaClass.getDeclaredMethod("showLiveDraft", PlanStreamDraft::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
        val proposal = """{"explanation":"First, we will inspect local connections without changing your network settings. Each command has its own explanation.","roadmap":[{"title":"Inspect connections","purpose":"Identify listeners and active sessions.","checks":["Read the output before choosing changes."]}],"assumptions":[],"decision":{"mode":"commands","questions":[],"commands":[{"title":"Check connections","command":"ss -tupn","explanation":"Show active TCP and UDP connections and their processes, where permitted.","risk":"low","requires_admin":false,"expected_result":"A bounded list of current connections.","warnings":[]},{"title":"Check interface addresses","command":"ip -brief address","explanation":"Show addresses assigned to this computer's network interfaces.","risk":"low","requires_admin":false,"expected_result":"Interface names and addresses.","warnings":[]}]}}"""
        fun event(type: String, key: String, value: Any) = "data: " + org.json.JSONObject().put("type", type).put(key, value).apply {
            if (type.endsWith(".delta")) { put("output_index", 0); put("content_index", 0) }
        }.toString() + "\n\n"
        val finalResponse = org.json.JSONObject().put("status", "completed").put("output", org.json.JSONArray().put(org.json.JSONObject()
            .put("type", "message").put("content", org.json.JSONArray().put(org.json.JSONObject().put("type", "output_text").put("text", proposal)))))
        val wire = proposal.chunked(90).joinToString("") { event("response.output_text.delta", "delta", it) } + event("response.completed", "response", finalResponse)
        var captured = false
        val input = object : java.io.ByteArrayInputStream(wire.toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { Thread.sleep(15); return super.read(buffer, offset, minOf(64, length)) }
        }
        val parsed = kotlinx.coroutines.runBlocking {
            PlanResponseStream.read(input) { draft ->
                onMain {
                    showDraft.invoke(panel, draft, operation, revision)
                    check(workspace().plan!!.commands.single().command == "id")
                    check(!findButton(dialog, "▶ Auto Play").isEnabled)
                    check(descendants(dialog.window!!.decorView).filterIsInstance<Button>().none { it.isShown && it.isEnabled && it.text.contains("Approve") })
                }
                if (!captured && draft.commands.isNotEmpty() && draft.commands.first().command.isNotEmpty()) {
                    screenshot("search-stream-04-live-draft", dialog); captured = true
                }
            }
        }
        check(captured && parsed.commands.size == 2)
        onMain {
            check(texts(dialog).any { it.contains("LIVE DRAFT") })
            check(texts(dialog).any { it == "ss -tupn" })
            pass("Actual SSE parser renders progressive explanation and monospace command cards while the previous workspace remains intact and approvals stay disabled")
            findButton(dialog, "Edit goal").performClick()
            check((field(panel, "goalStage") as View).isShown)
            showDraft.invoke(panel, PlanStreamDraft("STALE DRAFT MUST NOT APPEAR"), operation, revision)
            check(field(panel, "liveDraft") == null)
            check(workspace().plan!!.commands.single().command == "id")
            check(texts(dialog).none { it.contains("STALE DRAFT") })
            pass("Cancellation discards the live draft and rejects a late callback without replacing the old plan")
            workspace().replace(parsed); setField(panel, "goalExpanded", false); invoke(panel, "renderAll")
        }
        screenshot("search-stream-05-validated-plan", dialog)
        val original = android.content.res.Configuration(activity.resources.configuration)
        val metrics = activity.resources.displayMetrics
        for (width in listOf(320, 360, 390, 412)) for (scale in listOf(1f, 2f)) {
            onMain {
                invoke(panel, "dismiss")
                val config = android.content.res.Configuration(original).apply { fontScale = scale }
                activity.resources.updateConfiguration(config, metrics)
                val type = Class.forName("com.blackcat.remote.AiPanel")
                panel = type.declaredConstructors.single().apply { isAccessible = true }.newInstance(activity, field(activity, "hid") as HidManager, {})
                invoke(panel, "show"); dialog = field(panel, "dialog") as Dialog
                dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.window!!.setLayout((width * metrics.density).toInt(), (780 * metrics.density).toInt())
                (field(panel, "goal") as EditText).setText("Inspect current network connections")
                setField(panel, "busy", true); setField(panel, "liveDraft", PlanDraftPreview.read(proposal))
                val live = field(panel, "livePlan") as LivePlanView
                live.reset(); live.update(PlanDraftPreview.read(proposal), "local network")
                invoke(panel, "renderAll")
                live.followLatest()
            }
            screenshot("search-stream-matrix-$width-${(scale * 100).toInt()}", dialog)
            onMain {
                fullyVisible(findButton(dialog, "Cancel"), 48)
                fullyVisible(findButton(dialog, "▶ Auto Play"), 48)
                check((field(panel, "planView") as View).height >= (80 * metrics.density).toInt())
                check(descendants(field(panel, "livePlan") as View).filterIsInstance<TextView>().any { it.typeface == android.graphics.Typeface.MONOSPACE })
                pass("Live plan $width dp / font $scale retains visible Cancel, Auto/Voice and separate command formatting")
            }
        }
        onMain { invoke(panel, "dismiss"); activity.resources.updateConfiguration(original, metrics) }
    }

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
    private fun await(name: String, timeoutMillis: Long = 30000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
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
                val output = File(targetContext.filesDir, "ui12-rendered").apply { mkdirs() }
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
