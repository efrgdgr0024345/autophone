package com.blackcat.remote

import android.app.Activity
import android.app.Application
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object AiEntry {
    private val assistantPanels = mutableMapOf<Activity, AiPanel>()
    /** Lazy entry points only: opening a screen never sends API or HID input by itself. */
    fun openAssistant(activity: Activity, manager: () -> HidManager) {
        try {
            val panel = assistantPanels[activity] ?: AiPanel(activity, manager()) {
                assistantPanels.remove(activity)
            }.also { assistantPanels[activity] = it }
            panel.show()
        } catch (_: Exception) {
            assistantPanels.remove(activity)
            Toast.makeText(activity, "Could not open AI Assistant. Bluetooth controls are unchanged.", Toast.LENGTH_LONG).show()
        }
    }

    fun openPhotoFeedback(activity: Activity, manager: () -> HidManager) {
        try {
            PhotoFeedbackPanel(activity, manager(), onClosed = {}).show()
        } catch (_: Exception) {
            Toast.makeText(activity, "Could not open Photo Feedback. Bluetooth controls are unchanged.", Toast.LENGTH_LONG).show()
        }
    }

    fun openTargetSystem(activity: Activity) {
        try {
            TargetSystemPanel(activity).show()
        } catch (_: Exception) {
            Toast.makeText(activity, "Could not open Target System.", Toast.LENGTH_LONG).show()
        }
    }

    fun openSettings(activity: Activity) {
        try {
            AiSettingsPanel(activity).show()
        } catch (_: Exception) {
            Toast.makeText(activity, "Could not open Settings.", Toast.LENGTH_LONG).show()
        }
    }

    fun leaveAssistantForNavigation(activity: Activity) {
        assistantPanels[activity]?.hideForNavigation()
    }

    /** Explicit dictation produces editable text only; it never sends a key report. */
    fun dictateManualText(activity: Activity, onTranscript: (String) -> Unit, onStatus: (String) -> Unit): () -> Unit {
        val voice = VoiceFeedbackController(activity)
        var active = true
        val consent = AlertDialog.Builder(activity)
            .setTitle("Speak text")
            .setMessage(if (voice.onDeviceAvailable())
                "Use on-device speech recognition. Review the transcript before pressing Send Text."
                else "Your Android speech provider may process audio online. Review the transcript before pressing Send Text. Black Cat saves no audio.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Start dictation") { _, _ ->
                if (active) voice.start({ text -> if (active) onTranscript(text) },
                    { status -> if (active) onStatus(status) })
            }
            .create()
        consent.setOnShowListener { consent.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        consent.show()
        return { active = false; voice.cancel(); consent.dismiss() }
    }

    /** Retained compatibility hook for older UI branches. */
    fun attach(activity: Activity, row: LinearLayout, manager: () -> HidManager) {
        val assistant = Button(activity).apply {
            text = "Linux Assistant"
            isAllCaps = false
            setOnClickListener { openAssistant(activity, manager) }
        }
        val photo = Button(activity).apply {
            text = "Photo Feedback"
            isAllCaps = false
            setOnClickListener { openPhotoFeedback(activity, manager) }
        }
        row.addView(assistant, LinearLayout.LayoutParams(0, BlackCatStyle.dp(activity, 56), 1f))
        row.addView(photo, LinearLayout.LayoutParams(0, BlackCatStyle.dp(activity, 56), 1f))
    }
}

/**
 * UI-only redesign over the CatAI-02 API and transport boundary.
 *
 * Important: this class may observe the already-registered HID manager through AiTransportScope,
 * but it never initialises, registers, pairs, connects, disconnects or closes Bluetooth HID.
 */
private class AiPanel(
    private val activity: Activity,
    private val manager: HidManager,
    private val onClosed: () -> Unit
) {
    private enum class Tab { PLAN, STEP, PREVIEW, FEEDBACK }

    private var target = AiTransportScope(activity, manager) { suspendForBackground() }
    private var transportPaused = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val planner = OpenAiPlanner()
    private val vault = ApiKeyVault(activity.applicationContext)
    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)

    private var confirmation: AlertDialog? = null
    private var job: Job? = null
    private var busy = false
    private var activeTab = Tab.PLAN
    private var selected = -1
    private var roadmapExpanded = false
    private var selectedModel = PlanCodec.DEFAULT_MODEL
    private val workspace = PlanWorkspace()
    private val commands get() = workspace.plan?.commands.orEmpty()
    private val currentPlan get() = workspace.plan
    private val planRevision get() = workspace.revision
    private var operationToken = 0L
    private var feedbackDraft = ""
    private val voice = VoiceFeedbackController(activity)
    private var photoPanel: PhotoFeedbackPanel? = null
    private var autoPanel: AutoAssistPanel? = null
    private var libraryPanel: LinuxCommandLibraryPanel? = null
    private var oneShotKey = ""
    private var lastTypedCommand: String? = null
    private var lastTypedIndex = -1
    private val recentGoals = ArrayDeque<String>()
    private val targetPrefs by lazy { activity.getSharedPreferences(TARGET_PREFS, Activity.MODE_PRIVATE) }
    private var selectedTarget = DEFAULT_TARGET
    private var customTarget = ""

    private lateinit var goal: EditText
    private lateinit var targetLabel: TextView
    private lateinit var buildButton: Button
    private lateinit var cancelButton: Button
    private lateinit var statusPill: TextView
    private lateinit var statusText: TextView
    private lateinit var contentHost: FrameLayout
    private lateinit var planView: View
    private lateinit var stepView: View
    private lateinit var feedbackView: View
    private lateinit var previewView: View
    private lateinit var planResult: LinearLayout
    private lateinit var recentSection: LinearLayout
    private lateinit var recentRow: LinearLayout
    private lateinit var nextTitle: TextView
    private lateinit var nextCopy: TextView
    private lateinit var stepBody: LinearLayout
    private lateinit var feedbackBody: LinearLayout
    private lateinit var previewBody: LinearLayout
    private val tabButtons = linkedMapOf<Tab, LinearLayout>()
    private var viewBuilt = false
    private var panelVisible = false

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(a: Activity) {
            if (a === activity && transportPaused && panelVisible && dialog.isShowing) {
                transportPaused = false
                target = AiTransportScope(activity, manager) { suspendForBackground() }
                target.start()
                setStatus("Returned to your plan. Review again before any typing.")
                renderAll()
            }
        }
        override fun onActivityDestroyed(a: Activity) { if (a === activity) dismiss() }
        override fun onActivityCreated(a: Activity, state: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}
    }

    fun show() {
        if (viewBuilt) { resumeForNavigation(); return }
        selectedModel = targetPrefs.getString(AiSettingsPanel.MODEL_KEY, PlanCodec.DEFAULT_MODEL)
            ?.takeIf { it in PlanCodec.MODELS } ?: PlanCodec.DEFAULT_MODEL
        dialog.setContentView(buildShell())
        viewBuilt = true
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            panelVisible = false
            confirmation?.dismiss()
            target.close()
            activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
            photoPanel?.dismiss()
            photoPanel = null
            autoPanel?.dismiss()
            autoPanel = null
            libraryPanel?.dismiss()
            libraryPanel = null
            voice.cancel()
            onClosed()
            planner.cancel()
            job?.cancel()
            scope.cancel()
            oneShotKey = ""
            workspace.clear()
            feedbackDraft = ""
            recentGoals.clear()
            lastTypedCommand = null
        }
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(BG))
        dialog.window?.statusBarColor = BG
        dialog.window?.navigationBarColor = PAPER

        try {
            dialog.show()
            panelVisible = true
        } catch (e: Exception) {
            target.close()
            throw e
        }
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Observes only the already-existing V2 connection. No HID lifecycle call occurs here.
        target.start()
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
        scope.launch {
            while (isActive) {
                if (panelVisible) refreshConnectionState()
                delay(350)
            }
        }
        renderAll()
    }

    fun hideForNavigation() {
        if (!panelVisible) return
        cancelWork()
        confirmation?.dismiss()
        voice.cancel()
        autoPanel?.dismiss()
        photoPanel?.dismiss()
        libraryPanel?.dismiss()
        workspace.touch()
        oneShotKey = ""
        target.close()
        transportPaused = true
        panelVisible = false
        dialog.hide()
    }

    private fun resumeForNavigation() {
        if (panelVisible) return
        val previousTarget = selectedTarget
        loadTargetState()
        selectedModel = targetPrefs.getString(AiSettingsPanel.MODEL_KEY, PlanCodec.DEFAULT_MODEL)
            ?.takeIf { it in PlanCodec.MODELS } ?: PlanCodec.DEFAULT_MODEL
        if (previousTarget != selectedTarget) {
            workspace.touch()
            lastTypedCommand = null
            lastTypedIndex = -1
        }
        targetLabel.text = "Target: $selectedTarget"
        transportPaused = false
        panelVisible = true
        dialog.show()
        target = AiTransportScope(activity, manager) { suspendForBackground() }
        target.start()
        setStatus("Draft ready. Review the current target and command before typing.")
        renderAll()
    }

    private fun cancelWork() {
        operationToken++
        planner.cancel()
        job?.cancel()
        job = null
        busy = false
    }

    private fun suspendForBackground() {
        transportPaused = true
        autoPanel?.dismiss()
        confirmation?.dismiss()
        voice.cancel()
        cancelWork()
        workspace.touch()
        target.close()
        setStatus("Paused. Requests and typing were cancelled; no automatic resume.")
    }

    fun dismiss() {
        // hide() can make isShowing false while the dialog still owns its window and draft.
        // Final Activity destruction must run the dismissal cleanup for hidden drafts too.
        dialog.dismiss()
    }

    private fun buildShell(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(buildBottomNav(), matchWrap())
        root.addView(buildStatusBar(), matchWrap())

        contentHost = FrameLayout(activity).apply { setBackgroundColor(BG) }
        planView = buildPlanView()
        stepView = scrollPage { stepBody = this }
        feedbackView = scrollPage { feedbackBody = this }
        previewView = scrollPage { previewBody = this }
        contentHost.addView(planView, matchMatch())
        contentHost.addView(stepView, matchMatch())
        contentHost.addView(feedbackView, matchMatch())
        contentHost.addView(previewView, matchMatch())
        root.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildAppNav(), matchWrap())

        applyActiveTab()
        return root
    }

    private fun buildStatusBar(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = round(COMMAND_BG, 0, Color.rgb(48, 66, 59), 1)
        }
        statusPill = label("●  WAITING", 11f, GREEN, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = round(Color.rgb(28, 58, 47), 24)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        row.addView(statusPill)
        statusText = label("Open Settings for API key", 11f, Color.rgb(205, 218, 212)).apply {
            setPadding(dp(10), 0, 0, 0)
            maxLines = 4
        }
        row.addView(statusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun buildPlanView(): View {
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(14))
        }
        scroll.addView(body, matchWrap())

        val stage = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(16))
            background = round(PAPER, 20, LINE, 1)
        }
        stage.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(label("Goal", 17f, INK, Typeface.BOLD).apply { setPadding(0, 0, 0, dp(8)) })
        goal = input("What do you want to achieve?", 2000, multiline = true).apply {
            minHeight = dp(96)
            textSize = 17f
        }
        card.addView(goal, matchWrap())
        card.addView(actionButton("Speak your goal", primary = false) { dictateGoal() }, matchWrap(top = 8))

        loadTargetState()
        targetLabel = label("Target: $selectedTarget", 12f, MUTED).apply {
            setPadding(0, dp(8), 0, dp(2))
        }
        card.addView(targetLabel, matchWrap())
        card.addView(textButton("Change target in Settings") { openSettings() }, matchWrap())

        val next = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = round(TINT, 13)
        }
        nextTitle = label("Next step", 14f, INK, Typeface.BOLD)
        nextCopy = label("Ask OpenAI for a reviewable command plan.", 12f, MUTED)
        val nextText = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, 0, 0)
            addView(nextTitle)
            addView(nextCopy)
        }
        next.addView(label("▤", 19f, GREEN, Typeface.BOLD).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(34), dp(34)))
        next.addView(nextText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(next, matchWrap(top = 13))

        val actionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val photoButton = actionButton("▣", primary = false) {
            openPlanPhotoFeedback()
        }.apply {
            contentDescription = "Photo Feedback"
            textSize = 18f
            minWidth = 0
        }
        buildButton = actionButton("Get Plan", primary = true) { generateProposal() }.apply {
            textSize = 17f
            minHeight = dp(54)
        }
        actionRow.addView(photoButton, LinearLayout.LayoutParams(dp(58), dp(54)).apply { rightMargin = dp(8) })
        actionRow.addView(buildButton, LinearLayout.LayoutParams(0, dp(54), 1f))
        card.addView(actionRow, matchWrap(top = 12))
        cancelButton = actionButton("Cancel request / typing", primary = false) {
            cancelWork()
            workspace.touch()
            setStatus("Cancelled. Inspect any partial text on the computer; nothing will resume.")
            renderAll()
        }
        card.addView(cancelButton, matchWrap(top = 8))
        card.addView(
            actionButton("Browse Offline Linux Library", primary = false) {
                openCommandTree()
            },
            matchWrap(top = 8)
        )
        body.addView(stage, matchWrap())

        recentSection = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val recentHead = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("Recent goals", 13f, INK, Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(textButton("Clear") {
                recentGoals.clear()
                renderRecentGoals()
            })
        }
        recentSection.addView(recentHead)
        val recentScroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        recentRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        recentScroll.addView(recentRow, matchWrap())
        recentSection.addView(recentScroll, matchWrap())
        body.addView(recentSection, matchWrap(top = 10))

        planResult = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        body.addView(planResult, matchWrap(top = 10))

        val changed = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { invalidatePlanForInputChange() }
            override fun afterTextChanged(s: Editable?) {}
        }
        goal.addTextChangedListener(changed)
        return scroll
    }

    private fun loadTargetState() {
        selectedTarget = targetPrefs.getString(TARGET_KEY, DEFAULT_TARGET)?.trim().orEmpty().ifBlank { DEFAULT_TARGET }
        customTarget = targetPrefs.getString(CUSTOM_TARGET_KEY, "")?.trim().orEmpty()
        if (selectedTarget !in COMMON_TARGETS && selectedTarget.isNotBlank()) customTarget = selectedTarget
    }

    private fun buildBottomNav(): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = round(COMMAND_BG, 0, Color.rgb(48, 66, 59), 1)
        }
        fun add(tab: Tab, title: String) {
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                isClickable = true
                isFocusable = true
                contentDescription = title
                addView(label(title, 11f, Color.rgb(188, 204, 198), Typeface.BOLD).apply { gravity = Gravity.CENTER })
                setOnClickListener { switchTab(tab) }
            }
            tabButtons[tab] = item
            bar.addView(item, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
        }
        add(Tab.PLAN, "Plan")
        add(Tab.STEP, "Step")
        add(Tab.PREVIEW, "Preview")
        add(Tab.FEEDBACK, "Result")
        return bar
    }

    private fun buildAppNav(): View = (activity as? MainActivity)
        ?.createBottomNavigation("AI") { hideForNavigation() }
        ?: actionButton("Home", false) { dismiss() }

    private fun scrollPage(assign: LinearLayout.() -> Unit): View {
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(18))
            background = round(PAPER, 18, LINE, 1)
            assign()
        }
        scroll.addView(body, matchWrap())
        return scroll
    }

    private fun switchTab(tab: Tab) {
        activeTab = tab
        applyActiveTab()
        renderAll()
    }

    private fun applyActiveTab() {
        if (!::contentHost.isInitialized) return
        planView.visibility = if (activeTab == Tab.PLAN) View.VISIBLE else View.GONE
        stepView.visibility = if (activeTab == Tab.STEP) View.VISIBLE else View.GONE
        feedbackView.visibility = if (activeTab == Tab.FEEDBACK) View.VISIBLE else View.GONE
        previewView.visibility = if (activeTab == Tab.PREVIEW) View.VISIBLE else View.GONE
        tabButtons.forEach { (tab, view) ->
            val active = tab == activeTab
            view.background = if (active) round(Color.rgb(31, 70, 54), 14) else ColorDrawable(Color.TRANSPARENT)
            for (i in 0 until view.childCount) {
                (view.getChildAt(i) as? TextView)?.setTextColor(if (active) Color.rgb(103, 240, 104) else Color.rgb(188, 204, 198))
            }
        }
    }

    private fun invalidatePlanForInputChange() {
        cancelWork()
        voice.cancel()
        workspace.clear()
        selected = -1
        lastTypedCommand = null
        lastTypedIndex = -1
        confirmation?.dismiss()
        renderAll()
    }

    private fun renderAll() {
        if (!::goal.isInitialized) return
        buildButton.isEnabled = !busy && goal.text.isNotBlank() && selectedTarget.isNotBlank()
        cancelButton.visibility = if (busy) View.VISIBLE else View.GONE
        val count = commands.size
        nextTitle.text = when {
            busy -> "Working"
            count > 0 -> "$count reviewable ${if (count == 1) "command" else "commands"}"
            currentPlan?.questions?.isNotEmpty() == true -> "Clarification needed"
            else -> "Next step"
        }
        nextCopy.text = when {
            busy -> "Waiting for OpenAI. Nothing is being typed."
            count > 0 -> "Choose one step. Selection alone sends nothing."
            currentPlan?.questions?.isNotEmpty() == true -> "Update the goal with the missing detail and build again."
            else -> "Ask OpenAI for a reviewable command plan."
        }
        renderPlanResult()
        renderRecentGoals()
        renderStep()
        renderFeedback()
        renderPreview()
        refreshConnectionState()
    }

    private fun renderPlanResult() {
        planResult.removeAllViews()
        workspace.proposal?.let { candidate ->
            val token = planRevision
            val proposal = card()
            proposal.addView(label("Proposed updated plan", 18f, GREEN, Typeface.BOLD))
            proposal.addView(label("Review this replacement before applying it. No step is executed or marked successful.", 13f, MUTED))
            proposal.addView(label(candidate.explanation, 14f, INK).apply { setPadding(0, dp(8), 0, dp(8)) })
            candidate.roadmap.forEach { phase ->
                proposal.addView(label(phase.title, 15f, INK, Typeface.BOLD))
                proposal.addView(label(phase.purpose, 13f, MUTED))
                phase.checks.forEach { proposal.addView(label("• $it", 12f, MUTED)) }
            }
            candidate.assumptions.forEach { proposal.addView(label("• $it", 13f, MUTED)) }
            candidate.questions.forEach { proposal.addView(label("Question: $it", 14f, INK)) }
            candidate.commands.forEachIndexed { index, command ->
                proposal.addView(label("${index + 1}. ${command.title} · ${command.risk.uppercase()}", 14f, INK, Typeface.BOLD))
                proposal.addView(commandBox(command.command), matchWrap(top = 5, bottom = 8))
            }
            proposal.addView(actionButton("Apply updated plan", true) {
                if (!busy && workspace.accept(token)) {
                    confirmation?.dismiss()
                    selected = if (commands.isEmpty()) -1 else 0
                    lastTypedCommand = null
                    lastTypedIndex = -1
                    setStatus("Updated plan applied. Every command needs fresh review.")
                    renderAll()
                } else setStatus("Feedback changed. Request a fresh update.", error = true)
            }, matchWrap(top = 8))
            proposal.addView(textButton("Keep current plan") { workspace.touch(); renderAll() }, matchWrap())
            planResult.addView(proposal, matchWrap(bottom = 10))
        }
        val plan = currentPlan ?: run {
            planResult.visibility = if (workspace.proposal == null) View.GONE else View.VISIBLE
            return
        }
        planResult.visibility = View.VISIBLE
        val card = card()
        card.addView(label("Plan", 18f, INK, Typeface.BOLD))
        if (commands.isNotEmpty()) {
            card.addView(actionButton("Auto · explain, run and check", true) { openAutoAssist() }.apply {
                isEnabled = !busy && workspace.proposal == null
            }, matchWrap(top = 8))
        }
        if (plan.explanation.isNotBlank()) card.addView(label(plan.explanation, 13f, MUTED).apply { setPadding(0, dp(6), 0, dp(8)) })
        if (plan.questions.isNotEmpty()) {
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), dp(9), dp(11), dp(9))
                background = round(AMBER_TINT, 11)
            }
            box.addView(label("OpenAI needs one more detail", 13f, AMBER, Typeface.BOLD))
            plan.questions.forEach { box.addView(label("• $it", 13f, INK).apply { setPadding(0, dp(4), 0, 0) }) }
            card.addView(box, matchWrap(top = 4, bottom = 8))
        }
        if (plan.roadmap.isNotEmpty()) {
            card.addView(label("Full roadmap", 17f, INK, Typeface.BOLD).apply { setPadding(0, dp(12), 0, dp(6)) })
            card.addView(textButton(if (roadmapExpanded) "Hide phase details" else "Show all phase details") {
                roadmapExpanded = !roadmapExpanded
                renderAll()
            }, matchWrap())
            plan.roadmap.forEachIndexed { index, phase ->
                card.addView(label("${index + 1}. ${phase.title}", 15f, INK, Typeface.BOLD).apply { setPadding(0, dp(8), 0, dp(3)) })
                if (roadmapExpanded) {
                    card.addView(label(phase.purpose, 13f, MUTED))
                    phase.checks.forEach { card.addView(label("• $it", 13f, MUTED).apply { setPadding(dp(6), dp(3), 0, 0) }) }
                }
            }
        }
        plan.assumptions.forEach { card.addView(label("• $it", 12f, MUTED).apply { setPadding(0, dp(2), 0, 0) }) }

        if (commands.isNotEmpty()) {
            card.addView(label("Steps", 13f, MUTED, Typeface.BOLD).apply { setPadding(0, dp(10), 0, dp(4)) })
            commands.forEachIndexed { index, command ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(9), dp(9), dp(9), dp(9))
                    background = round(if (index == selected) TINT else FIELD, 12, LINE, 1)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (busy) return@setOnClickListener
                        confirmation?.dismiss()
                        workspace.touch()
                        selected = index
                        activeTab = Tab.STEP
                        applyActiveTab()
                        renderAll()
                    }
                }
                val number = label((index + 1).toString(), 12f, if (index == selected) Color.WHITE else GREEN, Typeface.BOLD).apply {
                    gravity = Gravity.CENTER
                    background = round(if (index == selected) GREEN else TINT, 24, if (index == selected) GREEN else LINE, 1)
                }
                row.addView(number, LinearLayout.LayoutParams(dp(30), dp(30)))
                val texts = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), 0, 0, 0)
                    addView(label(command.title, 14f, INK, Typeface.BOLD))
                    addView(label(command.explanation, 12f, MUTED).apply { maxLines = 3 })
                    addView(label("Check: ${command.expectedResult}", 11f, MUTED).apply { maxLines = 2 })
                    addView(label("${command.risk.uppercase()} · ${if (command.requiresAdmin) "admin may be required" else "no admin flag"}", 11f, MUTED))
                    if (index in workspace.confirmedSteps) addView(label("✓ Result confirmed by you", 11f, GREEN))
                }
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(label("›", 19f, MUTED, Typeface.BOLD))
                card.addView(row, matchWrap(top = 5))
            }
            card.addView(
                actionButton("Start with step 1  →", true) {
                    if (busy) return@actionButton
                    workspace.touch()
                    selected = 0
                    switchTab(Tab.STEP)
                },
                matchWrap(top = 10)
            )
        }
        planResult.addView(card, matchWrap())
    }

    private fun renderRecentGoals() {
        if (!::recentSection.isInitialized) return
        recentRow.removeAllViews()
        if (recentGoals.isEmpty()) {
            recentSection.visibility = View.GONE
            return
        }
        recentSection.visibility = View.VISIBLE
        recentGoals.forEach { value ->
            val chip = textButton("◷  ${value.take(34)}") {
                goal.setText(value)
                goal.setSelection(goal.text.length)
                activeTab = Tab.PLAN
                applyActiveTab()
            }.apply {
                background = round(FIELD, 30, LINE, 1)
                setPadding(dp(11), dp(8), dp(11), dp(8))
                maxLines = 1
            }
            recentRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply { rightMargin = dp(7) })
        }
    }

    private fun renderStep() {
        if (!::stepBody.isInitialized) return
        stepBody.removeAllViews()
        pageHeading(stepBody, "Step", "Review, then approve once on this screen.")
        val command = commands.getOrNull(selected)
        if (command == null) {
            stepBody.addView(emptyState("No step selected", if (commands.isEmpty()) "Build a plan first." else "Choose a step from Plan."), matchWrap())
            stepBody.addView(actionButton(if (commands.isEmpty()) "Go to Plan" else "Choose from Plan", true) {
                switchTab(Tab.PLAN)
            }, matchWrap(top = 10))
            return
        }
        val detail = card()
        detail.addView(label("Step ${selected + 1} of ${commands.size}", 12f, GREEN, Typeface.BOLD))
        detail.addView(label(command.title, 19f, INK, Typeface.BOLD))
        detail.addView(label(command.explanation, 14f, MUTED).apply { setPadding(0, dp(8), 0, dp(4)) })
        detail.addView(commandBox(command.command), matchWrap(top = 8, bottom = 8))
        infoLine(detail, "Expected result", command.expectedResult)
        infoLine(detail, "Risk", "${command.risk.uppercase()}${if (command.requiresAdmin) " · administrative access may be required" else ""}")
        if (command.warnings.isNotEmpty()) infoLine(detail, "Warnings", command.warnings.joinToString("\n• ", prefix = "• "))
        stepBody.addView(detail, matchWrap())
        detail.addView(label("Destination", 12f, MUTED, Typeface.BOLD).apply { setPadding(0, dp(8), 0, 0) })
        detail.addView(label(target.current()?.label ?: "No ready Bluetooth HID connection", 14f, INK, Typeface.BOLD))
        detail.addView(label("Your approval types this exact command once. Press Enter yourself after checking it.", 12f, MUTED))
        stepBody.addView(approvalButton(), matchWrap(top = 10))
        stepBody.addView(textButton("Optional full preview") { switchTab(Tab.PREVIEW) }, matchWrap(top = 3))
        stepBody.addView(actionButton("Edit this step", false) { editSelectedStep() }.apply { isEnabled = !busy }, matchWrap(top = 8))
        if (selected > 0) stepBody.addView(textButton("← Previous step") {
            if (!busy) { confirmation?.dismiss(); workspace.touch(); selected--; renderAll() }
        }, matchWrap(top = 3))
        if (selected + 1 < commands.size) stepBody.addView(textButton("Next step →") {
            if (!busy) { confirmation?.dismiss(); workspace.touch(); selected++; renderAll() }
        }, matchWrap(top = 3))
        stepBody.addView(textButton("Choose another step") { switchTab(Tab.PLAN) }, matchWrap(top = 3))
    }

    private fun renderFeedback() {
        if (!::feedbackBody.isInitialized) return
        feedbackBody.removeAllViews()
        pageHeading(feedbackBody, "Result", "What happens after the command reaches the computer.")
        val typed = lastTypedCommand
        if (typed == null) {
            feedbackBody.addView(emptyState(
                "Tell Black Cat what you see",
                "Type feedback, speak, or review a terminal photo. Feedback never approves a command."
            ), matchWrap())
        } else {
            val next = card()
            next.addView(label("Now check the computer", 18f, INK, Typeface.BOLD))
            next.addView(label(
                "Black Cat typed the reviewed text only. It did not press Enter.",
                13f, MUTED
            ).apply { setPadding(0, dp(7), 0, dp(8)) })
            next.addView(commandBox(typed), matchWrap(bottom = 9))
            next.addView(label("1. Confirm the text on the computer is exactly what you expected.", 13f, INK))
            next.addView(label("2. Press Enter yourself only when you are satisfied.", 13f, INK).apply { setPadding(0, dp(6), 0, 0) })
            next.addView(label("3. Read the terminal result before asking for the next command.", 13f, INK).apply { setPadding(0, dp(6), 0, 0) })
            feedbackBody.addView(next, matchWrap())
            if (commands.getOrNull(lastTypedIndex)?.command == typed) {
                feedbackBody.addView(actionButton("I checked the computer: this step succeeded", true) {
                    if (!busy && workspace.confirmResult(lastTypedIndex, planRevision)) {
                        setStatus("Result recorded from your confirmation, not from AI.")
                        renderAll()
                    }
                }.apply { isEnabled = !busy }, matchWrap(top = 8))
            }
        }

        currentPlan?.questions.orEmpty().forEach { question ->
            feedbackBody.addView(label("Question: $question", 14f, INK).apply {
                setPadding(dp(12), dp(8), dp(12), dp(8)); background = round(PAPER, 12)
            }, matchWrap(top = 8))
        }

        val note = card()
        note.addView(label("Result feedback", 15f, INK, Typeface.BOLD))
        note.addView(label(
            "Describe the output or error, answer a question, or explain what changed. Review transcripts and remove secrets before submitting.",
            12f, MUTED
        ).apply { setPadding(0, dp(6), 0, 0) })
        val feedback = input("What did the terminal show?", 2000, multiline = true).apply {
            setText(feedbackDraft)
            minHeight = dp(100)
        }
        feedback.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                feedbackDraft = s?.toString().orEmpty()
                confirmation?.dismiss()
                voice.cancel()
                cancelWork()
                workspace.touch()
                cancelButton.visibility = View.GONE
                buildButton.isEnabled = goal.text.isNotBlank() && selectedTarget.isNotBlank()
                renderPlanResult()
            }
        })
        note.addView(feedback, matchWrap(top = 8))
        note.addView(actionButton("Speak feedback", false) { dictateFeedback() }.apply { isEnabled = !busy }, matchWrap(top = 8))
        note.addView(textButton("Stop dictation") { voice.cancel(); setStatus("Dictation stopped. Review the text before submitting.") }, matchWrap())
        note.addView(actionButton("Take a photo of the result / error", false) { openPlanPhotoFeedback() }.apply { isEnabled = !busy }, matchWrap(top = 8))
        note.addView(actionButton("Request updated plan", true) { generateProposal(revise = true) }.apply { isEnabled = !busy }, matchWrap(top = 8))
        feedbackBody.addView(note, matchWrap(top = 8))
        if (lastTypedIndex in workspace.confirmedSteps && lastTypedIndex + 1 < commands.size) {
            feedbackBody.addView(actionButton("Review next step →", true) {
                if (!busy) { workspace.touch(); selected = lastTypedIndex + 1; switchTab(Tab.STEP) }
            }, matchWrap(top = 8))
        }
        feedbackBody.addView(actionButton("Back to Plan", true) { switchTab(Tab.PLAN) }, matchWrap(top = 10))
    }

    private fun renderPreview() {
        if (!::previewBody.isInitialized) return
        previewBody.removeAllViews()
        pageHeading(previewBody, "Preview", "Exact text and destination before one-use approval.")
        val command = commands.getOrNull(selected)
        if (command == null) {
            previewBody.addView(emptyState("Nothing selected", "Choose one step before previewing keyboard text."), matchWrap())
            previewBody.addView(actionButton("Choose a step", true) { switchTab(if (commands.isEmpty()) Tab.PLAN else Tab.STEP) }, matchWrap(top = 10))
            return
        }
        val host = target.current()
        val c = card()
        c.addView(label("Destination", 12f, MUTED, Typeface.BOLD))
        c.addView(label(host?.label ?: "No ready Bluetooth HID connection", 15f, if (host == null) AMBER else INK, Typeface.BOLD).apply { setPadding(0, dp(5), 0, dp(8)) })
        c.addView(label("Text to type", 12f, MUTED, Typeface.BOLD))
        c.addView(commandBox(command.command), matchWrap(top = 6, bottom = 8))
        c.addView(label("TYPE ONLY · Enter is never appended", 11f, GREEN, Typeface.BOLD))
        previewBody.addView(c, matchWrap())
        previewBody.addView(actionButton("Copy command", false) {
            (activity.getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Linux command", command.command))
            setStatus("Command copied. Nothing was typed.")
        }, matchWrap(top = 8))
        previewBody.addView(approvalButton(), matchWrap(top = 10))
        previewBody.addView(textButton("Back to step") { switchTab(Tab.STEP) }, matchWrap(top = 3))
    }

    private fun generateProposal(revise: Boolean = false) {
        if (busy) return
        if (goal.text.isBlank() || selectedTarget.isBlank()) {
            setStatus("Enter the goal and target system first.", error = true)
            return
        }
        val originalGoal = goal.text.toString().trim()
        val requestedGoal = try {
            if (revise) PlanFeedbackRequest.goal(originalGoal, commands.getOrNull(selected),
                workspace.confirmedSteps, feedbackDraft.trim(), commands) else originalGoal
        } catch (e: PlanException) {
            setStatus(e.message ?: "Check your feedback.", error = true)
            return
        }
        val requestedTarget = selectedTarget
        val requestedModel = selectedModel
        val enteredKey = oneShotKey.ifBlank { PendingApiKey.take(activity) }
        oneShotKey = ""
        confirmation?.dismiss()
        voice.cancel()
        busy = true
        val requestRevision = workspace.touch()
        val operation = ++operationToken
        setStatus("Waiting for OpenAI · nothing is being typed")
        renderAll()

        job = scope.launch {
            try {
                val key = withContext(Dispatchers.IO) {
                    if (enteredKey.isNotEmpty()) enteredKey else vault.read().orEmpty()
                }
                if (!OpenAiPlanner.validKey(key)) {
                    setStatus("Open Settings and add a valid OpenAI API key.", error = true)
                    openSettings()
                    return@launch
                }
                val plan = withTimeout(75000) { planner.propose(requestedGoal, requestedTarget, requestedModel, key) }
                if (operation != operationToken || planRevision != requestRevision) return@launch
                if (revise) workspace.stage(requestRevision, plan) else {
                    workspace.replace(plan)
                    selected = if (commands.isNotEmpty()) 0 else -1
                    lastTypedCommand = null
                    lastTypedIndex = -1
                }
                rememberGoal(originalGoal)
                activeTab = Tab.PLAN
                applyActiveTab()
                setStatus(if (revise) "Proposed update ready. Review and apply it on Plan."
                    else if (commands.isEmpty()) "OpenAI needs clarification." else "Plan ready · choose one step")
            } catch (_: TimeoutCancellationException) {
                planner.cancel()
                setStatus("OpenAI request timed out. Nothing was typed.", error = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlanException) {
                setStatus(e.message ?: "OpenAI request failed.", error = true)
            } catch (_: Exception) {
                setStatus("Request or key retrieval failed. Nothing was typed.", error = true)
            } finally {
                if (operation == operationToken) { busy = false; renderAll() }
            }
        }
    }

    /** This visible button is the only command approval; there is no second modal. */
    private fun approvalButton(): Button {
        val index = selected
        val command = commands.getOrNull(index)?.command
        val host = target.current()
        val revision = planRevision
        return actionButton(if (host == null) "Connect Bluetooth first" else "Approve & type once", true) {
            if (command != null && host != null) confirmSelected(command, host, index, revision)
        }.apply { isEnabled = !busy && host != null && command != null && workspace.proposal == null }
    }

    private fun confirmSelected(command: String, host: HostSession, index: Int, snapshotRevision: Long) {
        if (busy || transportPaused || !panelVisible || workspace.proposal != null) return
        if (planRevision != snapshotRevision || selected != index ||
            commands.getOrNull(index)?.command != command || target.current() != host) {
            setStatus("Plan or connection changed. Review the current command and destination.", error = true)
            renderAll()
            return
        }
        CommandPolicy.problem(command)?.let { setStatus(it, error = true); return }
        busy = true
        val operation = ++operationToken
        setStatus("Typing selected text only · Enter excluded")
        renderAll()
        val approval = CommandApproval(command, host)
        job = scope.launch {
            try {
                val outcome = ApprovedCommandSender.send(approval, target)
                setStatus(
                    if (outcome == SendOutcome.REPORTS_ACCEPTED)
                        "Text typed. Check the computer before pressing Enter."
                    else "Typing stopped ($outcome). Inspect any partial text manually.",
                    error = outcome != SendOutcome.REPORTS_ACCEPTED
                )
                if (outcome == SendOutcome.REPORTS_ACCEPTED) {
                    lastTypedCommand = command
                    lastTypedIndex = index
                    activeTab = Tab.FEEDBACK
                    applyActiveTab()
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { setStatus("Typing stopped. Inspect any partial text manually.", error = true) }
              finally { if (operation == operationToken) { busy = false; renderAll() } }
        }
    }

    private fun editSelectedStep() {
        if (busy) return
        val index = selected
        val command = commands.getOrNull(index) ?: return
        val token = planRevision
        confirmation?.dismiss()
        val body = card()
        val title = input("Step title", 160, false).apply { setText(command.title) }
        val text = input("One printable ASCII command", 512, false).apply { setText(command.command) }
        val explanation = input("What will this do?", 1500, true).apply { setText(command.explanation) }
        val expected = input("What should you see?", 1000, true).apply { setText(command.expectedResult) }
        body.addView(label("Edits require fresh review and are labelled HIGH risk until you check their effects.", 13f, MUTED))
        for (field in listOf(title, text, explanation, expected)) body.addView(field, matchWrap(top = 8))
        val edit = AlertDialog.Builder(activity).setTitle("Edit plan step")
            .setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Cancel", null).setNeutralButton("Remove step", null)
            .setPositiveButton("Save changes", null).create()
        confirmation = edit
        edit.setOnShowListener {
            edit.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (!busy && token == planRevision) {
                    workspace.remove(index)
                    selected = index.coerceAtMost(commands.lastIndex)
                    lastTypedCommand = null
                    lastTypedIndex = -1
                    edit.dismiss()
                    setStatus("Step removed. Review the remaining plan before continuing.")
                    renderAll()
                } else edit.dismiss()
            }
            edit.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (busy || token != planRevision) {
                    edit.dismiss()
                    setStatus("The plan changed. Open the editor again.", error = true)
                    return@setOnClickListener
                }
                try {
                    workspace.edit(index, title.text.toString().trim(), text.text.toString(),
                        explanation.text.toString(), expected.text.toString())
                    lastTypedCommand = null
                    lastTypedIndex = -1
                    edit.dismiss()
                    setStatus("Step edited. Review its text and destination before typing.")
                    renderAll()
                } catch (e: PlanException) { text.error = e.message }
            }
        }
        edit.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        edit.show()
    }

    private fun openCommandTree() {
        if (busy) return
        confirmation?.dismiss()
        val token = planRevision
        libraryPanel = LinuxCommandLibraryPanel(activity, manager, onClosed = { libraryPanel = null }, onSelectForPlan = { item ->
            if (!dialog.isShowing || transportPaused || busy || token != planRevision) return@LinuxCommandLibraryPanel
            try {
                // A tree-first workflow gets an editable goal from the user's selected task.
                if (goal.text.isBlank()) goal.setText(item.title)
                workspace.append(SuggestedCommand(item.title, item.command, item.explanation,
                    item.risk.name.lowercase(), item.requiresAdmin, item.expectedResult,
                    listOf("Check compatibility: ${item.compatibility}")))
                selected = commands.lastIndex
                lastTypedCommand = null
                lastTypedIndex = -1
                activeTab = Tab.STEP
                applyActiveTab()
                setStatus("Library command added to your plan. Nothing was typed.")
                renderAll()
            } catch (e: PlanException) { setStatus(e.message ?: "Could not add this command.", error = true) }
        }).also { it.show() }
    }

    private fun openAutoAssist() {
        if (busy || transportPaused || workspace.proposal != null || commands.isEmpty()) return
        if (activity.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 918)
            setStatus("Grant camera permission, then tap Auto again. No session has started.")
            return
        }
        val plan = currentPlan ?: return
        confirmation?.dismiss()
        voice.cancel()
        autoPanel = AutoAssistPanel(activity, manager, plan, goal.text.toString().trim(), selectedTarget,
            selectedModel, selected.coerceAtLeast(0), { planRevision },
            onProposal = { candidate ->
                val token = workspace.touch()
                workspace.stage(token, candidate)
                activeTab = Tab.PLAN
                applyActiveTab()
                renderAll()
            }, onAutomaticPlan = { updated ->
                workspace.replace(updated)
                lastTypedCommand = null
                lastTypedIndex = -1
                renderAll()
                planRevision
            }, onClosed = { autoPanel = null; renderAll() }).also { it.show() }
    }

    private fun dictateGoal() = dictate { transcript ->
        goal.setText(transcript)
        goal.setSelection(goal.text.length)
        switchTab(Tab.PLAN)
    }

    private fun dictateFeedback() = dictate { transcript ->
        feedbackDraft = transcript
        workspace.touch()
        switchTab(Tab.FEEDBACK)
    }

    private fun dictate(useTranscript: (String) -> Unit) {
        if (busy) return
        if (voice.listening) { voice.cancel(); setStatus("Dictation cancelled."); return }
        confirmation?.dismiss()
        val token = workspace.touch()
        val explanation = if (voice.onDeviceAvailable())
            "On-device speech recognition is available. Review and edit the transcript before sending it to OpenAI. The app saves no audio."
        else "Your Android speech service may send audio to its provider. Offline recognition is preferred but cannot be guaranteed. Review the transcript before submitting it; the app saves no audio."
        val consent = AlertDialog.Builder(activity).setTitle("Speak your feedback")
            .setMessage(explanation).setNegativeButton("Cancel", null)
            .setPositiveButton("Start dictation") { _, _ ->
                if (dialog.isShowing && planRevision == token && !busy) voice.start({ transcript ->
                    if (dialog.isShowing && !transportPaused && planRevision == token && !busy) {
                        useTranscript(transcript)
                        setStatus("Transcript ready. Review it; no AI request or typing has occurred.")
                    }
                }, { message -> if (dialog.isShowing) setStatus(message) })
            }.create()
        confirmation = consent
        consent.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        consent.show()
    }

    private fun openPlanPhotoFeedback() {
        if (busy) return
        confirmation?.dismiss()
        voice.cancel()
        val originalGoal = goal.text.toString().trim()
            .ifBlank { "Understand this terminal screen and suggest the next safe step." }
        val snapshotPlan = currentPlan
        val snapshotTarget = selectedTarget
        val snapshotIndex = selected
        val contextGoal = originalGoal + (commands.getOrNull(selected)?.let {
            "\nCurrent step to investigate (untrusted context): ${it.command}"
        } ?: "")
        if (contextGoal.length > 2000) return setStatus("Shorten the goal before adding photo context.", error = true)
        // Opening camera feedback does not consume the next-use key. The explicit upload does.
        val key = oneShotKey
        oneShotKey = ""
        photoPanel = PhotoFeedbackPanel(activity, manager, { photoPanel = null },
            initialGoal = contextGoal, initialTarget = snapshotTarget, requestKey = key,
            onUseAnalysis = { value ->
                if (!dialog.isShowing || currentPlan !== snapshotPlan || selectedTarget != snapshotTarget ||
                    selected != snapshotIndex || goal.text.toString().trim().ifBlank {
                        "Understand this terminal screen and suggest the next safe step."
                    } != originalGoal) {
                    setStatus("Plan or target changed. Review a new photo in the current context.", error = true)
                } else {
                    val token = workspace.touch()
                    try {
                        workspace.stage(token, PlanFeedbackRequest.fromPhoto(value, snapshotPlan, snapshotIndex))
                    } catch (e: PlanException) {
                        setStatus(e.message ?: "Photo update could not be applied; your plan was kept.", error = true)
                        return@PhotoFeedbackPanel
                    }
                    activeTab = Tab.PLAN
                    applyActiveTab()
                    setStatus("Photo feedback returned. Review and apply the proposed update; success is not assumed.")
                    renderAll()
                }
            }).also { it.show() }
    }

    private fun openSettings() {
        hideForNavigation()
        (activity as? MainActivity)?.navigateSettings() ?: AiSettingsPanel(activity).show()
    }

    private fun rememberGoal(value: String) {
        val clean = value.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return
        recentGoals.remove(clean)
        recentGoals.addFirst(clean)
        while (recentGoals.size > 3) recentGoals.removeLast()
    }

    private fun refreshConnectionState() {
        if (!::statusPill.isInitialized) return
        val host = target.current()
        if (host != null) {
            statusPill.text = "●  READY"
            statusPill.setTextColor(GREEN)
            statusPill.background = round(TINT, 24)
            if (!busy && statusText.text.toString().startsWith("Bluetooth")) statusText.text = "Connected · ${host.label}"
        } else {
            statusPill.text = "●  PAIR"
            statusPill.setTextColor(AMBER)
            statusPill.background = round(AMBER_TINT, 24)
            if (!busy && statusText.text.toString().startsWith("Connected")) statusText.text = "Bluetooth host not ready"
        }
    }

    private fun setStatus(value: String, error: Boolean = false) {
        if (!::statusText.isInitialized) return
        statusText.text = value
        statusText.setTextColor(if (error) Color.rgb(255, 177, 185) else Color.rgb(205, 218, 212))
    }

    private fun pageHeading(parent: LinearLayout, title: String, subtitle: String) {
        parent.addView(label(title, 22f, INK, Typeface.BOLD))
        parent.addView(label(subtitle, 12f, MUTED).apply { setPadding(0, dp(3), 0, dp(10)) })
    }

    private fun emptyState(title: String, copy: String): View = card().apply {
        addView(label(title, 18f, INK, Typeface.BOLD))
        addView(label(copy, 13f, MUTED).apply { setPadding(0, dp(6), 0, 0) })
    }

    private fun infoLine(parent: LinearLayout, heading: String, value: String) {
        parent.addView(View(activity).apply { setBackgroundColor(LINE) }, matchFixed(dp(1), top = 8, bottom = 8))
        parent.addView(label(heading, 11f, MUTED, Typeface.BOLD))
        parent.addView(label(value, 13f, INK).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun commandBox(command: String): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = round(COMMAND_BG, 13)
        addView(label(command, 14f, COMMAND_TEXT, Typeface.NORMAL).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
    }

    private fun card(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = round(PAPER, 18, LINE, 1)
    }

    private fun input(
        hintText: String,
        limit: Int,
        multiline: Boolean,
        password: Boolean = false
    ) = EditText(activity).apply {
        hint = hintText
        filters = arrayOf(InputFilter.LengthFilter(limit))
        inputType = when {
            password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        isSingleLine = !multiline
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        setTextColor(INK)
        setHintTextColor(0xff71857c.toInt())
        background = round(FIELD, 11, FIELD_LINE, 1)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun label(value: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, style)
        includeFontPadding = false
    }

    private fun actionButton(textValue: String, primary: Boolean, action: () -> Unit) = Button(activity).apply {
        text = textValue
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (primary) Color.BLACK else INK)
        background = round(if (primary) Color.rgb(116, 244, 91) else FIELD, 13, if (primary) Color.rgb(116, 244, 91) else LINE, 1)
        minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun textButton(textValue: String, action: () -> Unit) = Button(activity).apply {
        text = textValue
        isAllCaps = false
        textSize = 12f
        setTextColor(GREEN)
        background = ColorDrawable(Color.TRANSPARENT)
        minHeight = dp(40)
        minWidth = 0
        setPadding(dp(7), dp(5), dp(7), dp(5))
        setOnClickListener { action() }
    }

    private fun round(fill: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null && strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun matchWrap(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun matchFixed(height: Int, top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        height
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun matchMatch() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        private const val TARGET_PREFS = "blackcat_ai_ui"
        private const val TARGET_KEY = "target_system"
        private const val CUSTOM_TARGET_KEY = "target_custom"
        private const val DEFAULT_TARGET = "Ubuntu Linux / Bash"
        private const val CUSTOM_PREFIX = "Custom · "
        private const val EDIT_TARGET = "Edit / custom…"
        private val COMMON_TARGETS = listOf(
            "Ubuntu Linux / Bash",
            "Debian Linux / Bash",
            "Linux Mint / Bash",
            "Fedora Linux / Bash",
            "Arch Linux / Bash",
            "Kali Linux / Bash",
            "Red Hat Enterprise Linux / Bash",
            "Rocky Linux / Bash",
            "AlmaLinux / Bash",
            "openSUSE Linux / Bash",
            "Alpine Linux / ash"
        )

        private val BG = Color.WHITE
        private val PAPER = Color.rgb(18, 26, 23)
        private val INK = Color.rgb(244, 248, 246)
        private val MUTED = Color.rgb(166, 184, 176)
        private val LINE = Color.rgb(51, 70, 63)
        private val GREEN = Color.rgb(86, 220, 91)
        private val TINT = Color.rgb(29, 67, 46)
        private val FIELD = Color.rgb(28, 39, 35)
        private val FIELD_LINE = Color.rgb(60, 82, 73)
        private val AMBER = Color.rgb(190, 128, 48)
        private val AMBER_TINT = Color.rgb(59, 47, 26)
        private val RED = Color.rgb(207, 72, 82)
        private val COMMAND_BG = Color.rgb(11, 20, 17)
        private val COMMAND_TEXT = Color.rgb(207, 242, 194)
    }
}
