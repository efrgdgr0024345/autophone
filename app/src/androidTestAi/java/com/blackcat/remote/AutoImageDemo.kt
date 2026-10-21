package com.blackcat.remote

import android.app.Activity
import android.app.Dialog
import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Emulator-only, operator-reviewed images. No OpenAI key, network client or shell on Android.
 * The external test driver decodes our HID text, executes it on a disposable Linux host,
 * renders stdout into the emulator video camera, and exchanges reviewed model responses.
 * This deliberately does NOT claim to verify the production OpenAI network/model boundary.
 */
internal class AutoImageDemo(private val instrumentation: Instrumentation, private val activity: Activity,
                             private val injectedFrames: Boolean = false, private val runId: String = "") {
    private val folder = File(activity.filesDir, "image-auto-demo")
    private lateinit var auto: AutoAssistPanel
    private lateinit var dialog: Dialog
    private var revision = 1L
    private var requestNumber = 0
    private var commandNumber = 0
    private var observations = 0
    private var revisions = 0
    private val host = HostSession("image-demo", 1, "Disposable Linux demo (test bridge)")
    private val line = StringBuilder()
    private val chars = (32..126).map { it.toChar() }.associateBy { HidReports.char(it)!! }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (e: Throwable) { failure = e } }
        failure?.let { throw it }
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun button(text: String) = views(dialog.window!!.decorView).filterIsInstance<Button>().first { it.text.toString() == text }
    private fun await(name: String, timeout: Long = 60000, ready: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            var done = false
            main { done = ready() }
            if (done) return
            Thread.sleep(200)
        }
        error("Image demo timeout: $name")
    }
    private fun write(name: String, text: String) {
        val temporary = File(folder, "$name.tmp")
        temporary.writeText(text)
        check(temporary.renameTo(File(folder, name)))
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val latch = CountDownLatch(1)
        var failure = ""
        main {
            val decor = dialog.window!!.decorView
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(dialog.window!!, bitmap, { result ->
                try {
                    if (result != PixelCopy.SUCCESS) failure = "PixelCopy: $result"
                    else File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle(); latch.countDown() }
            }, Handler(Looper.getMainLooper()))
        }
        check(latch.await(30, TimeUnit.SECONDS) && failure.isEmpty()) { failure }
    }
    private suspend fun exchange(kind: String, request: JSONObject, jpeg: ByteArray? = null): String = withContext(Dispatchers.IO) {
        val n = ++requestNumber
        val id = "%03d".format(n)
        if (jpeg != null) File(folder, "photo-$id.jpg").writeBytes(jpeg)
        write("request-$id.json", request.put("kind", kind).put("id", id).toString(2))
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "IMAGE_DEMO_REQUEST $id $kind\n") })
        val reply = File(folder, "reply-$id.json")
        while (!reply.exists()) delay(150)
        reply.readText()
    }

    fun run() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        folder.deleteRecursively()
        check(folder.mkdirs())
        if (runId.isNotBlank()) File(folder, "run-id").writeText(runId)
        val observer = object : AutoObservationClient {
            override fun cancel() {}
            override suspend fun observe(goal: String, step: SuggestedCommand, target: String, model: String,
                                         key: String, jpeg: ByteArray, preflight: Boolean, collectionContext: String): AutoObservation {
                check(key == "sk-image-demo-not-a-real-key")
                check(jpeg.size > 100 && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte())
                observations++
                val image = if (!injectedFrames) jpeg else withContext(Dispatchers.IO) {
                    val expected = if (preflight) "" else step.command
                    val ready = File(folder, "frame-ready.json")
                    while (!ready.exists() || JSONObject(ready.readText()).getString("command") != expected) delay(150)
                    File(folder, "frame.jpg").readBytes()
                }
                // Build the REAL production request, excluding credentials, to check the image/wire contract too.
                val body = AutoObservationCodec.request(goal, step, target, model, image, preflight, collectionContext)
                val data = JSONObject().put("preflight", preflight).put("command", step.command)
                    .put("collection_context", collectionContext).put("goal", goal)
                    .put("production_instructions", body.getString("instructions"))
                    .put("production_schema", body.getJSONObject("text").getJSONObject("format").getJSONObject("schema"))
                return try { AutoObservationCodec.parseResponse(exchange("observation", data, image)) }
                    finally { if (injectedFrames) image.fill(0) }
            }
        }
        val planner = object : AutoPlanClient {
            override fun cancel() {}
            override suspend fun propose(goal: String, target: String, model: String, key: String): CommandPlan {
                check(key == "sk-image-demo-not-a-real-key")
                revisions++
                val body = PlanCodec.request(goal, target, model)
                return PlanCodec.parseResponse(exchange("plan", JSONObject().put("context", goal)
                    .put("production_instructions", body.getString("instructions"))))
            }
        }
        val target = object : CommandTarget {
            override fun current() = host
            override fun sendKey(session: HostSession, modifier: Int, usage: Int): Boolean {
                check(session == host)
                if (usage == 0) return true
                if (usage == 40) {
                    val id = "%03d".format(++commandNumber)
                    write("command-$id.json", JSONObject().put("command", line.toString()).toString())
                    line.clear()
                } else line.append(chars[usage to modifier] ?: error("Unsupported demo HID input"))
                return true
            }
        }
        fun step(title: String, command: String, expected: String) = SuggestedCommand(title, command,
            "Read the diagnostic evidence before choosing the next step.", "low", false, expected, emptyList())
        val initial = CommandPlan("Inspect a long service log, then check identity. Adapt if the log points elsewhere.",
            listOf("Disposable Linux demo; vision and planning supplied by the reviewing assistant."), emptyList(),
            listOf(step("Inspect service diagnostic log", "cat /tmp/blackcat-image-demo.log", "Find why the demo service failed."),
                step("Check account name", "whoami", "Display the current account name.")))
        try {
            main {
                activity.getSharedPreferences(TargetSystemPanel.PREFS, Activity.MODE_PRIVATE).edit()
                    .putString(AssistantNarrator.VOICE_KEY, NarrationVoice.TEXT.name).apply()
                PendingApiKey.put(activity, "sk-image-demo-not-a-real-key")
                auto = AutoAssistPanel(activity, field(activity, "hid") as HidManager, initial,
                    "Diagnose why the disposable demo service failed; collect all relevant output and adapt the plan.",
                    "Linux / POSIX sh", PlanCodec.DEFAULT_MODEL, 0, { revision },
                    { error("Demo should adapt without manual approval") }, { plan ->
                        write("visible-plan-%03d.json".format(++revision), JSONObject().put("commands", JSONArray(plan.commands.map {
                            JSONObject().put("title", it.title).put("command", it.command)
                        })).toString(2))
                        revision
                    }, {}, observer, target, planner)
                auto.show()
                dialog = field(auto, "dialog") as Dialog
                dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                (field(auto, "consentChoice") as CheckBox).isChecked = true
                button("Start live camera preview").performClick()
            }
            await("camera frames") { field(auto, "cameraStarted") == true && (field(auto, "previewFrames") as Int) >= 3 }
            screenshot("01-camera-ready")
            main { button("Start reviewed session").performClick() }
            var lastStage = ""
            await("full image-feedback cycle", 1500000) {
                val session = field(auto, "session") as AutoRunSession?
                val stage = "${session?.index}-${session?.stage}"
                if (stage != lastStage) {
                    lastStage = stage
                    write("stage.json", JSONObject().put("stage", stage).put("observations", observations).put("commands", commandNumber).toString())
                }
                check(session?.stage !in setOf(AutoStage.STOPPED, AutoStage.BLOCKED)) {
                    "Auto stopped: $stage; ${(field(auto, "status") as TextView).text}"
                }
                session?.stage == AutoStage.FINISHED
            }
            screenshot("02-plan-finished")
            main {
                val session = field(auto, "session") as AutoRunSession
                check(revisions >= 1 && session.transmitted.contains("cat /etc/os-release"))
                check(!session.transmitted.contains("whoami"))
                check(session.history.tasks.map { it.state } == listOf(AutoTaskState.FAILED, AutoTaskState.REPLACED, AutoTaskState.OBSERVED))
                check(field(auto, "camera") == null && field(auto, "key") == "")
                write("result.json", JSONObject().put("passed", true).put("observations", observations)
                    .put("commands_executed", commandNumber).put("plan_revisions", revisions)
                    .put("task_states", JSONArray(session.history.tasks.map { it.state.name }))
                    .put("injected_vision_frames", injectedFrames)
                    .put("app_openai_network_tested", false).put("physical_bluetooth_tested", false).toString(2))
            }
        } finally {
            if (::auto.isInitialized) main { auto.dismiss() }
        }
    }
}
