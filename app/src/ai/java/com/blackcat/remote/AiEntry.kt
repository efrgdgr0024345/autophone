package com.blackcat.remote

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
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
    /** Lazy entry points only: opening a screen never sends API or HID input by itself. */
    fun openAssistant(activity: Activity, manager: () -> HidManager) {
        try {
            AiPanel(activity, manager()) {}.show()
        } catch (_: Exception) {
            Toast.makeText(activity, "Could not open AI Assistant. Bluetooth controls are unchanged.", Toast.LENGTH_LONG).show()
        }
    }

    fun openPhotoFeedback(activity: Activity, manager: () -> HidManager) {
        try {
            PhotoFeedbackPanel(activity, manager()) {}.show()
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
    manager: HidManager,
    private val onClosed: () -> Unit
) {
    private enum class Tab { PLAN, STEP, PREVIEW, FEEDBACK }

    private val target = AiTransportScope(activity, manager) { dismiss() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val planner = OpenAiPlanner()
    private val vault = ApiKeyVault(activity.applicationContext)
    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)

    private var confirmation: AlertDialog? = null
    private var settingsDialog: AlertDialog? = null
    private var job: Job? = null
    private var busy = false
    private var activeTab = Tab.PLAN
    private var selected = -1
    private var selectedModel = PlanCodec.DEFAULT_MODEL
    private var commands = emptyList<SuggestedCommand>()
    private var currentPlan: CommandPlan? = null
    private var planRevision = 0L
    private var oneShotKey = ""
    private var lastTypedCommand: String? = null
    private val recentGoals = ArrayDeque<String>()
    private val targetPrefs by lazy { activity.getSharedPreferences(TARGET_PREFS, Activity.MODE_PRIVATE) }
    private var selectedTarget = DEFAULT_TARGET
    private var customTarget = ""
    private var targetOptions = emptyList<String>()
    private var suppressTargetSelection = false

    private lateinit var goal: EditText
    private lateinit var targetSpinner: Spinner
    private lateinit var buildButton: Button
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

    fun show() {
        dialog.setContentView(buildShell())
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            confirmation?.dismiss()
            settingsDialog?.dismiss()
            target.close()
            onClosed()
            planner.cancel()
            job?.cancel()
            scope.cancel()
            oneShotKey = ""
            commands = emptyList()
            currentPlan = null
        }
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(BG))
        dialog.window?.statusBarColor = BG
        dialog.window?.navigationBarColor = PAPER

        try {
            dialog.show()
        } catch (e: Exception) {
            target.close()
            throw e
        }
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Observes only the already-existing V2 connection. No HID lifecycle call occurs here.
        target.start()
        scope.launch {
            while (isActive) {
                refreshConnectionState()
                delay(350)
            }
        }
        renderAll()
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    private fun buildShell(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(buildTopBar(), matchWrap())
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
        root.addView(buildBottomNav(), matchWrap())

        applyActiveTab()
        return root
    }

    private fun buildTopBar(): View {
        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(4), dp(10), dp(5))
            setBackgroundColor(Color.WHITE)
        }
        val portrait = ImageView(activity).apply {
            setImageResource(R.drawable.black_cat_full)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Black Cat"
        }
        hero.addView(portrait, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(128)))

        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("Linux Assistant", 19f, Color.BLACK, Typeface.BOLD))
        titles.addView(label("Plan → Step → Preview → Result", 11f, Color.DKGRAY))
        titleRow.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val settings = actionButton("Settings", primary = false) { openSettings() }.apply {
            minWidth = 0
            minHeight = dp(40)
            setPadding(dp(10), 0, dp(10), 0)
            textSize = 12f
            contentDescription = "Open AI settings"
        }
        titleRow.addView(settings, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        hero.addView(titleRow, matchWrap())
        return hero
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
            maxLines = 2
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
        val cardLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(45)
        }
        stage.addView(card, cardLp)

        val mascot = ImageView(activity).apply {
            setImageResource(R.drawable.black_cat_peek)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val mascotLp = FrameLayout.LayoutParams(dp(156), dp(108), Gravity.TOP or Gravity.END).apply {
            rightMargin = dp(3)
        }
        stage.addView(mascot, mascotLp)

        card.addView(label("Goal", 17f, INK, Typeface.BOLD).apply { setPadding(0, 0, dp(142), dp(8)) })
        goal = input("What do you want to achieve?", 2000, multiline = true).apply {
            minHeight = dp(96)
            textSize = 17f
        }
        card.addView(goal, matchWrap())

        card.addView(label("Target system & shell", 13f, INK, Typeface.BOLD).apply { setPadding(0, dp(13), 0, dp(7)) })
        loadTargetState()
        val targetRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = round(FIELD, 11, FIELD_LINE, 1)
            setPadding(dp(10), 0, dp(5), 0)
        }
        targetRow.addView(label(">_", 15f, GREEN, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            background = round(TINT, 8)
            setPadding(dp(7), dp(7), dp(7), dp(7))
        })
        targetSpinner = Spinner(activity).apply {
            minimumHeight = dp(50)
            contentDescription = "Target Linux system and shell"
        }
        targetRow.addView(targetSpinner, LinearLayout.LayoutParams(0, dp(50), 1f))
        card.addView(targetRow, matchWrap())
        refreshTargetSpinner()
        targetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressTargetSelection) return
                val chosen = targetOptions.getOrNull(position) ?: return
                if (chosen == EDIT_TARGET) {
                    showCustomTargetEditor()
                    return
                }
                val value = if (chosen.startsWith(CUSTOM_PREFIX)) customTarget else chosen
                if (value.isNotBlank() && value != selectedTarget) {
                    selectedTarget = value
                    targetPrefs.edit().putString(TARGET_KEY, selectedTarget).apply()
                    invalidatePlanForInputChange()
                    renderAll()
                }
            }
        }

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

        buildButton = actionButton("Build plan  →", primary = true) { generateProposal() }.apply {
            textSize = 17f
            minHeight = dp(54)
        }
        card.addView(buildButton, matchWrap(top = 12))
        stage.minimumHeight = dp(360)
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

    private fun refreshTargetSpinner() {
        if (!::targetSpinner.isInitialized) return
        val options = mutableListOf<String>()
        options.addAll(COMMON_TARGETS)
        if (customTarget.isNotBlank()) options.add(CUSTOM_PREFIX + customTarget)
        options.add(EDIT_TARGET)
        targetOptions = options
        suppressTargetSelection = true
        targetSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, targetOptions)
        val selectedLabel = when {
            selectedTarget in COMMON_TARGETS -> selectedTarget
            customTarget.isNotBlank() && selectedTarget == customTarget -> CUSTOM_PREFIX + customTarget
            else -> DEFAULT_TARGET
        }
        targetSpinner.setSelection(targetOptions.indexOf(selectedLabel).coerceAtLeast(0), false)
        suppressTargetSelection = false
    }

    private fun showCustomTargetEditor() {
        val editor = input("Distribution / OS and shell", 500, multiline = false).apply {
            val initial = if (customTarget.isNotBlank()) customTarget else if (selectedTarget !in COMMON_TARGETS) selectedTarget else ""
            setText(initial)
            setSelection(text.length)
        }
        val d = AlertDialog.Builder(activity)
            .setTitle("Edit target system")
            .setMessage("Example: Proxmox VE / Bash, Ubuntu Server / Zsh, or a remote SSH target.")
            .setView(editor)
            .setNegativeButton("CANCEL") { _, _ -> refreshTargetSpinner() }
            .setPositiveButton("SAVE") { _, _ ->
                val value = editor.text.toString().trim()
                if (value.isNotBlank()) {
                    customTarget = value
                    selectedTarget = value
                    targetPrefs.edit()
                        .putString(TARGET_KEY, selectedTarget)
                        .putString(CUSTOM_TARGET_KEY, customTarget)
                        .apply()
                    refreshTargetSpinner()
                    invalidatePlanForInputChange()
                    renderAll()
                } else {
                    refreshTargetSpinner()
                }
            }
            .create()
        d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        d.setOnDismissListener {
            if (::targetSpinner.isInitialized && targetSpinner.selectedItem?.toString() == EDIT_TARGET) refreshTargetSpinner()
        }
        d.show()
    }

    private fun buildBottomNav(): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(6))
            background = round(COMMAND_BG, 0, Color.rgb(48, 66, 59), 1)
        }
        fun add(tab: Tab, icon: String, title: String) {
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(5), dp(4), dp(5))
                isClickable = true
                isFocusable = true
                contentDescription = title
                addView(label(icon, 17f, Color.rgb(188, 204, 198), Typeface.BOLD).apply { gravity = Gravity.CENTER })
                addView(label(title, 11f, Color.rgb(188, 204, 198), Typeface.BOLD).apply { gravity = Gravity.CENTER })
                setOnClickListener { switchTab(tab) }
            }
            tabButtons[tab] = item
            bar.addView(item, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        add(Tab.PLAN, "▤", "Plan")
        add(Tab.STEP, "→", "Step")
        add(Tab.PREVIEW, ">_", "Preview")
        add(Tab.FEEDBACK, "◇", "Result")
        return bar
    }

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
        if (commands.isEmpty() && currentPlan == null) {
            renderAll()
            return
        }
        planRevision++
        selected = -1
        commands = emptyList()
        currentPlan = null
        confirmation?.dismiss()
        renderAll()
    }

    private fun renderAll() {
        if (!::goal.isInitialized) return
        buildButton.isEnabled = !busy && goal.text.isNotBlank() && selectedTarget.isNotBlank()
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
        val plan = currentPlan ?: run {
            planResult.visibility = View.GONE
            return
        }
        planResult.visibility = View.VISIBLE
        val card = card()
        card.addView(label("Plan", 18f, INK, Typeface.BOLD))
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
        plan.assumptions.take(4).forEach { card.addView(label("• $it", 12f, MUTED).apply { setPadding(0, dp(2), 0, 0) }) }

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
                    addView(label("${command.risk.uppercase()} · ${if (command.requiresAdmin) "admin may be required" else "no admin flag"}", 11f, MUTED))
                }
                row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(label("›", 19f, MUTED, Typeface.BOLD))
                card.addView(row, matchWrap(top = 5))
            }
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
        pageHeading(stepBody, "Step", "Inspect one command before it can be typed.")
        val command = commands.getOrNull(selected)
        if (command == null) {
            stepBody.addView(emptyState("No step selected", if (commands.isEmpty()) "Build a plan first." else "Choose a step from Plan."), matchWrap())
            stepBody.addView(actionButton(if (commands.isEmpty()) "Go to Plan" else "Choose from Plan", true) {
                switchTab(Tab.PLAN)
            }, matchWrap(top = 10))
            return
        }
        val detail = card()
        detail.addView(label(command.title, 19f, INK, Typeface.BOLD))
        detail.addView(label(command.explanation, 14f, MUTED).apply { setPadding(0, dp(8), 0, dp(4)) })
        detail.addView(commandBox(command.command), matchWrap(top = 8, bottom = 8))
        infoLine(detail, "Expected result", command.expectedResult)
        infoLine(detail, "Risk", "${command.risk.uppercase()}${if (command.requiresAdmin) " · administrative access may be required" else ""}")
        if (command.warnings.isNotEmpty()) infoLine(detail, "Warnings", command.warnings.joinToString("\n• ", prefix = "• "))
        stepBody.addView(detail, matchWrap())
        stepBody.addView(actionButton("Preview destination  →", true) { switchTab(Tab.PREVIEW) }, matchWrap(top = 10))
        stepBody.addView(textButton("Choose another step") { switchTab(Tab.PLAN) }, matchWrap(top = 3))
    }

    private fun renderFeedback() {
        if (!::feedbackBody.isInitialized) return
        feedbackBody.removeAllViews()
        pageHeading(feedbackBody, "Result", "What happens after the command reaches the computer.")
        val questions = currentPlan?.questions.orEmpty()
        if (questions.isNotEmpty()) {
            val q = card()
            q.addView(label("Clarification needed", 18f, INK, Typeface.BOLD))
            questions.forEach { q.addView(label("• $it", 14f, INK).apply { setPadding(0, dp(7), 0, 0) }) }
            q.addView(label("Add the missing detail to your goal, then build the plan again.", 13f, MUTED).apply { setPadding(0, dp(10), 0, 0) })
            feedbackBody.addView(q, matchWrap())
            feedbackBody.addView(actionButton("Edit goal", true) { switchTab(Tab.PLAN); goal.requestFocus() }, matchWrap(top = 10))
            return
        }

        val typed = lastTypedCommand
        if (typed == null) {
            feedbackBody.addView(emptyState(
                "Nothing has been typed yet",
                "Follow Plan → Step → Preview. This page becomes useful after TYPE ONLY sends the reviewed text."
            ), matchWrap())
            if (commands.isNotEmpty()) {
                feedbackBody.addView(actionButton("Go to Preview", true) { switchTab(Tab.PREVIEW) }, matchWrap(top = 10))
            }
            return
        }

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

        val note = card()
        note.addView(label("Result feedback", 15f, INK, Typeface.BOLD))
        note.addView(label(
            "Photo/text result analysis is not enabled in this build yet. If you need a revised plan, return to Plan and include what the terminal showed.",
            12f, MUTED
        ).apply { setPadding(0, dp(6), 0, 0) })
        feedbackBody.addView(note, matchWrap(top = 8))
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
        val send = actionButton(if (host == null) "Connect Bluetooth first" else "Review & type", true) { confirmSelected() }
        send.isEnabled = !busy && host != null
        previewBody.addView(send, matchWrap(top = 10))
        previewBody.addView(textButton("Back to step") { switchTab(Tab.STEP) }, matchWrap(top = 3))
    }

    private fun generateProposal() {
        if (busy) return
        if (goal.text.isBlank() || selectedTarget.isBlank()) {
            setStatus("Enter the goal and target system first.", error = true)
            return
        }
        val requestedGoal = goal.text.toString().trim()
        val requestedTarget = selectedTarget
        val requestedModel = selectedModel
        val enteredKey = oneShotKey
        oneShotKey = ""
        confirmation?.dismiss()
        busy = true
        planRevision++
        selected = -1
        commands = emptyList()
        currentPlan = null
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
                val plan = withTimeout(65000) { planner.propose(requestedGoal, requestedTarget, requestedModel, key) }
                currentPlan = plan
                commands = plan.commands
                selected = if (commands.isNotEmpty()) 0 else -1
                planRevision++
                rememberGoal(requestedGoal)
                activeTab = Tab.PLAN
                applyActiveTab()
                setStatus(if (commands.isEmpty()) "OpenAI needs clarification." else "Plan ready · choose one step")
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
                busy = false
                renderAll()
            }
        }
    }

    private fun confirmSelected() {
        if (busy || confirmation?.isShowing == true) return
        val index = selected
        val selectedCommand = commands.getOrNull(index) ?: return
        val host = target.current() ?: run {
            setStatus("No ready Bluetooth HID connection.", error = true)
            renderAll()
            return
        }
        val problem = CommandPolicy.problem(selectedCommand.command)
        if (problem != null) {
            setStatus(problem, error = true)
            return
        }
        val snapshotRevision = planRevision
        val command = selectedCommand.command
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = round(PAPER, 16, LINE, 1)
            addView(label("Destination", 11f, MUTED, Typeface.BOLD))
            addView(label(host.label, 15f, INK, Typeface.BOLD).apply { setPadding(0, dp(4), 0, dp(10)) })
            addView(label("Risk: ${selectedCommand.risk.uppercase()}${if (selectedCommand.requiresAdmin) " · admin may be required" else ""}", 12f, MUTED))
            addView(label(selectedCommand.explanation, 13f, MUTED).apply { setPadding(0, dp(7), 0, dp(3)) })
            addView(commandBox(command), matchWrap(top = 7, bottom = 6))
            addView(label("Pressing TYPE ONLY is your one-use approval for this exact command and destination. Enter will not be sent.", 12f, GREEN, Typeface.BOLD))
        }
        val confirm = AlertDialog.Builder(activity)
            .setTitle("Review before typing")
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("TYPE ONLY", null)
            .create()
        confirmation = confirm
        confirm.setOnShowListener {
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val unchanged = planRevision == snapshotRevision && selected == index &&
                    commands.getOrNull(index)?.command == command && target.current() == host
                if (!unchanged || busy) {
                    setStatus("Plan or connection changed. Review again.", error = true)
                    confirm.dismiss()
                    renderAll()
                    return@setOnClickListener
                }
                confirm.dismiss()
                busy = true
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
                            activeTab = Tab.FEEDBACK
                            applyActiveTab()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        setStatus("Typing stopped. Inspect any partial text manually.", error = true)
                    } finally {
                        busy = false
                        renderAll()
                    }
                }
            }
        }
        confirm.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        confirm.show()
    }

    private fun openSettings() {
        if (settingsDialog?.isShowing == true) return
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        body.addView(label("OpenAI", 18f, INK, Typeface.BOLD))
        body.addView(label("Build plan sends the visible goal and target to OpenAI using your key. No request is made just by opening Settings.", 12f, MUTED).apply { setPadding(0, dp(5), 0, dp(9)) })
        val key = input(if (vault.exists()) "Saved key available · enter only to replace/use once" else "OpenAI API key", 512, multiline = false, password = true)
        body.addView(key, matchWrap())
        body.addView(label("Model", 12f, MUTED, Typeface.BOLD).apply { setPadding(0, dp(10), 0, dp(5)) })
        val model = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, PlanCodec.MODELS)
            setSelection(PlanCodec.MODELS.indexOf(selectedModel).coerceAtLeast(0))
        }
        body.addView(model, matchWrap())
        val message = label(if (vault.exists()) "Saved key is available on this phone." else "No saved key on this phone.", 12f, MUTED).apply { setPadding(0, dp(8), 0, dp(4)) }
        body.addView(message)
        val actions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val useOnce = actionButton("Use key for next request", true) {
            val value = key.text.toString().trim()
            if (!OpenAiPlanner.validKey(value)) {
                message.text = "Enter a valid API key first."
            } else {
                oneShotKey = value
                key.text.clear()
                selectedModel = PlanCodec.MODELS[model.selectedItemPosition.coerceIn(PlanCodec.MODELS.indices)]
                settingsDialog?.dismiss()
                setStatus("API key ready for the next request.")
            }
        }
        val save = actionButton("Save key encrypted on this phone", false) {
            val value = key.text.toString().trim()
            if (!OpenAiPlanner.validKey(value)) {
                message.text = "Enter a valid API key first."
                return@actionButton
            }
            useOnce.isEnabled = false
            job = scope.launch {
                try {
                    withContext(Dispatchers.IO) { vault.save(value) }
                    oneShotKey = ""
                    key.text.clear()
                    message.text = "Key saved encrypted on this phone."
                    selectedModel = PlanCodec.MODELS[model.selectedItemPosition.coerceIn(PlanCodec.MODELS.indices)]
                    setStatus("Saved API key available.")
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    message.text = "Key could not be saved securely. No plaintext fallback was used."
                } finally {
                    useOnce.isEnabled = true
                }
            }
        }
        val forget = textButton("Forget saved key") {
            job = scope.launch {
                try {
                    withContext(Dispatchers.IO) { vault.forget() }
                    oneShotKey = ""
                    message.text = "Saved key deleted. This does not revoke it at OpenAI."
                    setStatus("No saved API key.")
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    message.text = "Could not finish deleting the saved key."
                }
            }
        }
        actions.addView(useOnce, matchWrap(top = 8))
        actions.addView(save, matchWrap(top = 6))
        actions.addView(forget, matchWrap(top = 2))
        body.addView(actions)
        body.addView(label("API requests may cost money. store=false is requested; provider retention rules still apply. Saving on a phone does not protect a key from a compromised device.", 11f, MUTED).apply { setPadding(0, dp(9), 0, 0) })

        val d = AlertDialog.Builder(activity)
            .setTitle("Settings")
            .setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("CLOSE") { _, _ ->
                selectedModel = PlanCodec.MODELS[model.selectedItemPosition.coerceIn(PlanCodec.MODELS.indices)]
            }
            .create()
        settingsDialog = d
        d.setCanceledOnTouchOutside(false)
        d.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        d.setOnDismissListener {
            key.text.clear()
            settingsDialog = null
            renderAll()
        }
        d.show()
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
        statusText.setTextColor(if (error) RED else Color.DKGRAY)
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
        setTextColor(if (primary) Color.WHITE else INK)
        background = round(if (primary) GREEN else FIELD, 13, if (primary) GREEN else LINE, 1)
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