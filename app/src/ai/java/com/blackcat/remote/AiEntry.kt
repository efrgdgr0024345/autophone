package com.blackcat.remote

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Typeface
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import kotlinx.coroutines.*

object AiEntry {
    /** Only adds a button in an existing row; does not access HID or credentials at app startup. */
    fun attach(activity: Activity, row: LinearLayout, manager: () -> HidManager) {
        val button = Button(activity).apply { text = "AI"; contentDescription = "Open AI command assistant" }
        row.addView(button, LinearLayout.LayoutParams(0, (46 * activity.resources.displayMetrics.density).toInt(), 1f))
        button.setOnClickListener {
            button.isEnabled = false
            try {
                AiPanel(activity, manager()) { button.isEnabled = true }.show()
            } catch (_: Exception) {
                button.isEnabled = true
                Toast.makeText(activity, "Could not open AI panel. Bluetooth controls are unchanged.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** No AI response handler can call CommandTarget: only the explicit approval callback below. */
private class AiPanel(private val activity: Activity, manager: HidManager, private val onClosed: () -> Unit) {
    private val target = AiTransportScope(activity, manager) { dismiss() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val planner = OpenAiPlanner()
    private val vault = ApiKeyVault(activity.applicationContext)
    private val dialog = Dialog(activity)
    private var confirmation: AlertDialog? = null
    private var job: Job? = null
    private var busy = false
    private var selected = -1
    private var commands = emptyList<SuggestedCommand>()
    private lateinit var keyField: EditText
    private lateinit var goal: EditText
    private lateinit var targetField: EditText
    private lateinit var model: Spinner
    private lateinit var consent: CheckBox
    private lateinit var message: TextView
    private lateinit var result: TextView
    private lateinit var choices: RadioGroup
    private lateinit var generate: Button
    private lateinit var send: Button
    private lateinit var saveKey: Button
    private lateinit var forgetKey: Button

    private fun text(value: String, size: Float = 15f) = TextView(activity).apply {
        text = value; textSize = size; setPadding(dp(4), dp(6), dp(4), dp(6))
    }
    private fun input(hintText: String, limit: Int, password: Boolean = false) = EditText(activity).apply {
        hint = hintText
        filters = arrayOf(InputFilter.LengthFilter(limit))
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        if (password) setSingleLine(true)
    }
    private fun button(label: String, action: () -> Unit) = Button(activity).apply {
        text = label; setOnClickListener { action() }
    }

    fun show() {
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            isSaveEnabled = false
        }
        body.addView(text("AI command assistant — CatAI-02", 22f))
        body.addView(text("Suggestions only. Nothing is sent to your computer until you select a command and confirm Send. Enter is never appended."))
        body.addView(text("Use your own OpenAI project API key. API requests may cost money. Never use a shared developer key. Saving on a phone is not protection against a compromised device."))
        keyField = input(if (vault.exists()) "Saved key available; leave blank to use it" else "OpenAI API key", 512, true)
        body.addView(keyField)
        saveKey = button("Save key encrypted on this phone") { saveCredential() }
        forgetKey = button("Forget saved key") { forgetCredential() }
        body.addView(saveKey); body.addView(forgetKey)
        body.addView(text("Saving is optional. Otherwise the key is used for this request only. The saved key is encrypted using Android Keystore and excluded from backups."))
        model = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, PlanCodec.MODELS)
        }
        body.addView(model)
        targetField = input("Target OS and shell (for example Ubuntu Linux, Bash)", 500)
        goal = input("Your goal (for example: create a standard user called alice)", 2000)
        body.addView(targetField); body.addView(goal)
        consent = CheckBox(activity).apply {
            text = "Send this goal and target description to OpenAI. Do not include passwords or private terminal output."
        }
        body.addView(consent)
        body.addView(text("No screen capture, terminal feedback or Bluetooth diagnostics are uploaded. store=false is requested; OpenAI's API retention policies still apply."))
        generate = button("GENERATE PROPOSAL") { generateProposal() }
        body.addView(generate)
        message = text("Pair the phone normally first, or generate a proposal before connecting.")
        body.addView(message)
        result = text("")
        body.addView(result)
        choices = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
            setOnCheckedChangeListener { _, id -> selected = id - 100; refreshSend() }
        }
        body.addView(choices)
        send = button("SEND SELECTED — TYPE ONLY") { confirmSelected() }
        body.addView(send)
        body.addView(text("Before sending: focus an EMPTY terminal prompt on the selected computer and use a US keyboard layout with Caps Lock off. The app cannot see or verify the active window or the resulting text."))
        body.addView(button("CANCEL REQUEST / STOP TYPING") { stopWork() })
        body.addView(button("CLOSE ASSISTANT") { dismiss() })
        body.addView(text("Bluetooth baseline: BlackCat v2; HID lineage credited to GhostBoard / ToxicOrca. Earlier reference: Linkpad / Devdas Kumar. Licences remain bundled with the app."))
        dialog.setContentView(ScrollView(activity).apply { addView(body) })
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            confirmation?.dismiss()
            target.close()
            onClosed()
            planner.cancel(); job?.cancel(); scope.cancel()
            keyField.text.clear(); goal.text.clear(); targetField.text.clear()
            commands = emptyList(); choices.removeAllViews(); result.text = ""
        }
        // Do not save the key or goal into screenshots, recent-app thumbnails or view-state bundles.
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        target.start()
        try { dialog.show() } catch (e: Exception) { target.close(); throw e }
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val changed = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { clearPlan() }
            override fun afterTextChanged(s: Editable?) {}
        }
        goal.addTextChangedListener(changed); targetField.addTextChangedListener(changed)
        model.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { clearPlan() }
            override fun onNothingSelected(parent: AdapterView<*>?) { clearPlan() }
        }
        scope.launch { while (isActive) { refreshSend(); delay(300) } }
        refreshSend()
    }

    fun dismiss() { if (dialog.isShowing) dialog.dismiss() }
    private fun clearPlan() {
        selected = -1; commands = emptyList()
        if (::choices.isInitialized) choices.removeAllViews()
        if (::result.isInitialized) result.text = ""
        refreshSend()
    }
    private fun refreshSend() {
        if (::send.isInitialized) send.isEnabled = !busy && selected in commands.indices && target.current() != null
    }
    private fun working(value: Boolean) {
        busy = value
        listOf<View>(generate, keyField, goal, targetField, model, consent, saveKey, forgetKey, choices).forEach { it.isEnabled = !value }
        refreshSend()
    }
    private fun stopWork() {
        planner.cancel(); job?.cancel(); confirmation?.dismiss()
        message.text = "Stopped. A partially typed command may remain on the laptop; inspect or clear it manually. API work already received by OpenAI may still be charged."
    }
    private fun saveCredential() {
        if (busy) return
        val key = keyField.text.toString().trim()
        if (!OpenAiPlanner.validKey(key)) { message.text = "Enter your own API key first."; return }
        working(true)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { vault.save(key) }
                keyField.text.clear(); keyField.hint = "Saved key available; leave blank to use it"
                message.text = "Key saved encrypted on this phone. No API request was made."
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { message.text = "Key could not be saved securely. There is no plaintext fallback." }
            finally { working(false) }
        }
    }
    private fun forgetCredential() {
        if (busy) return
        working(true)
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { vault.forget() }
                keyField.text.clear(); keyField.hint = "OpenAI API key"
                message.text = "Saved key deleted. This does not revoke the key at OpenAI."
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { message.text = "Could not finish deleting the saved key. Revoke it in your OpenAI account if necessary." }
            finally { working(false) }
        }
    }
    private fun generateProposal() {
        if (busy) return
        if (!consent.isChecked || goal.text.isBlank() || targetField.text.isBlank()) {
            message.text = "Enter the goal, target OS/shell, and confirm sending those fields to OpenAI."
            return
        }
        val requestedGoal = goal.text.toString()
        val requestedTarget = targetField.text.toString()
        val requestedModel = PlanCodec.MODELS[model.selectedItemPosition.coerceIn(PlanCodec.MODELS.indices)]
        val enteredKey = keyField.text.toString().trim()
        keyField.text.clear(); clearPlan(); working(true)
        message.text = "Requesting a proposal. No keyboard or mouse input is being sent."
        job = scope.launch {
            try {
                val key = withContext(Dispatchers.IO) { if (enteredKey.isNotEmpty()) enteredKey else vault.read().orEmpty() }
                if (!OpenAiPlanner.validKey(key)) throw PlanException("Enter a valid personal API key or save one first.")
                val plan = withTimeout(65000) { planner.propose(requestedGoal, requestedTarget, requestedModel, key) }
                result.text = buildString {
                    append(plan.explanation)
                    if (plan.assumptions.isNotEmpty()) append("\n\nAssumptions:\n" + plan.assumptions.joinToString("\n"))
                    if (plan.questions.isNotEmpty()) append("\n\nPlease clarify in your goal before generating again:\n" + plan.questions.joinToString("\n"))
                    append("\n\nAI risk labels are not a guarantee. Review every command.")
                }
                commands = plan.commands
                commands.forEachIndexed { index, command ->
                    choices.addView(RadioButton(activity).apply {
                        id = 100 + index
                        typeface = Typeface.MONOSPACE
                        text = "${index + 1}. ${command.title} [${command.risk}; admin=${command.requiresAdmin}]\n${command.command}\n${command.explanation}\nExpected: ${command.expectedResult}\n${command.warnings.joinToString("\n") }"
                        setPadding(dp(4), dp(10), dp(4), dp(10))
                    })
                }
                message.text = if (commands.isEmpty()) "No commands proposed. Resolve the questions first."
                    else "Proposal ready. Select ONE command; nothing has been typed."
            } catch (_: TimeoutCancellationException) { planner.cancel(); message.text = "Request timed out. Nothing was typed. Generate again only when ready." }
              catch (e: CancellationException) { throw e }
              catch (e: PlanException) { message.text = e.message }
              catch (_: Exception) { message.text = "Request or key retrieval failed. Check connectivity/key storage. Nothing was typed; no automatic retry." }
            finally { working(false) }
        }
    }
    private fun confirmSelected() {
        if (busy || confirmation?.isShowing == true) return
        val selectedCommand = commands.getOrNull(selected) ?: return
        val host = target.current() ?: run { message.text = "No ready Bluetooth HID connection."; return }
        val problem = CommandPolicy.problem(selectedCommand.command)
        if (problem != null) { message.text = problem; return }
        val command = selectedCommand.command // Immutable snapshot; cannot change underneath the confirmation.
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        content.addView(text("Destination: ${host.label}\nRisk: ${selectedCommand.risk}; admin: ${selectedCommand.requiresAdmin}\n${selectedCommand.explanation}"))
        content.addView(text(command, 16f).apply { typeface = Typeface.MONOSPACE })
        val focus = CheckBox(activity).apply { text = "The correct computer has an empty terminal prompt, US layout, and Caps Lock off." }
        val review = CheckBox(activity).apply { text = "I reviewed this exact command and accept its effects. Type text only, without Enter." }
        content.addView(focus); content.addView(review)
        val confirm = AlertDialog.Builder(activity).setTitle("Review before typing")
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("CANCEL", null).setPositiveButton("TYPE ONLY", null).create()
        confirmation = confirm
        confirm.setOnShowListener {
            val positive = confirm.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            fun enable() { positive.isEnabled = focus.isChecked && review.isChecked && target.current() == host }
            focus.setOnCheckedChangeListener { _, _ -> enable() }
            review.setOnCheckedChangeListener { _, _ -> enable() }
            positive.setOnClickListener {
                if (!focus.isChecked || !review.isChecked || busy || target.current() != host) {
                    message.text = "Connection changed. Re-select and review the destination."; confirm.dismiss()
                } else {
                    confirm.dismiss()
                    working(true)
                    choices.clearCheck()
                    message.text = "Typing selected command only. You can stop; Enter will not be sent."
                    val approval = CommandApproval(command, host)
                    job = scope.launch {
                        try {
                            val outcome = ApprovedCommandSender.send(approval, target)
                            message.text = if (outcome == SendOutcome.REPORTS_ACCEPTED)
                                "All keyboard reports were accepted by Android. Verify the text on the laptop; this is not proof of execution. To run it, press Enter on the laptop or close this panel and use Full Keyboard → Enter."
                            else "Transmission stopped ($outcome). Inspect/clear any partial text manually; do not resume automatically."
                        } catch (e: CancellationException) { throw e }
                          catch (_: Exception) { message.text = "Typing stopped. Inspect or clear any partial text manually." }
                          finally { working(false) }
                    }
                }
            }
        }
        confirm.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        confirm.show()
    }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
