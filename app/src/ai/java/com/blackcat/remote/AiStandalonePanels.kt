package com.blackcat.remote

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object ReferencePanelStyle {
    val DARK = 0xff101714.toInt()
    val DARK_SOFT = 0xff18221e.toInt()
    val LINE = 0xff30423b.toInt()
    val GREEN = 0xff74f45b.toInt()
    val MUTED = 0xffb7c5be.toInt()
    val ERROR = 0xffffb4b4.toInt()

    fun dp(activity: Activity, value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    fun label(activity: Activity, textValue: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(activity).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
        }

    fun greenButton(activity: Activity, textValue: String, action: () -> Unit) = Button(activity).apply {
        text = textValue
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(Color.BLACK)
        background = BlackCatStyle.round(activity, GREEN, 13)
        setOnClickListener { action() }
    }

    fun darkButton(activity: Activity, textValue: String, selected: Boolean = false, action: () -> Unit) = Button(activity).apply {
        text = textValue
        isAllCaps = false
        textSize = 14f
        gravity = Gravity.CENTER_VERTICAL
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (selected) GREEN else Color.WHITE)
        background = BlackCatStyle.round(activity, DARK_SOFT, 12, LINE, 1)
        setPadding(dp(activity, 14), 0, dp(activity, 12), 0)
        setOnClickListener { action() }
    }

    fun header(activity: Activity, title: String, close: () -> Unit): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 12), dp(activity, 3))
        addView(TextView(activity).apply {
            text = "‹"
            textSize = 34f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            setOnClickListener { close() }
        }, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)))
        addView(label(activity, title, 20f, Color.BLACK, true), LinearLayout.LayoutParams(0, -2, 1f))
    }

    fun bottomNav(activity: Activity, active: String, close: () -> Unit): View =
        (activity as MainActivity).createBottomNavigation(active, close)

    fun card(activity: Activity) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13))
        background = BlackCatStyle.round(activity, DARK, 18, LINE, 1)
    }

    fun show(dialog: Dialog) {
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window?.statusBarColor = Color.WHITE
        dialog.window?.navigationBarColor = DARK
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}

class TargetSystemPanel(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
    private var editor: AlertDialog? = null
    private var selected = prefs.getString(TARGET_KEY, DEFAULT_TARGET)?.trim().orEmpty().ifBlank { DEFAULT_TARGET }

    fun show() {
        dialog.setContentView(build())
        dialog.setOnDismissListener { editor?.dismiss(); editor = null }
        ReferencePanelStyle.show(dialog)
    }

    private fun returnToSettings() {
        dialog.dismiss()
        AiSettingsPanel(activity).show()
    }

    private fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(ReferencePanelStyle.header(activity, "Target system") { returnToSettings() })
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(14))
        }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        val card = ReferencePanelStyle.card(activity)
        card.addView(ReferencePanelStyle.label(activity, "Choose the computer's Linux system", 14f, ReferencePanelStyle.GREEN, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val options = COMMON_TARGETS.toMutableList()
        val custom = prefs.getString(CUSTOM_KEY, "")?.trim().orEmpty()
        if (custom.isNotBlank() && custom !in options) options.add(custom)
        if (selected !in options) options.add(selected)
        options.forEach { option ->
            val chosen = option == selected
            card.addView(ReferencePanelStyle.darkButton(activity, (if (chosen) "✓  " else "   ") + option, chosen) {
                selected = option
                prefs.edit().putString(TARGET_KEY, option).apply()
                returnToSettings()
            }, LinearLayout.LayoutParams(-1, -2).apply { height = dp(56); bottomMargin = dp(5) })
        }
        card.addView(ReferencePanelStyle.darkButton(activity, "Edit / Custom target…") { openCustomEditor() },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(3) })
        body.addView(card)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ReferencePanelStyle.bottomNav(activity, "Settings") { dialog.dismiss() })
        return root
    }

    private fun openCustomEditor() {
        val existing = prefs.getString(CUSTOM_KEY, "")?.trim().orEmpty()
        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        val name = EditText(activity).apply {
            hint = "System name"
            setText(existing.substringBefore(" — "))
            BlackCatStyle.styleInput(activity, this)
        }
        val details = EditText(activity).apply {
            hint = "Optional details (e.g. OS / shell)"
            setText(existing.substringAfter(" — ", ""))
            BlackCatStyle.styleInput(activity, this)
        }
        wrap.addView(name, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        wrap.addView(details, LinearLayout.LayoutParams(-1, dp(62)))
        editor = AlertDialog.Builder(activity)
            .setTitle("Custom target system")
            .setView(wrap)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                val d = details.text.toString().trim()
                if (n.isNotBlank()) {
                    val value = if (d.isBlank()) n else "$n — $d"
                    prefs.edit().putString(TARGET_KEY, value).putString(CUSTOM_KEY, value).apply()
                    selected = value
                    returnToSettings()
                }
            }.create()
        editor?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        editor?.show()
    }

    private fun dp(value: Int) = ReferencePanelStyle.dp(activity, value)

    companion object {
        const val PREFS = "blackcat_ai_ui"
        const val TARGET_KEY = "target_system"
        const val CUSTOM_KEY = "target_custom"
        const val DEFAULT_TARGET = "Ubuntu Linux / Bash"
        val COMMON_TARGETS = listOf(
            "Ubuntu Linux / Bash", "Debian Linux / Bash", "Linux Mint / Bash", "Kali Linux / Bash",
            "Fedora Linux / Bash", "openSUSE Linux / Bash", "Arch Linux / Bash"
        )
    }
}

