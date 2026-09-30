package com.blackcat.remote

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object ReferencePanelStyle {
    val DARK = 0xff101714.toInt()
    val DARK_SOFT = 0xff18221e.toInt()
    val LINE = 0xff30423b.toInt()
    val GREEN = 0xff74f45b.toInt()
    val MUTED = 0xffb7c5be.toInt()

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

    fun header(activity: Activity, title: String, image: Int, close: () -> Unit): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 8), 0)
            }
            row.addView(TextView(activity).apply {
                text = "‹"
                textSize = 34f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "Close"
                setOnClickListener { close() }
            }, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)))
            row.addView(label(activity, title, 18f, Color.BLACK, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, dp(activity, 42), 1f))
            row.addView(TextView(activity).apply {
                text = "⋮"
                textSize = 24f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)))
            addView(row)
            addView(ImageView(activity).apply {
                setImageResource(image)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "Black Cat"
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 140)))
        }
    }

    fun bottomNav(activity: Activity, active: String, close: () -> Unit, target: (() -> Unit)? = null, settings: (() -> Unit)? = null): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4))
            setBackgroundColor(DARK)
            fun add(icon: String, title: String, action: () -> Unit) {
                val selected = title == active
                val item = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { action() }
                    addView(label(activity, icon, 17f, if (selected) GREEN else Color.WHITE, true).apply { gravity = Gravity.CENTER })
                    addView(label(activity, title, 10f, if (selected) GREEN else Color.WHITE, true).apply { gravity = Gravity.CENTER })
                }
                addView(item, LinearLayout.LayoutParams(0, dp(activity, 56), 1f))
            }
            add("⌂", "Home") { close() }
            add("✣", "AI") { close() }
            add("▣", "Target") { (target ?: close).invoke() }
            add("⚙", "Settings") { (settings ?: close).invoke() }
        }
    }
}

class TargetSystemPanel(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
    private var selected = prefs.getString(TARGET_KEY, DEFAULT_TARGET)?.trim().orEmpty().ifBlank { DEFAULT_TARGET }

    fun show() {
        dialog.setContentView(build())
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window?.statusBarColor = Color.WHITE
        dialog.window?.navigationBarColor = ReferencePanelStyle.DARK
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    private fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(ReferencePanelStyle.header(activity, "Target System", R.drawable.black_cat_peek) { dialog.dismiss() })

        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(14))
        }
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = BlackCatStyle.round(activity, ReferencePanelStyle.DARK, 18, ReferencePanelStyle.LINE, 1)
        }
        card.addView(ReferencePanelStyle.label(activity, "Select Target System", 14f, ReferencePanelStyle.GREEN, true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val options = COMMON_TARGETS.toMutableList()
        val custom = prefs.getString(CUSTOM_KEY, "")?.trim().orEmpty()
        if (custom.isNotBlank() && custom !in options) options.add(custom)

        options.forEach { option ->
            val chosen = option == selected
            val prefix = if (chosen) "✓  " else "   "
            card.addView(ReferencePanelStyle.darkButton(activity, prefix + option, chosen) {
                selected = option
                prefs.edit().putString(TARGET_KEY, option).apply()
                dialog.dismiss()
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(5) })
        }

        card.addView(ReferencePanelStyle.darkButton(activity, "✎  Edit / Custom…") { openCustomEditor() },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(3) })
        body.addView(card)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ReferencePanelStyle.bottomNav(activity, "Target", { dialog.dismiss() }, { }, {
            dialog.dismiss()
            AiSettingsPanel(activity).show()
        }), LinearLayout.LayoutParams(-1, dp(56)))
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
        val editor = AlertDialog.Builder(activity)
            .setTitle("Custom System")
            .setView(wrap)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val n = name.text.toString().trim()
                val d = details.text.toString().trim()
                if (n.isNotBlank()) {
                    val value = if (d.isBlank()) n else "$n — $d"
                    prefs.edit().putString(TARGET_KEY, value).putString(CUSTOM_KEY, value).apply()
                    selected = value
                    dialog.dismiss()
                    TargetSystemPanel(activity).show()
                }
            }
            .create()
        editor.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        editor.show()
    }

    private fun dp(value: Int) = ReferencePanelStyle.dp(activity, value)

    companion object {
        const val PREFS = "blackcat_ai_ui"
        const val TARGET_KEY = "target_system"
        const val CUSTOM_KEY = "target_custom"
        const val DEFAULT_TARGET = "Ubuntu Linux / Bash"
        val COMMON_TARGETS = listOf(
            "Ubuntu Linux / Bash",
            "Debian Linux / Bash",
            "Linux Mint / Bash",
            "Kali Linux / Bash",
            "Fedora Linux / Bash",
            "openSUSE Linux / Bash",
            "Arch Linux / Bash"
        )
    }
}

