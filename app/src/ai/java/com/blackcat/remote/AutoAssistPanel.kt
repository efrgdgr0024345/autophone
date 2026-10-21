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
    commandTargetOverride: CommandTarget? = null,
    planClientOverride: AutoPlanClient? = null,
    private val onTaskHandoff: (AutoTaskHandoff) -> Unit = {}
) {
    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transport = AiTransportScope(activity, manager) { dismiss() }
    private val target: CommandTarget = commandTargetOverride ?: transport
    private val vault = ApiKeyVault(activity.applicationContext)
    private val planner: AutoPlanClient = planClientOverride ?: AutoPlanClient.Live()
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
    private var repeatPhotos = 0
    private var activePlan = plan
    private var handedOff = false
    private var updatingPlan = false
    private var followCurrentTask = true
    private var showOperations = false
    private val expandedTasks = mutableSetOf<Int>()
    private var sessionKeyInput: EditText? = null
    private lateinit var body: LinearLayout
    private lateinit var taskScroll: ScrollView
    private lateinit var texture: TextureView
    private lateinit var status: TextView
    private lateinit var voiceStatus: TextView
    private lateinit var phaseStatus: TextView
    private lateinit var autoToggle: Button
    private lateinit var voiceToggle: Button
    private var runChoice: RadioGroup? = null
    private var consentChoice: CheckBox? = null

    fun show() {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        BlackCatStyle.applySystemBarInsets(root)
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false // Measure wrapped, weighted controls at their actual phone width.
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        autoToggle = button("Auto Play · OFF", true) {
            if (session?.active == true) dismiss()
            else {
                val group = runChoice
                if (group != null && consentChoice?.isChecked == true && cameraStarted)
                    startSession(group.indexOfChild(group.findViewById(group.checkedRadioButtonId)), true)
                else status.text = "Review the goal and destination below, start the camera and enable session consent, then turn Auto on."
            }
        }
        voiceToggle = button("Voice · ON", false) {
            val isOn = !muted && selectedVoice() != NarrationVoice.TEXT
            muted = isOn
            activity.getSharedPreferences(TargetSystemPanel.PREFS, Activity.MODE_PRIVATE).edit()
                .putString(AssistantNarrator.VOICE_KEY, if (isOn) NarrationVoice.TEXT.name else NarrationVoice.OPENAI.name).apply()
            if (isOn) { narrator.stop(); voiceStatus.text = "Voice off. Explanations remain visible." }
            else if (session?.active == true) explainStep()
            updateQuickControls()
        }
        bar.addView(autoToggle, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(voiceToggle, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        root.addView(bar)
        status = label("Review the session before starting.", 13f).apply {
            setTextColor(BlackCatStyle.INK)
            setPadding(dp(12), dp(6), dp(12), dp(6)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(status)
        phaseStatus = label("Ready for session review", 12f, true).apply {
            setTextColor(BlackCatStyle.INK); setPadding(dp(12), 0, dp(12), dp(5))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(phaseStatus)
        texture = TextureView(activity)
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            addView(texture, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(frame, LinearLayout.LayoutParams(-1, dp(112)).apply { leftMargin = dp(12); rightMargin = dp(12) })
        voiceStatus = label("Explanations stay visible. Voice is optional in Settings.", 11f).apply {
            setTextColor(BlackCatStyle.MUTED); setPadding(dp(12), dp(5), dp(12), dp(5))
        }
        root.addView(voiceStatus)
        val scroll = object : ScrollView(activity) {
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) followCurrentTask = false
                return super.dispatchTouchEvent(event)
            }
        }.apply { isFillViewport = true }
        taskScroll = scroll
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
        updateQuickControls()
        renderSetup()
        scope.launch {
            while (isActive) {
                val current = session
                if (current?.active == true && !current.valid(target.current(), revision())) {
                    pause(if (current.expired) "The 45-minute session limit was reached. Auto stopped; no automatic restart."
                        else "Connection or plan changed. Auto stopped; inspect any partial text. Start a fresh session to continue.")
                }
                updateQuickControls()
                delay(200)
            }
        }
    }

    fun dismiss() { if (dialog.isShowing) dialog.dismiss() }

    private fun renderSetup() {
        body.removeAllViews()
        body.addView(label("From step ${startIndex + 1} of ${plan.commands.size}", 16f, true))
        body.addView(label("Destination: ${target.current()?.label ?: "No Bluetooth host"}", 13f, true))
        body.addView(label("Keep the terminal focused and the phone pointed at it. Auto runs a command, checks a photo, then updates your plan when needed.", 14f))
        val run = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        val choiceIds = List(3) { View.generateViewId() }
        listOf("Auto Play · run and adapt this plan", "Auto read-only checks", "Guided · approve and press Enter yourself").forEachIndexed { index, title ->
            run.addView(RadioButton(activity).apply { id = choiceIds[index]; text = title; minHeight = dp(48); setTextColor(Color.WHITE) })
        }
        run.check(choiceIds[0])
        runChoice = run
        run.visibility = View.GONE
        val modeButton = button("Mode · Auto Play", false) {
            run.visibility = if (run.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        run.setOnCheckedChangeListener { _, checked ->
            modeButton.text = "Mode · " + when (choiceIds.indexOf(checked)) { 1 -> "Read-only"; 2 -> "Guided"; else -> "Auto Play" }
        }
        body.addView(modeButton, lp())
        body.addView(run)
        body.addView(button(if (cameraStarted) "Restart camera preview" else "Start live camera preview", false) { startCamera() }, lp())
        // Saved credentials are managed in Settings, outside the task/session flow.
        sessionKeyInput = null
        if (!vault.exists()) {
            val keyField = EditText(activity).apply {
                hint = "API key for this Auto session"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                isSingleLine = true; isSaveEnabled = false
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                filters = arrayOf(android.text.InputFilter.LengthFilter(512))
                BlackCatStyle.styleInput(activity, this)
                visibility = View.GONE
            }
            sessionKeyInput = keyField
            body.addView(button("Use a temporary API key here", false) {
                keyField.visibility = View.VISIBLE; keyField.requestFocus()
                status.text = "Enter a key for this Auto session. To reuse it across plans, save it in Settings. It is checked with the first photo request."
            }, lp())
            body.addView(keyField, lp())
        }
        val consent = CheckBox(activity).apply {
            text = "I approve this goal and destination, automatic Enter, new diagnostic commands and plan changes for this goal, and automatic photos to OpenAI. The terminal is focused and the camera frame contains no secrets."
            isChecked = false; minHeight = dp(56); setTextColor(Color.WHITE)
        }
        consentChoice = consent
        body.addView(consent)
        val voice = selectedVoice()
        body.addView(button("Start reviewed session", true) { startSession(choiceIds.indexOf(run.checkedRadioButtonId), consent.isChecked) }, lp())
        val limits = label("Auto presses Enter only for supported diagnostic contracts and can add diagnostic steps for this goal. Configuration changes and unsupported arguments need manual handling. It stops on passwords, uncertain photos or disconnection. Authenticate on the computer first; passwords are never typed by the app. Guided uses manual Enter.\n\nUp to ${AutoRunSession.MAX_CAPTURES} paid photo checks, ${AutoRunSession.MAX_REVISIONS} plan revisions, ${AssistantNarrator.MAX_REQUESTS} paid speech requests and 45 minutes per session. Voice: ${voice.label}. Photos are advisory, not a security guarantee. Captures over 1 MiB or 16 pages are incomplete and require a narrower query. Linux output is held in a private /tmp/bc-* folder and cleaned after collection. Stopping early can leave it there. Leaving the app ends consent and clears the session key/audio/photos. Stop prevents further input; it cannot undo a command already running on Linux.", 13f).apply { visibility = View.GONE }
        body.addView(button("Session details and limits", false) { limits.visibility = if (limits.visibility == View.GONE) View.VISIBLE else View.GONE }, lp())
        body.addView(limits)
        body.addView(button("Stop / close", false) { dismiss() }, lp())
        plan.commands.drop(startIndex).forEachIndexed { offset, step ->
            body.addView(label("${startIndex + offset + 1}. ${step.title}", 14f, true), lp())
            body.addView(label(step.command, 12f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) })
            body.addView(label(if (AutoRunPolicy.canRunReviewed(step)) "Supported diagnostic · ${step.risk} risk" else "Manual handling · no automatic Enter", 12f))
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
        val explicitKey = sessionKeyInput?.text?.toString()?.trim().orEmpty()
        sessionKeyInput?.text?.clear()
        val enteredKey = if (explicitKey.isNotBlank()) {
            PendingApiKey.clear(activity)
            explicitKey
        } else PendingApiKey.take(activity)
        work = scope.launch {
            try {
                val value = if (enteredKey.isNotBlank()) enteredKey else withContext(Dispatchers.IO) { vault.read().orEmpty() }
                if (closed || target.current() != host || revision() != snapshotRevision) return@launch
                if (!OpenAiPlanner.validKey(value)) {
                    if (sessionKeyInput == null || vault.exists()) {
                        status.text = "Your saved API key is unavailable. Close Auto and check the key in Settings, then start again."
                    } else {
                        sessionKeyInput?.visibility = View.VISIBLE
                        sessionKeyInput?.requestFocus()
                        status.text = "Enter an API key above and start again, or save one in Settings to reuse it across plans. A temporary key used to generate the plan has already been consumed."
                    }
                    return@launch
                }
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
                advance(current)
            } catch (e: TimeoutCancellationException) { pause("Initial camera check timed out. No command was sent. Check the connection and try a fresh session.") }
              catch (e: CancellationException) { throw e }
              catch (e: PlanException) { pause(e.message ?: "Initial camera check failed. No command was sent.") }
              catch (_: Exception) { pause("Could not prepare Auto securely. No command was sent.") }
        }
    }

    private fun renderSession() {
        updateQuickControls()
        val previousScroll = taskScroll.scrollY
        body.removeAllViews()
        val current = session ?: return
        if (!followCurrentTask) body.addView(button("Follow current task", false) {
            followCurrentTask = true; renderSession()
        }, lp())
        val step = current.current
        if (current.stage == AutoStage.FINISHED) {
            val checked = current.history.tasks.count { it.state == AutoTaskState.OBSERVED }
            val unresolved = current.history.tasks.count { it.state in setOf(AutoTaskState.FAILED, AutoTaskState.UNCERTAIN) }
            status.text = "Plan ended · $checked tasks supported by photo evidence · $unresolved need attention. This is not a security guarantee."
            body.addView(label("Plan steps finished", 18f, true))
        }
        body.addView(label(if (current.stage == AutoStage.FINISHED) "${current.steps.size} operations observed" else "Step ${current.index + 1} of ${current.steps.size} · ${current.stage.name.replace('_', ' ').lowercase()}", 14f, true))
        body.addView(label("Destination: ${current.host.label}", 12f))
        body.addView(label("Session budget · photos ${current.captures}/${AutoRunSession.MAX_CAPTURES} · revisions $revisionRequests/${AutoRunSession.MAX_REVISIONS} · speech ${narrator.speechRequests}/${AssistantNarrator.MAX_REQUESTS}", 12f))
        if (step != null) {
            val operation = current.collection.operation(step.command)
            val task = current.originalTask(step)
            body.addView(label(task.title, 18f, true), lp())
            operation?.let {
                body.addView(label(when {
                    it.page < 0 -> "Clearing the temporary output before choosing the next task"
                    it.page == 0 -> "Capturing this task's output once"
                    else -> "Collecting output · page ${it.page}"
                }, 12f))
            }
            body.addView(label(task.command, 15f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }, lp())
            body.addView(label(task.explanation, 14f), lp())
            body.addView(label("Expected: ${task.expectedResult}", 13f))
            AutoRunPolicy.contract(task)?.let { contract -> body.addView(label("Evidence check: ${contract.evidence}", 12f)) }
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
        body.addView(label("Task history · stable steps and evidence", 16f, true), lp())
        current.history.tasks.forEach { record ->
            val detail = label(record.task.explanation + "\nExpected: " + record.task.expectedResult +
                if (record.finding.isBlank()) "" else "\nObserved: ${record.finding}", 13f).apply {
                visibility = if (record.id in expandedTasks) View.VISIBLE else View.GONE
            }
            body.addView(label("${record.id}. ${record.state.label} · ${record.task.title}", 13f, true), lp())
            body.addView(label(record.task.command, 12f).apply {
                typeface = Typeface.MONOSPACE; minHeight = dp(48); isClickable = true; isFocusable = true
                setTextIsSelectable(true)
                contentDescription = "Task ${record.id}. ${record.state.label}. Tap for explanation."
                setOnClickListener {
                    if (!expandedTasks.add(record.id)) expandedTasks.remove(record.id)
                    detail.visibility = if (record.id in expandedTasks) View.VISIBLE else View.GONE
                }
            })
            body.addView(detail)
        }
        val operations = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; visibility = if (showOperations) View.VISIBLE else View.GONE
        }
        body.addView(button("Live command list · show output operations", false) {
            showOperations = !showOperations
            operations.visibility = if (showOperations) View.VISIBLE else View.GONE
        }, lp())
        body.addView(operations)
        current.steps.forEachIndexed { i, item ->
            val details = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; visibility = View.GONE
                addView(label(item.explanation, 13f))
                addView(label("Expected: ${item.expectedResult}", 12f))
                addView(label("${item.risk} risk · ${if (item.requiresAdmin) "admin" else "no admin"}", 12f))
                item.warnings.forEach { addView(label(it, 12f)) }
            }
            operations.addView(label("${i + 1}. ${if (i < current.index) "Past operation · " else if (i == current.index) "Current · " else "Pending · "}${item.title}", 13f, true), lp())
            operations.addView(label(item.command, 12f).apply {
                typeface = Typeface.MONOSPACE
                minHeight = dp(48); isClickable = true; isFocusable = true
                contentDescription = "Command ${i + 1}. Tap to expand explanation. ${item.command}"
                setOnClickListener { details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE }
            })
            operations.addView(details)
        }
        body.addView(button("Stop / close", false) { dismiss() }, lp())
        body.addView(button("Stop session", false) { pause("Stopped by you. Nothing will resume; inspect any partial text.") }, lp())
        body.post { if (!closed) taskScroll.scrollTo(0, if (followCurrentTask) 0 else previousScroll) }
    }

    private fun typeStep(human: Boolean) {
        val current = session ?: return
        val approval = current.approve(target.current(), revision(), human) ?: run {
            if (!human) pause("Auto cannot approve this step or repeat a transmitted change. Current plan preserved.")
            return
        }
        work = scope.launch {
            try {
                explainStep()
                val helper = current.collection.operation(current.current!!.command)?.page?.let { it != 0 } == true
                status.text = if (!approval.enter) "Typing your approved text only."
                    else if (helper) "Collecting the next part of the evidence. Stop remains available."
                    else "Explaining this step, then running it after a 3-second pause. Stop remains available."
                renderSession()
                narrator.awaitExplanation()
                if (approval.enter && !helper) delay(3000)
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
                val operation = current.collection.operation(current.current!!.command)
                val value = withTimeout(75000) { observer.observe(originalGoal, current.current!!, targetSystem, model, key, bytes,
                    collectionContext = operation?.let { current.collection.context(it) }.orEmpty()) }
                if (!current.valid(target.current(), revision()) || !current.observed(token) || closed) return@launch
                observation = value
                // Speak task findings once, after reconciliation. Paging and cleanup are visual details.
                if (operation == null) narrator.explain(NarrationText.result(value), if (muted) NarrationVoice.TEXT else selectedVoice(), key)
                renderSession()
                if (value.terminalState == "busy" && repeatPhotos < 12) {
                    repeatPhotos++
                    status.text = "The command is still working. Checking again automatically ($repeatPhotos/12)."
                    if (current.waitAgain()) scheduleCapture(current)
                    return@launch
                }
                if (operation != null) {
                    if (!value.collectionReady(operation)) {
                        val smaller = if (value.canResizePage(operation)) current.collection.smallerPage(operation) else null
                        if (smaller != null) {
                            insertCollection(current, smaller, "The output page did not fit. Displaying a smaller slice automatically.")
                            return@launch
                        }
                        if (value.terminalState != "password_prompt" && repeatPhotos < 3) {
                            repeatPhotos++
                            if (current.waitAgain()) scheduleCapture(current)
                        } else pause("Cannot verify the captured page or a safe idle prompt. Auto stopped without typing more.", keepObservation = true)
                        return@launch
                    }
                    repeatPhotos = 0
                    if (operation.page < 0) {
                        val result = operation.ticket.conclusion!!
                        current.recordEvidence(value)
                        observation = result
                        renderSession()
                        narrator.explain(NarrationText.result(result), if (muted) NarrationVoice.TEXT else selectedVoice(), key)
                        if (result.revisionNeeded || result.outcome != "appears_complete" || operation.ticket.exitCode != "0" || result.pageState == "limit" || operation.ticket.truncated) {
                            proposeRevision(current, value, "Captured command exit code: ${operation.ticket.exitCode}.\n" + result.explanation + "\n" +
                                (if (result.pageState == "limit" || operation.ticket.truncated) "Capture limit reached. Evidence is INCOMPLETE. Use focused diagnostic queries; do not infer success.\n" else "") + current.collection.evidence(operation))
                            return@launch
                        }
                    } else {
                        if (operation.page > 0) current.collection.retain(operation, value)
                        else operation.ticket.exitCode = value.exitCode
                        if (operation.page == 0 && value.pageState == "truncated") {
                            operation.ticket.truncated = true
                            operation.ticket.conclusion = value
                            insertCollection(current, current.collection.cleanup(operation), "Output exceeded the byte limit. Evidence is incomplete; preparing a focused diagnostic.")
                        } else if (operation.page == 0 || value.pageState == "more") {
                            val page = current.collection.nextPage(operation) ?: throw PlanException("Output page limit reached.")
                            insertCollection(current, page, "Collecting terminal output in numbered pages; remaining task stays visible.")
                        } else {
                            operation.ticket.conclusion = value
                            insertCollection(current, current.collection.cleanup(operation), "Output collected. Clearing temporary Linux files before reconciling the plan.")
                        }
                        return@launch
                    }
                    narrator.awaitExplanation()
                    if (current.valid(target.current(), revision()) && current.next(visualReady = true, human = false)) advance(current)
                    return@launch
                }
                val ready = value.allowsNext(current.current!!.command)
                current.recordEvidence(value)
                if (!ready) {
                    if (value.revisionNeeded || value.outcome == "error") proposeRevision(current, value)
                    else if (value.terminalState != "password_prompt" && repeatPhotos < 3) {
                        repeatPhotos++
                        if (current.waitAgain()) scheduleCapture(current)
                    } else pause("Camera could not establish the result and a safe shell prompt. Auto stopped.", keepObservation = true)
                } else if (current.automatic) {
                    repeatPhotos = 0
                    narrator.awaitExplanation()
                    if (current.valid(target.current(), revision()) && current.next(visualReady = true, human = false)) advance(current)
                }
                else status.text = "The photo appears consistent with the expected result. Check it before moving on."
            } catch (_: TimeoutCancellationException) {
                pause("Photo analysis or plan update timed out. Auto stopped; no next command was sent. Review the latest evidence before starting a fresh session.", keepObservation = true)
            } catch (e: CancellationException) { throw e }
              catch (e: PlanException) { pause(e.message ?: "Photo check failed. Auto paused.") }
              catch (_: Exception) { pause("Camera or photo check failed. Auto paused; no next command.") }
              finally { bytes?.fill(0); photoWaiter = null }
        }
    }

    private fun advance(current: AutoRunSession) {
        current.prepareCurrent { updated -> publish(updated, "Auto Play: commands and output collection are updated here as evidence arrives.") }
        repeatPhotos = 0
        observation = null
        renderSession()
        if (current.stage == AutoStage.FINISHED) {
            stopWork(finished = true)
        } else if (current.canRun(current.current!!) && (!current.runAllReviewed || current.collection.owns(current.current!!.command))) typeStep(human = false)
        else {
            status.text = "This step is outside the supported diagnostic contracts. Auto is paused; review it and use manual Enter if appropriate."
            explainStep()
        }
    }

    private suspend fun proposeRevision(current: AutoRunSession, value: AutoObservation, collected: String = "") {
        val originIndex = current.index
        val operation = current.collection.operation(current.current!!.command)
        if (!value.canAutoRevise(current.current!!.command) && !(operation != null && value.collectionReady(operation))) {
            pause("The terminal needs manual attention. Auto stopped before any plan request or revised command.", keepObservation = true)
            return
        }
        if (revisionRequests >= AutoRunSession.MAX_REVISIONS) { pause("Plan-revision budget reached. Review the current result before a fresh session.", keepObservation = true); return }
        revisionRequests++
        updatingPlan = true
        updateQuickControls()
        status.text = "The output needs attention. Updating the plan for your goal; this can take up to 3 minutes. Stop remains available."
        val feedback = "Automatic photo observation, NOT human-confirmed success: " + value.explanation +
            "\nVisible evidence: " + value.evidence.joinToString("\n").take(1000)
        val source = operation?.ticket?.original ?: current.current!!
        val context = AutoRevisionContext.goal(originalGoal, source, current.steps.drop(current.index + 1),
            feedback + "\n" + collected, current.transmitted)
        narrator.awaitExplanation()
        if (!current.valid(target.current(), revision()) || current.index != originIndex || current.stage != AutoStage.REVIEW_RESULT || closed) return
        val candidate = try {
            withTimeout(OpenAiPlanner.REQUEST_TIMEOUT_MS) { planner.propose(context, targetSystem, model, key) }
        } finally { updatingPlan = false; updateQuickControls() }
        if (!current.valid(target.current(), revision()) || current.index != originIndex ||
            current.stage != AutoStage.REVIEW_RESULT || closed) return
        if (current.canAdopt(candidate)) {
            activePlan = CommandPlan(candidate.explanation, candidate.assumptions, emptyList(), activePlan.commands,
                candidate.roadmap.ifEmpty { activePlan.roadmap })
            val newRevision = publish(current.steps.take(current.index + 1) + candidate.commands, candidate.explanation)
            if (current.adopt(candidate, newRevision)) {
                status.text = "Updated the remaining plan for your goal from the collected evidence. Continuing with fresh command checks."
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

    private suspend fun insertCollection(current: AutoRunSession, step: SuggestedCommand, explanation: String) {
        val originIndex = current.index
        narrator.awaitExplanation()
        if (!current.valid(target.current(), revision()) || current.index != originIndex || current.stage != AutoStage.REVIEW_RESULT || closed) return
        if (current.steps.size >= AutoRunSession.MAX_STEPS) throw PlanException("Auto plan-step limit reached.")
        val updated = current.steps.take(current.index + 1) + step + current.steps.drop(current.index + 1)
        val newRevision = publish(updated, explanation)
        if (current.insertCollection(step, newRevision)) advance(current)
    }

    private fun updateQuickControls() {
        val currentSession = session
        val paused = currentSession?.active == true && (currentSession.stage == AutoStage.MANUAL_ENTER ||
            currentSession.stage == AutoStage.REVIEW_STEP && !currentSession.canRun(currentSession.current!!) ||
            currentSession.stage == AutoStage.REVIEW_RESULT && !currentSession.automatic)
        autoToggle.text = if (paused) "■ Auto Play · PAUSED" else if (session?.active == true) "■ Auto Play · ON" else "▶ Auto Play · OFF"
        autoToggle.contentDescription = if (session?.active == true) "Turn Auto Play off immediately and close session" else "Turn Auto Play on after session review"
        voiceToggle.text = if (muted || selectedVoice() == NarrationVoice.TEXT) "Voice · OFF"
            else if (selectedVoice() == NarrationVoice.OPENAI && !narrator.openAiReady) "Voice · TEXT" else "Voice · ON"
        voiceToggle.contentDescription = "Toggle OpenAI spoken explanations of commands and terminal feedback"
        if (::phaseStatus.isInitialized) {
            val current = session
            phaseStatus.text = when {
                updatingPlan -> "Updating the remaining plan"
                current == null -> if (work?.isActive == true) "Preparing session" else "Ready for session review"
                current.stage == AutoStage.FINISHED -> "Session finished · review the evidence"
                current.stage in setOf(AutoStage.BLOCKED, AutoStage.STOPPED) -> "Auto stopped · no automatic restart"
                current.stage == AutoStage.REVIEW_STEP -> "Next task needs review"
                current.stage == AutoStage.TYPING -> if (current.collection.operation(current.current!!.command)?.page?.let { it != 0 } == true)
                    "Collecting output for the current task" else "Explaining and sending the current task"
                current.stage == AutoStage.MANUAL_ENTER -> "Waiting for your manual Enter"
                current.stage == AutoStage.WAITING -> "Waiting for Linux output"
                current.stage == AutoStage.ANALYZING -> "Checking the terminal photo"
                else -> "Reconciling the observed evidence"
            }
        }
    }

    private fun publish(commands: List<SuggestedCommand>, explanation: String): Long {
        activePlan = CommandPlan(explanation, activePlan.assumptions, emptyList(), commands, activePlan.roadmap)
        return onAutomaticPlan(activePlan)
    }

    private fun explainStep() {
        val step = session?.current ?: return
        val operation = session?.collection?.operation(step.command)
        val speech = if (operation == null) NarrationText.step(step)
            else if (operation.page == 0) NarrationText.step(operation.ticket.original)
            else return // Internal page/cleanup operations do not generate paid narration.
        narrator.explain(speech, if (muted) NarrationVoice.TEXT else selectedVoice(), key)
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
        updatingPlan = false
        val handoff = if (!handedOff) session?.handoff() else null
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
        sessionKeyInput?.text?.clear()
        session?.collection?.clear()
        cameraGeneration++
        camera?.close()
        camera = null
        cameraStarted = false
        if (handoff != null) { handedOff = true; onTaskHandoff(handoff) }
    }

    private suspend fun capturePhoto(): ByteArray = try { withTimeout(12000) {
        suspendCancellableCoroutine { waiter ->
            photoWaiter = waiter
            waiter.invokeOnCancellation { activity.runOnUiThread { if (photoWaiter === waiter) photoWaiter = null } }
            camera?.capture() ?: waiter.resumeWithException(PlanException("Camera unavailable."))
        }
    } } catch (_: TimeoutCancellationException) {
        throw PlanException("The camera did not deliver a photo in time. Auto stopped; no next command was sent. Restart the preview before a fresh session.")
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

/** Test seam at the network boundary; production retains the existing HTTPS planner. */
internal interface AutoPlanClient {
    suspend fun propose(goal: String, target: String, model: String, key: String): CommandPlan
    fun cancel()
    class Live : AutoPlanClient {
        private val client = OpenAiPlanner()
        override suspend fun propose(goal: String, target: String, model: String, key: String) = client.propose(goal, target, model, key)
        override fun cancel() = client.cancel()
    }
}
