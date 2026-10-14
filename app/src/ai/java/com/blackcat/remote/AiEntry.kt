package com.blackcat.remote

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlinx.coroutines.*

object AiEntry {
    const val enabled = true
    fun open(activity: Activity, target: CommandTarget): () -> Unit {
        val panel = AiPanel(activity, target)
        panel.show()
        return { panel.dismiss() }
    }
}

private class AiPanel(private val activity: Activity, private val target: CommandTarget) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val planner = OpenAiPlanner()
    private val vault = ApiKeyVault(activity.applicationContext)
    private val dialog = Dialog(activity)
    private var job: Job? = null
    private var busy = false
    private var selected = -1
    private var commands = emptyList<SuggestedCommand>()

    private lateinit var keyField: EditText
    private lateinit var targetField: EditText
    private lateinit var goalField: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var consent: CheckBox
    private lateinit var status: TextView
    private lateinit var explanation: TextView
    private lateinit var choices: RadioGroup
    private lateinit var generate: Button
    private lateinit var send: Button

    private fun text(value: String, size: Float = 14f) = TextView(activity).apply {
        text = value
        textSize = size
        setPadding(dp(4), dp(5), dp(4), dp(5))
    }

    private fun input(hintText: String, max: Int, secret: Boolean = false) = EditText(activity).apply {
        hint = hintText
        filters = arrayOf(InputFilter.LengthFilter(max))
        inputType = if (secret) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (secret) setSingleLine(true)
    }

    private fun button(label: String, action: () -> Unit) = Button(activity).apply {
        text = label
        setOnClickListener { action() }
    }

    fun show() {
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(18))
        }
        body.addView(text("BLACK CAT AI COMMAND ASSISTANT", 20f))
        body.addView(text("AI proposes commands only. Nothing is typed until you select one and confirm. TYPE ONLY never presses Enter."))

        keyField = input(if (vault.exists()) "Saved OpenAI key available — leave blank to use it" else "OpenAI API key", 512, true)
        targetField = input("Target OS and shell, e.g. Ubuntu Linux / Bash", 500)
        goalField = input("Goal, e.g. create a standard user called alice", 2000)
        modelSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, PlanCodec.MODELS)
        }
        consent = CheckBox(activity).apply {
            text = "Send this goal and target description to OpenAI. Do not include passwords or private terminal output."
        }
        body.addView(keyField)
        body.addView(button("SAVE KEY ON THIS PHONE") { saveKey() })
        body.addView(button("FORGET SAVED KEY") { forgetKey() })
        body.addView(text("Saving is optional. The app uses Android Keystore; a compromised phone can still expose a usable credential."))
        body.addView(modelSpinner)
        body.addView(targetField)
        body.addView(goalField)
        body.addView(consent)

        generate = button("GENERATE PROPOSAL") { generateProposal() }
        body.addView(generate)
        status = text("Ready. No API request has been made.")
        explanation = text("")
        choices = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
            setOnCheckedChangeListener { _, id ->
                selected = id - 1000
                refreshSend()
            }
        }
        body.addView(status)
        body.addView(explanation)
        body.addView(choices)

        send = button("SEND SELECTED — TYPE ONLY") { confirmSelected() }
        body.addView(send)
        body.addView(text("Before sending, focus an EMPTY terminal prompt on the connected computer. The app cannot see the screen, terminal output, or whether the command succeeded."))
        body.addView(button("CANCEL REQUEST / STOP TYPING") {
            planner.cancel()
            job?.cancel()
            status.text = "Stopped. Inspect and clear any partially typed command manually."
        })
        body.addView(button("CLOSE ASSISTANT") { dismiss() })

        dialog.setContentView(ScrollView(activity).apply { addView(body) })
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            planner.cancel()
            job?.cancel()
            scope.cancel()
            if (::keyField.isInitialized) keyField.text.clear()
            if (::goalField.isInitialized) goalField.text.clear()
            if (::targetField.isInitialized) targetField.text.clear()
        }
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        refreshSend()
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        generate.isEnabled = !value
        keyField.isEnabled = !value
        targetField.isEnabled = !value
        goalField.isEnabled = !value
        modelSpinner.isEnabled = !value
        consent.isEnabled = !value
        refreshSend()
    }

    private fun refreshSend() {
        if (::send.isInitialized) send.isEnabled = !busy && selected in commands.indices && target.current() != null
    }

    private fun saveKey() {
        if (busy) return
        val key = keyField.text.toString().trim()
        if (!OpenAiPlanner.validKey(key)) {
            status.text = "Enter a valid API key first."
            return
        }
        setBusy(true)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { vault.save(key) }
                keyField.text.clear()
                keyField.hint = "Saved OpenAI key available — leave blank to use it"
                status.text = "Key saved. No API request was made."
            } catch (_: Exception) {
                status.text = "Key could not be saved securely. There is no plaintext fallback."
            } finally {
                setBusy(false)
            }
        }
    }

    private fun forgetKey() {
        if (busy) return
        setBusy(true)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { vault.forget() }
                keyField.text.clear()
                keyField.hint = "OpenAI API key"
                status.text = "Saved key removed from this phone. Revoke it separately at OpenAI if needed."
            } catch (_: Exception) {
                status.text = "Could not finish deleting the saved key."
            } finally {
                setBusy(false)
            }
        }
    }

    private fun generateProposal() {
        if (busy) return
        if (!consent.isChecked || goalField.text.isBlank() || targetField.text.isBlank()) {
            status.text = "Enter a goal and target OS/shell, then tick the consent box."
            return
        }
        val goal = goalField.text.toString()
        val targetText = targetField.text.toString()
        val entered = keyField.text.toString().trim()
        val model = PlanCodec.MODELS[modelSpinner.selectedItemPosition.coerceIn(PlanCodec.MODELS.indices)]
        keyField.text.clear()
        choices.removeAllViews()
        explanation.text = ""
        commands = emptyList()
        selected = -1
        setBusy(true)
        status.text = "Requesting command proposal… nothing is being typed."

        job = scope.launch {
            try {
                val key = withContext(Dispatchers.IO) {
                    if (entered.isNotBlank()) entered else vault.read().orEmpty()
                }
                if (!OpenAiPlanner.validKey(key)) throw PlanException("Enter or save a valid OpenAI API key.")
                val plan = withTimeout(65000) { planner.propose(goal, targetText, model, key) }
                explanation.text = buildString {
                    append(plan.explanation)
                    if (plan.assumptions.isNotEmpty()) append("\n\nAssumptions:\n").append(plan.assumptions.joinToString("\n"))
                    if (plan.questions.isNotEmpty()) append("\n\nQuestions to resolve:\n").append(plan.questions.joinToString("\n"))
                }
                commands = plan.commands
                commands.forEachIndexed { index, command ->
                    choices.addView(RadioButton(activity).apply {
                        id = 1000 + index
                        typeface = Typeface.MONOSPACE
                        text = buildString {
                            append(index + 1).append(". ").append(command.title)
                            append(" [").append(command.risk).append("; admin=").append(command.requiresAdmin).append("]\n")
                            append(command.command).append("\n").append(command.explanation)
                            append("\nExpected: ").append(command.expectedResult)
                            if (command.warnings.isNotEmpty()) append("\nWarnings: ").append(command.warnings.joinToString("; "))
                        }
                        setPadding(dp(4), dp(9), dp(4), dp(9))
                    })
                }
                status.text = if (commands.isEmpty()) "No commands proposed. Resolve the questions and generate again."
                    else "Proposal ready. Select exactly one command to review."
            } catch (_: TimeoutCancellationException) {
                planner.cancel()
                status.text = "Request timed out. Nothing was typed."
            } catch (e: PlanException) {
                status.text = e.message ?: "Proposal rejected."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status.text = "Request failed. Nothing was typed; no automatic retry occurred."
            } finally {
                setBusy(false)
            }
        }
    }

    private fun confirmSelected() {
        if (busy) return
        val chosen = commands.getOrNull(selected) ?: return
        val host = target.current() ?: run {
            status.text = "No ready Bluetooth HID connection."
            return
        }
        val problem = CommandPolicy.problem(chosen.command)
        if (problem != null) {
            status.text = problem
            return
        }
        val command = chosen.command
        val checkFocus = CheckBox(activity).apply { text = "Correct computer selected; terminal prompt is empty; keyboard layout is US; Caps Lock is off." }
        val checkReview = CheckBox(activity).apply { text = "I reviewed this exact command and want it typed WITHOUT Enter." }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(text("Destination: " + host.label))
            addView(text(command, 16f).apply { typeface = Typeface.MONOSPACE })
            addView(checkFocus)
            addView(checkReview)
        }
        val confirm = AlertDialog.Builder(activity)
            .setTitle("Final review")
            .setView(ScrollView(activity).apply { addView(box) })
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("TYPE ONLY", null)
            .create()
        confirm.setOnShowListener {
            val positive = confirm.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            fun refresh() {
                positive.isEnabled = checkFocus.isChecked && checkReview.isChecked && target.current() == host
            }
            checkFocus.setOnCheckedChangeListener { _, _ -> refresh() }
            checkReview.setOnCheckedChangeListener { _, _ -> refresh() }
            positive.setOnClickListener {
                if (!checkFocus.isChecked || !checkReview.isChecked || target.current() != host) {
                    status.text = "Connection changed. Review again."
                    confirm.dismiss()
                    return@setOnClickListener
                }
                confirm.dismiss()
                setBusy(true)
                choices.clearCheck()
                status.text = "Typing selected command only. Enter will not be sent."
                val approval = CommandApproval(command, host)
                job = scope.launch {
                    try {
                        val outcome = ApprovedCommandSender.send(approval, target)
                        status.text = if (outcome == SendOutcome.REPORTS_ACCEPTED) {
                            "Android accepted all HID reports. Verify the text on the computer, then press Enter yourself."
                        } else {
                            "Typing stopped (" + outcome + "). Inspect/clear any partial text manually."
                        }
                    } finally {
                        setBusy(false)
                    }
                }
            }
            refresh()
        }
        confirm.show()
        confirm.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
