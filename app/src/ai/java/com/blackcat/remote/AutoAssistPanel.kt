package com.blackcat.remote

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicit foreground camera/AI session around the existing HID observer and reviewed sender. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class AutoAssistPanel(
    private val activity: Activity, manager: HidManager,
    private val plan: CommandPlan, private val originalGoal: String, private val targetSystem: String,
    private val model: String, private val startIndex: Int, private val revision: () -> Long,
    private val onProposal: (CommandPlan) -> Unit, private val onAutomaticPlan: (CommandPlan) -> Long, private val onClosed: () -> Unit,
    private val observer: AutoObservationClient = OpenAiAutoObserver(),
    commandTargetOverride: CommandTarget? = null
) {
    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transport = AiTransportScope(activity, manager) { dismiss() }
    private val target: CommandTarget = commandTargetOverride ?: transport
    private val vault = ApiKeyVault(activity.applicationContext)
    private val planner = OpenAiPlanner()
    private val narrator = AssistantNarrator(activity) { message -> voiceStatus.text = message }
    private var session: AutoRunSession? = null
    private var work: Job? = null
    private var camera: PhotoCaptureController? = null
    private var cameraGeneration = 0L
    private var previewFrames = 0
    private var photoWaiter: CancellableContinuation<ByteArray>? = null
    private var key = ""
    private var closed = false
    private var cameraStarted = false
    private var observation: AutoObservation? = null
    private var muted = false
    private var revisionRequests = 0
    private lateinit var body: LinearLayout
    private lateinit var texture: TextureView
    private lateinit var status: TextView
    private lateinit var voiceStatus: TextView
    private var runChoice: RadioGroup? = null
    private var consentChoice: CheckBox? = null

    fun show() {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        BlackCatStyle.applySystemBarInsets(root)
        val bar = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(6), dp(12), dp(6)) }
        bar.addView(label("Auto", 21f, true).apply { setTextColor(BlackCatStyle.INK) }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(button("Stop / close", false) { dismiss() }, LinearLayout.LayoutParams(-2, dp(48)))
        root.addView(bar)
        status = label("Review the session before starting.", 13f).apply {
            setTextColor(BlackCatStyle.INK)
            setPadding(dp(12), dp(6), dp(12), dp(6)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(status)
        texture = TextureView(activity)
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            addView(texture, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(frame, LinearLayout.LayoutParams(-1, dp(175)).apply { leftMargin = dp(12); rightMargin = dp(12) })
        voiceStatus = label("Explanations stay visible. Voice is optional in Settings.", 11f).apply {
            setTextColor(BlackCatStyle.MUTED); setPadding(dp(12), dp(5), dp(12), dp(5))
        }
        root.addView(voiceStatus)
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(12))
            background = BlackCatStyle.round(activity, BlackCatStyle.COMMAND_BG, 16)
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        (activity as? MainActivity)?.let { root.addView(it.createBottomNavigation("AI") { dismiss() }) }
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            closed = true
            stopWork()
            transport.close()
            narrator.close()
            scope.cancel()
            onClosed()
        }
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        transport.start()
        renderSetup()
        scope.launch {
            while (isActive) {
                val current = session
                if (current?.active == true && !current.valid(target.current(), revision())) {
                    pause("Connection or plan changed. Auto stopped; inspect any partial text. Start a fresh session to continue.")
                }
                delay(200)
            }
        }
    }

    fun dismiss() { if (dialog.isShowing) dialog.dismiss() }

    private fun renderSetup() {
        body.removeAllViews()
        body.addView(label("From step ${startIndex + 1} of ${plan.commands.size}", 16f, true))
        body.addView(label("Destination: ${target.current()?.label ?: "No Bluetooth host"}", 13f, true))
        body.addView(label("Auto explains each step, types it, photographs the terminal reply and checks that photo with OpenAI. Keep the phone pointed at the terminal and keep that terminal focused. A photo is advisory evidence; it cannot prove an exit code or that the whole system is secure.", 13f))
        val run = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        val choiceIds = List(3) { View.generateViewId() }
        listOf("Full Auto · run this reviewed plan", "Auto read-only checks", "Guided · approve and press Enter yourself").forEachIndexed { index, title ->
            run.addView(RadioButton(activity).apply { id = choiceIds[index]; text = title; minHeight = dp(48); setTextColor(Color.WHITE) })
        }
        run.check(choiceIds[0])
        runChoice = run
        body.addView(run)
        body.addView(label("Full Auto types and presses Enter for supported commands you approve here, including system changes. It stops for destructive/unsupported commands, password prompts, unclear photos, lost connection or changes outside this approved plan. Authenticate on the computer first if needed; the app never handles passwords. Read-only Auto pauses before changes. Guided mode uses manual Enter.", 12f))
        body.addView(button(if (cameraStarted) "Restart camera preview" else "Start live camera preview", false) { startCamera() }, lp())
        val consent = CheckBox(activity).apply {
            text = "I have reviewed these commands and the destination. The terminal is focused, the camera frame contains no secrets, and I allow automatic terminal photos to OpenAI for this session."
            isChecked = false; minHeight = dp(56); setTextColor(Color.WHITE)
        }
        consentChoice = consent
        body.addView(consent)
        val voice = selectedVoice()
        body.addView(label("This session permits up to ${AutoRunSession.MAX_CAPTURES} photo checks and up to three bounded plan revisions. These use your OpenAI API account and may incur charges. Voice: ${voice.label}. OpenAI voice sends explanation text to OpenAI for paid AI-generated speech. Stop/close or leaving the app ends consent and clears the session key/audio/photos.", 12f))
        body.addView(button("Start reviewed session", true) { startSession(choiceIds.indexOf(run.checkedRadioButtonId), consent.isChecked) }, lp())
        plan.commands.drop(startIndex).forEachIndexed { offset, step ->
            body.addView(label("${startIndex + offset + 1}. ${step.title}", 14f, true), lp())
            body.addView(label(step.command, 12f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) })
            body.addView(label(if (AutoRunPolicy.canRunReviewed(step)) "Eligible for Full Auto · ${step.risk} risk" else "Requires manual handling", 12f))
            body.addView(label(step.explanation, 12f))
        }
    }

    private fun startCamera() {
        if (closed) return
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Grant camera permission in the photo screen first, then open Auto again."
            return
        }
        cameraGeneration++
        val generation = cameraGeneration
        camera?.close()
        previewFrames = 0
        cameraStarted = false
        camera = PhotoCaptureController(activity, texture, { bytes ->
            if (closed || generation != cameraGeneration) { bytes.fill(0); return@PhotoCaptureController }
            val waiter = photoWaiter
            photoWaiter = null
            if (bytes.size !in 1..4_500_000 || waiter?.isActive != true) {
                bytes.fill(0)
                if (waiter?.isActive == true) waiter.resumeWithException(PlanException("Camera photo was unusable. Auto paused."))
            } else waiter.resume(bytes) { bytes.fill(0) }
        }, { message ->
            if (!closed && generation == cameraGeneration) {
                photoWaiter?.let { if (it.isActive) it.resumeWithException(PlanException(message)) }
                photoWaiter = null
                if (session?.active == true) pause(message) else status.text = message
            }
        }).also { it.start() }
        // Keep the controller's texture lifecycle listener, and count actual preview updates.
        val original = texture.surfaceTextureListener
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                original?.onSurfaceTextureAvailable(surface, width, height)
            }
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                original?.onSurfaceTextureSizeChanged(surface, width, height)
            }
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture) = original?.onSurfaceTextureDestroyed(surface) ?: true
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {
                original?.onSurfaceTextureUpdated(surface)
                if (generation == cameraGeneration && !closed) { previewFrames++; cameraStarted = true }
            }
        }
        status.text = "Frame the terminal clearly and remove secrets before starting."
    }

    private fun startSession(mode: Int, consent: Boolean) {
        if (session?.active == true || work?.isActive == true || closed) return
        if (!consent) { status.text = "Review the commands, destination and photo-upload consent first."; return }
        if (!cameraStarted || previewFrames < 3) { status.text = "Start the live preview and wait until the terminal is visible."; return }
        val host = target.current() ?: run { status.text = "Connect Bluetooth first."; return }
        val snapshotRevision = revision()
        status.text = "Preparing your foreground session…"
        runChoice?.isEnabled = false
        consentChoice?.isEnabled = false
        // A temporary key is consumed only after this explicit session approval.
        val enteredKey = PendingApiKey.take(activity)
        work = scope.launch {
            try {
                val value = if (enteredKey.isNotBlank()) enteredKey else withContext(Dispatchers.IO) { vault.read().orEmpty() }
                if (closed || target.current() != host || revision() != snapshotRevision) return@launch
                if (!OpenAiPlanner.validKey(value)) { status.text = "Add/check your API key in Settings before starting Auto."; return@launch }
                key = value
                session = AutoRunSession(plan.commands.toList(), host, snapshotRevision, mode == 1, startIndex, runAllReviewed = mode == 0)
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val current = session!!
                status.text = "Checking the camera view for an idle terminal before sending any command…"
                var preflightBytes: ByteArray? = null
                val preflight = try {
                    preflightBytes = capturePhoto()
                    current.preflightCaptured()
                    withTimeout(75000) { observer.observe(originalGoal,
                        SuggestedCommand("Check terminal ready", "", "Check the shell is ready.", "low", false, "An idle shell prompt.", emptyList()),
                        targetSystem, model, key, preflightBytes, preflight = true) }
                } finally { preflightBytes?.fill(0) }
                if (!current.valid(target.current(), revision()) || closed) return@launch
                if (!preflight.allowsNext("")) { pause("Camera cannot clearly see an idle terminal prompt. No command was sent."); return@launch }
                observation = null
                renderSession()
                if (current.canRun(current.current!!)) typeStep(human = false)
                else explainStep()
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { pause("Could not prepare Auto securely. No command was sent.") }
        }
    }

    private fun renderSession() {
        body.removeAllViews()
        val current = session ?: return
        val step = current.current
        if (current.stage == AutoStage.FINISHED) {
            status.text = "Reached the end of this reviewed plan. Check the computer; this is not a security guarantee."
            body.addView(label("Plan steps finished", 18f, true)); return
        }
        body.addView(label("Step ${current.index + 1} of ${current.steps.size} · ${current.stage.name.replace('_', ' ').lowercase()}", 14f, true))
        body.addView(label("Destination: ${current.host.label}", 12f))
        if (step != null) {
            body.addView(label(step.title, 18f, true), lp())
            body.addView(label(step.explanation, 14f), lp())
            body.addView(label(step.command, 13f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }, lp())
            body.addView(label("Expected: ${step.expectedResult}", 12f))
            body.addView(label("Risk: ${step.risk} · ${if (step.requiresAdmin) "admin" else "no admin flag"}", 12f))
            step.warnings.forEach { body.addView(label(it, 12f)) }
        }
        observation?.let { value ->
            body.addView(label("What I can see", 16f, true), lp())
            body.addView(label(value.explanation, 14f))
            value.evidence.forEach { body.addView(label("• $it", 12f)) }
        }
        when (current.stage) {
            AutoStage.REVIEW_STEP -> body.addView(button("Approve & type once", true) { typeStep(human = true) }, lp())
            AutoStage.MANUAL_ENTER -> {
                body.addView(label("Check the typed text and press Enter yourself. Handle any password prompt yourself; do not photograph passwords.", 13f), lp())
                body.addView(button("I ran it · capture and check the result", true) {
                    if (current.userRanCommand()) scheduleCapture(current)
                }, lp())
            }
            AutoStage.REVIEW_RESULT -> body.addView(button("I checked the result · review next step", true) {
                if (current.next(visualReady = false, human = true)) advance(current)
            }, lp())
            else -> Unit
        }
        body.addView(button("Mute explanations", false) { muted = true; narrator.stop(); voiceStatus.text = "Voice muted for this session." }, lp())
        body.addView(button("Stop session", false) { pause("Stopped by you. Nothing will resume; inspect any partial text.") }, lp())
    }

    private fun typeStep(human: Boolean) {
        val current = session ?: return
        val approval = current.approve(target.current(), revision(), human) ?: return
        work = scope.launch {
            try {
                explainStep()
                status.text = if (approval.enter) "Approved command runs in 3 seconds. Stop remains available." else "Typing your approved text only."
                renderSession()
                if (approval.enter) delay(3000)
                val outcome = AutoReadOnlySender.send(approval, current, target, revision)
                if (outcome != SendOutcome.REPORTS_ACCEPTED) { pause("Typing stopped ($outcome). Inspect any partial text; no automatic retry."); return@launch }
                if (!current.typed(approval)) return@launch
                renderSession()
                if (approval.enter) scheduleCapture(current)
                else status.text = "Text typed. Check it and press Enter on the computer when ready."
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { pause("Typing stopped. Inspect any partial text; no automatic retry.") }
        }
    }

    private fun scheduleCapture(current: AutoRunSession) {
        work = scope.launch {
            var bytes: ByteArray? = null
            try {
                status.text = "Waiting 5 seconds for terminal output, then automatically capturing one photo."
                renderSession()
                delay(5000)
                if (!current.valid(target.current(), revision())) { pause("Connection or plan changed. Auto paused."); return@launch }
                val token = current.captured() ?: run { pause("Photo-check budget reached. Start a fresh session after review."); return@launch }
                bytes = capturePhoto()
                status.text = "Checking the terminal photo with OpenAI. No next command is being typed."
                renderSession()
                val value = withTimeout(75000) { observer.observe(originalGoal, current.current!!, targetSystem, model, key, bytes) }
                if (!current.valid(target.current(), revision()) || !current.observed(token) || closed) return@launch
                observation = value
                narrator.explain(value.explanation, if (muted) NarrationVoice.TEXT else selectedVoice(), key)
                val ready = value.allowsNext(current.current!!.command)
                renderSession()
                if (!ready) {
                    if (value.revisionNeeded || value.outcome == "error") proposeRevision(current, value)
                    else pause("Photo did not clearly show this command finishing at a shell prompt. Auto stopped; inspect the terminal manually.", keepObservation = true)
                } else if (current.automatic) {
                    narrator.awaitExplanation()
                    if (current.valid(target.current(), revision()) && current.next(visualReady = true, human = false)) advance(current)
                }
                else status.text = "The photo appears consistent with the expected result. Check it before moving on."
            } catch (e: CancellationException) { throw e }
              catch (e: PlanException) { pause(e.message ?: "Photo check failed. Auto paused.") }
              catch (_: Exception) { pause("Camera or photo check failed. Auto paused; no next command.") }
              finally { bytes?.fill(0); photoWaiter = null }
        }
    }

    private fun advance(current: AutoRunSession) {
        observation = null
        renderSession()
        if (current.stage == AutoStage.FINISHED) {
            stopWork(finished = true)
        } else if (current.canRun(current.current!!)) typeStep(human = false)
        else {
            status.text = "Next step needs your approval and manual Enter. Auto will not execute this change."
            explainStep()
        }
    }

    private suspend fun proposeRevision(current: AutoRunSession, value: AutoObservation) {
        if (!value.canAutoRevise(current.current!!.command)) {
            pause("The terminal needs manual attention. Auto stopped before any plan request or revised command.", keepObservation = true)
            return
        }
        if (revisionRequests >= 3) { pause("Plan-revision budget reached. Review the current result before a fresh session.", keepObservation = true); return }
        revisionRequests++
        status.text = "The output needs attention. Preparing a plan update within your approved scope."
        val feedback = "Automatic photo observation, NOT human-confirmed success: " + value.explanation +
            "\nVisible evidence: " + value.evidence.joinToString("\n")
        val context = PlanFeedbackRequest.goal(originalGoal, current.current, emptySet(), feedback, current.steps.drop(current.index))
        val candidate = withTimeout(75000) { planner.propose(context, targetSystem, model, key) }
        if (!current.valid(target.current(), revision()) || closed) return
        if (current.canAdopt(candidate)) {
            val updated = CommandPlan(candidate.explanation, candidate.assumptions, candidate.questions,
                current.steps.take(current.index + 1) + candidate.commands, candidate.roadmap.ifEmpty { plan.roadmap })
            val newRevision = onAutomaticPlan(updated)
            if (current.adopt(candidate, newRevision)) {
                status.text = "Updated the remaining plan within your approved scope. Continuing with fresh command checks."
                advance(current)
                return
            }
        }
        current.stop()
        onProposal(candidate)
        status.text = "Updated plan proposed. Close Auto to review/apply it; a new session needs fresh approval."
        stopWork()
        body.addView(button("Close Auto · review updated plan", true) { dismiss() }, lp())
    }

    private fun explainStep() {
        val step = session?.current ?: return
        narrator.explain(NarrationText.step(step), if (muted) NarrationVoice.TEXT else selectedVoice(), key)
    }

    private fun pause(message: String, keepObservation: Boolean = false) {
        session?.block()
        stopWork()
        if (!keepObservation) observation = null
        status.text = message
        renderSession()
        body.addView(label("Close Auto and review the plan/result before starting again.", 13f), lp())
    }

    private fun stopWork(finished: Boolean = false) {
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!finished) session?.stop()
        observer.cancel()
        planner.cancel()
        work?.cancel()
        scope.coroutineContext.cancelChildren()
        photoWaiter?.cancel()
        photoWaiter = null
        narrator.stop()
        key = ""
        cameraGeneration++
        camera?.close()
        camera = null
        cameraStarted = false
    }

    private suspend fun capturePhoto(): ByteArray = withTimeout(12000) {
        suspendCancellableCoroutine { waiter ->
            photoWaiter = waiter
            waiter.invokeOnCancellation { activity.runOnUiThread { if (photoWaiter === waiter) photoWaiter = null } }
            camera?.capture() ?: waiter.resumeWithException(PlanException("Camera unavailable."))
        }
    }

    private fun selectedVoice(): NarrationVoice {
        val stored = activity.getSharedPreferences(TargetSystemPanel.PREFS, Activity.MODE_PRIVATE)
            .getString(AssistantNarrator.VOICE_KEY, NarrationVoice.OPENAI.name)
        return NarrationVoice.values().firstOrNull { it.name == stored } ?: NarrationVoice.TEXT
    }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(Color.rgb(224, 240, 230))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(value: String, primary: Boolean, action: () -> Unit) = Button(activity).apply {
        text = value; BlackCatStyle.styleButton(activity, this, primary); setOnClickListener { action() }
    }
    private fun lp() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
    private fun dp(value: Int) = BlackCatStyle.dp(activity, value)
}