class AiSettingsPanel(private val activity: Activity) {
    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
    private val vault = ApiKeyVault(activity.applicationContext)
    private val prefs = activity.getSharedPreferences(TargetSystemPanel.PREFS, Activity.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun show() {
        dialog.setContentView(build())
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { scope.cancel() }
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window?.statusBarColor = Color.WHITE
        dialog.window?.navigationBarColor = ReferencePanelStyle.DARK
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    private fun build(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(ReferencePanelStyle.header(activity, "Settings", R.drawable.black_cat_portrait) { dialog.dismiss() })

        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(14))
        }
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = BlackCatStyle.round(activity, ReferencePanelStyle.DARK, 18, ReferencePanelStyle.LINE, 1)
        }
        card.addView(ReferencePanelStyle.label(activity, "OpenAI API", 14f, Color.WHITE, true))
        val key = EditText(activity).apply {
            hint = if (vault.exists()) "Saved key available · enter to replace" else "API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            BlackCatStyle.styleInput(activity, this)
        }
        card.addView(key, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(7) })

        card.addView(ReferencePanelStyle.label(activity, "Model", 12f, ReferencePanelStyle.MUTED, true).apply {
            setPadding(0, dp(10), 0, dp(5))
        })
        val model = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, PlanCodec.MODELS)
            val saved = prefs.getString(MODEL_KEY, PlanCodec.DEFAULT_MODEL) ?: PlanCodec.DEFAULT_MODEL
            setSelection(PlanCodec.MODELS.indexOf(saved).coerceAtLeast(0))
        }
        card.addView(model, LinearLayout.LayoutParams(-1, dp(50)))

        val state = ReferencePanelStyle.label(
            activity,
            if (vault.exists()) "Saved key is encrypted on this phone." else "No saved API key on this phone.",
            11f,
            ReferencePanelStyle.MUTED
        ).apply { setPadding(0, dp(8), 0, dp(8)) }
        card.addView(state)

        card.addView(ReferencePanelStyle.greenButton(activity, "Save Settings") {
            val selectedModel = PlanCodec.MODELS[model.selectedItemPosition.coerceIn(PlanCodec.MODELS.indices)]
            prefs.edit().putString(MODEL_KEY, selectedModel).apply()
            val value = key.text.toString().trim()
            if (value.isBlank()) {
                state.text = "Model saved. Existing API key was not changed."
                return@greenButton
            }
            if (!OpenAiPlanner.validKey(value)) {
                state.text = "Enter a valid OpenAI API key."
                return@greenButton
            }
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { vault.save(value) }
                    key.text.clear()
                    state.text = "Settings saved. API key is encrypted on this phone."
                } catch (_: Exception) {
                    state.text = "Key could not be saved securely. No plaintext fallback was used."
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(7) })

        card.addView(ReferencePanelStyle.darkButton(activity, "Forget saved API key") {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { vault.forget() }
                    state.text = "Saved API key removed."
                } catch (_: Exception) {
                    state.text = "Saved API key could not be removed."
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(46)))

        card.addView(ReferencePanelStyle.label(activity, "App Preferences", 13f, ReferencePanelStyle.GREEN, true).apply {
            setPadding(0, dp(15), 0, dp(5))
        })
        fun preferenceRow(title: String, checked: Boolean): View {
            return LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ReferencePanelStyle.label(activity, title, 12f, Color.WHITE),
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(Switch(activity).apply { isChecked = checked; isEnabled = false })
            }
        }
        card.addView(preferenceRow("Save target system", true))
        card.addView(preferenceRow("Dark control panels", true))
        card.addView(ReferencePanelStyle.label(activity, "About", 13f, ReferencePanelStyle.GREEN, true).apply {
            setPadding(0, dp(12), 0, dp(4))
        })
        card.addView(ReferencePanelStyle.label(activity, "Black Cat AI\nUI08 reference-screen build\nBuilt around the preserved Bluetooth HID core.", 12f, Color.WHITE))

        body.addView(card)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ReferencePanelStyle.bottomNav(activity, "Settings", { dialog.dismiss() }, {
            dialog.dismiss()
            TargetSystemPanel(activity).show()
        }, { }), LinearLayout.LayoutParams(-1, dp(56)))
        return root
    }

    private fun dp(value: Int) = ReferencePanelStyle.dp(activity, value)

    companion object {
        const val MODEL_KEY = "model"
    }
}