class AiSettingsPanel(private val activity: Activity, private val keyChecker: ApiKeyChecker = OpenAiKeyChecker()) {
    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
    private val vault = ApiKeyVault(activity.applicationContext)
    private val prefs = activity.getSharedPreferences(TargetSystemPanel.PREFS, Activity.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkJob: Job? = null
    private var checkGeneration = 0L
    private var suppressInputChanges = false
    private var savingKey = false
    private var savingGeneration: Long? = null
    private var destroyed = false
    private lateinit var keyInput: EditText
    private lateinit var modelInput: Spinner
    private lateinit var keyState: TextView
    private lateinit var checkButton: Button
    private lateinit var forgetButton: Button
    private lateinit var rememberKey: CheckBox
    private lateinit var saveModelButton: Button

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityPaused(a: Activity) {
            if (a === activity) invalidateCheck("Verification cancelled when the app left the foreground. Check again to use your key.", onlyWhileChecking = true)
        }
        override fun onActivityDestroyed(a: Activity) { if (a === activity) dialog.dismiss() }
        override fun onActivityCreated(a: Activity, state: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityResumed(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}
    }

    fun show() {
        dialog.setContentView(build())
        dialog.setOnDismissListener {
            destroyed = true
            invalidateCheck()
            activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
            keyInput.text.clear()
            scope.cancel()
        }
        ReferencePanelStyle.show(dialog)
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
    }

    private fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(ReferencePanelStyle.header(activity, "Settings") { dialog.dismiss() })
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(14))
        }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))

        val systemCard = ReferencePanelStyle.card(activity)
        systemCard.addView(ReferencePanelStyle.label(activity, "Target system", 14f, ReferencePanelStyle.GREEN, true))
        systemCard.addView(ReferencePanelStyle.label(activity,
            prefs.getString(TargetSystemPanel.TARGET_KEY, TargetSystemPanel.DEFAULT_TARGET) ?: TargetSystemPanel.DEFAULT_TARGET,
            13f).apply { setPadding(0, dp(8), 0, dp(8)) })
        systemCard.addView(ReferencePanelStyle.darkButton(activity, "Change target system") {
            dialog.dismiss()
            TargetSystemPanel(activity).show()
        }, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(systemCard, sectionLp())

        val voiceCard = ReferencePanelStyle.card(activity)
        voiceCard.addView(ReferencePanelStyle.label(activity, "Spoken explanations", 14f, ReferencePanelStyle.GREEN, true))
        voiceCard.addView(ReferencePanelStyle.label(activity,
            "Auto explains why each step helps and what to expect. OpenAI speech is AI-generated and uses paid audio requests; explanation text goes to OpenAI. Phone voice uses only an available offline voice. Audio is kept in memory and stopped on background/close.", 12f))
        val voiceInput = Spinner(activity).apply {
            adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, NarrationVoice.values().map { it.label }) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getView(position, convertView, parent).apply { (this as TextView).setTextColor(Color.WHITE); textSize = 13f }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getDropDownView(position, convertView, parent).apply { (this as TextView).setTextColor(Color.BLACK); setBackgroundColor(Color.WHITE) }
            }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            val saved = prefs.getString(AssistantNarrator.VOICE_KEY, NarrationVoice.OPENAI.name)
            setSelection(NarrationVoice.values().indexOfFirst { it.name == saved }.coerceAtLeast(0))
            contentDescription = "Explanation voice"
        }
        voiceCard.addView(voiceInput, LinearLayout.LayoutParams(-1, dp(56)))
        voiceCard.addView(ReferencePanelStyle.darkButton(activity, "Save explanation voice") {
            prefs.edit().putString(AssistantNarrator.VOICE_KEY, NarrationVoice.values()[voiceInput.selectedItemPosition].name).apply()
            Toast.makeText(activity, "Explanation voice saved. No audio request was made.", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(voiceCard, sectionLp())

        val apiCard = ReferencePanelStyle.card(activity)
        apiCard.addView(ReferencePanelStyle.label(activity, "OpenAI API key", 14f, ReferencePanelStyle.GREEN, true))
        keyInput = EditText(activity).apply {
            hint = if (vault.exists()) "Saved key available · enter to replace" else "API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSaveEnabled = false
            BlackCatStyle.styleInput(activity, this)
        }
        apiCard.addView(keyInput, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(7) })
        apiCard.addView(ReferencePanelStyle.label(activity, "Model", 12f, ReferencePanelStyle.MUTED, true).apply {
            setPadding(0, dp(10), 0, dp(5))
        })
        modelInput = Spinner(activity).apply {
            adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, PlanCodec.MODELS) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getView(position, convertView, parent).apply {
                        (this as TextView).setTextColor(Color.WHITE)
                        textSize = 14f
                    }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getDropDownView(position, convertView, parent).apply {
                        (this as TextView).setTextColor(Color.BLACK)
                        setBackgroundColor(Color.WHITE)
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                    }
            }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            val saved = prefs.getString(MODEL_KEY, PlanCodec.DEFAULT_MODEL) ?: PlanCodec.DEFAULT_MODEL
            setSelection(PlanCodec.MODELS.indexOf(saved).coerceAtLeast(0))
            contentDescription = "OpenAI model"
        }
        apiCard.addView(modelInput, LinearLayout.LayoutParams(-1, dp(50)))
        keyState = ReferencePanelStyle.label(activity,
            if (vault.exists()) "Saved key is encrypted on this phone. Leave the field blank to verify it."
            else "No saved API key. Enter your key, then check it. Remembering it is optional.", 12f, ReferencePanelStyle.MUTED
        ).apply { setPadding(0, dp(8), 0, dp(8)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        apiCard.addView(keyState)
        rememberKey = CheckBox(activity).apply {
            text = "Remember encrypted key on this phone"
            setTextColor(Color.WHITE)
            textSize = 13f
            isChecked = false
            buttonTintList = ColorStateList.valueOf(ReferencePanelStyle.GREEN)
            minHeight = dp(48)
        }
        apiCard.addView(rememberKey, LinearLayout.LayoutParams(-1, -2))
        apiCard.addView(ReferencePanelStyle.label(activity,
            "Checking contacts OpenAI for the selected model's metadata. It does not generate a plan or verify billing, quota or image access.",
            12f, ReferencePanelStyle.MUTED).apply { setPadding(0, 0, 0, dp(10)) })
        checkButton = ReferencePanelStyle.greenButton(activity, "Check & use API key") { checkAndSave() }
        apiCard.addView(checkButton, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(7) })
        saveModelButton = ReferencePanelStyle.darkButton(activity, "Save model only") {
            invalidateCheck()
            prefs.edit().putString(MODEL_KEY, selectedModel()).apply()
            setKeyState("Model saved. Your saved API key was not changed.")
        }
        apiCard.addView(saveModelButton, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(7) })
        forgetButton = ReferencePanelStyle.darkButton(activity, "Forget saved API key") { forgetKey() }
        apiCard.addView(forgetButton, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(apiCard, sectionLp())

        val connectionCard = ReferencePanelStyle.card(activity)
        connectionCard.addView(ReferencePanelStyle.label(activity, "Bluetooth", 14f, ReferencePanelStyle.GREEN, true))
        connectionCard.addView(ReferencePanelStyle.darkButton(activity, "Bluetooth setup / connection") {
            dialog.dismiss()
            (activity as MainActivity).openBluetoothSetup()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        connectionCard.addView(ReferencePanelStyle.darkButton(activity, "Disconnect computer") {
            dialog.dismiss()
            (activity as MainActivity).endBluetoothSession()
            activity.navigateHome()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(7) })
        body.addView(connectionCard, sectionLp())
        val updatesCard = ReferencePanelStyle.card(activity)
        updatesCard.addView(ReferencePanelStyle.label(activity, "App updates", 14f, ReferencePanelStyle.GREEN, true))
        updatesCard.addView(ReferencePanelStyle.darkButton(activity, "Check for updates") {
            activity.startActivity(android.content.Intent(activity, UpdateActivity::class.java))
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        body.addView(updatesCard, sectionLp())
        body.addView((activity as MainActivity).createDiagnosticsView(), sectionLp())
        val aboutCard = ReferencePanelStyle.card(activity)
        aboutCard.addView(ReferencePanelStyle.label(activity, "About Black Cat AI", 14f, ReferencePanelStyle.GREEN, true))
        aboutCard.addView(ReferencePanelStyle.label(activity,
            "Phone keyboard and mouse with reviewed Linux plans. Ordinary typing never sends Enter. Full Auto can run the plan you explicitly approve, with camera checks and a Stop control. Keys are optionally encrypted on this phone; there is no shared developer key.",
            12f).apply { setPadding(0, dp(8), 0, 0) })
        body.addView(aboutCard, sectionLp())
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ReferencePanelStyle.bottomNav(activity, "Settings") { dialog.dismiss() })

        keyInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressInputChanges) invalidateCheck("API key changed. Check it before saving.")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        rememberKey.setOnCheckedChangeListener { _, _ ->
            if (!suppressInputChanges) invalidateCheck("Key storage choice changed. Check again to use it.")
            setChecking(checkJob?.isActive == true)
        }
        var previousModel = selectedModel()
        modelInput.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val value = selectedModel()
                if (value != previousModel) {
                    previousModel = value
                    invalidateCheck("Model changed. Check access before saving your key.")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        return root
    }

    private fun checkAndSave() {
        if (savingKey || destroyed) return
        invalidateCheck()
        val generation = checkGeneration
        val model = selectedModel()
        val enteredKey = keyInput.text.toString().trim()
        val replacingKey = enteredKey.isNotBlank()
        val remember = rememberKey.isChecked
        if (!replacingKey && !vault.exists()) {
            setKeyState("Enter your OpenAI API key before checking it.", error = true)
            return
        }
        setChecking(true)
        setKeyState("Checking API key and access to $model…")
        checkJob = scope.launch {
            try {
                val value = if (replacingKey) enteredKey else withContext(Dispatchers.IO) { vault.read() }.orEmpty()
                if (!isCurrent(generation, model)) return@launch
                val result = keyChecker.check(value, model)
                if (!isCurrent(generation, model)) return@launch
                if (!result.verified) {
                    setKeyState("${result.message} Your saved key was not changed.", error = true)
                    return@launch
                }
                if (replacingKey && remember) {
                    savingKey = true
                    savingGeneration = generation
                    keyInput.isEnabled = false
                    modelInput.isEnabled = false
                    forgetButton.isEnabled = false
                    rememberKey.isEnabled = false
                    saveModelButton.isEnabled = false
                    setKeyState("Key accepted. Saving encrypted key on this phone…")
                    withContext(Dispatchers.IO) { vault.save(value) }
                }
                if (!isCurrent(generation, model)) return@launch
                if (replacingKey && !remember) PendingApiKey.put(activity, value)
                else PendingApiKey.clear(activity)
                prefs.edit().putString(MODEL_KEY, model).apply()
                suppressInputChanges = true
                keyInput.text.clear()
                suppressInputChanges = false
                keyInput.hint = if (vault.exists()) "Saved key available · enter to replace" else "API key"
                val storage = if (replacingKey && !remember)
                    "Key held in memory for your next explicit AI request; it is not saved."
                    else "Key encrypted on this phone."
                setKeyState("${result.message} $storage Billing, generation and photo access still need a real request.", verified = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (isCurrent(generation, model)) {
                    setKeyState("Key could not be read or saved securely. No plaintext fallback was used.", error = true)
                }
            } finally {
                if (!destroyed && (generation == checkGeneration || savingGeneration == generation)) {
                    savingKey = false
                    savingGeneration = null
                    keyInput.isEnabled = true
                    modelInput.isEnabled = true
                    forgetButton.isEnabled = true
                    rememberKey.isEnabled = true
                    saveModelButton.isEnabled = true
                    setChecking(false)
                    checkJob = null
                }
            }
        }
    }

    private fun forgetKey() {
        if (savingKey || destroyed) return
        invalidateCheck()
        // Explicit forgetting clears the queued key even if encrypted deletion is interrupted.
        PendingApiKey.clear(activity)
        val generation = checkGeneration
        savingKey = true
        savingGeneration = generation
        keyInput.isEnabled = false
        modelInput.isEnabled = false
        forgetButton.isEnabled = false
        rememberKey.isEnabled = false
        saveModelButton.isEnabled = false
        checkButton.isEnabled = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) { vault.forget() }
                if (generation != checkGeneration || destroyed) return@launch
                suppressInputChanges = true
                keyInput.text.clear()
                suppressInputChanges = false
                keyInput.hint = "API key"
                setKeyState("Saved API key removed.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!destroyed) setKeyState("Saved API key could not be removed securely.", error = true)
            } finally {
                if (!destroyed) {
                    savingKey = false
                    savingGeneration = null
                    keyInput.isEnabled = true
                    modelInput.isEnabled = true
                    forgetButton.isEnabled = true
                    rememberKey.isEnabled = true
                    saveModelButton.isEnabled = true
                    setChecking(false)
                }
            }
        }
    }

    private fun selectedModel(): String =
        PlanCodec.MODELS[modelInput.selectedItemPosition.coerceIn(PlanCodec.MODELS.indices)]

    private fun isCurrent(generation: Long, model: String) =
        !destroyed && generation == checkGeneration && dialog.isShowing && model == selectedModel()

    private fun invalidateCheck(message: String? = null, onlyWhileChecking: Boolean = false) {
        val wasChecking = checkJob?.isActive == true
        checkGeneration++
        keyChecker.cancel()
        checkJob?.cancel()
        checkJob = null
        if (!destroyed && !savingKey && ::checkButton.isInitialized) setChecking(false)
        if (!destroyed && message != null && (!onlyWhileChecking || wasChecking)) setKeyState(message)
    }

    private fun setChecking(checking: Boolean) {
        checkButton.isEnabled = !checking && !savingKey
        checkButton.text = if (checking) "Checking…" else if (rememberKey.isChecked) "Check & save API key" else "Check & use API key"
    }

    private fun setKeyState(message: String, error: Boolean = false, verified: Boolean = false) {
        keyState.text = message
        keyState.setTextColor(when {
            error -> ReferencePanelStyle.ERROR
            verified -> ReferencePanelStyle.GREEN
            else -> ReferencePanelStyle.MUTED
        })
    }

    private fun dp(value: Int) = ReferencePanelStyle.dp(activity, value)
    private fun sectionLp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }

    companion object { const val MODEL_KEY = "model" }
}
