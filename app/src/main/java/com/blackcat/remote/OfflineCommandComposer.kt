package com.blackcat.remote

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** In-memory literal-argument editor. Produces data only; cannot create an approval or send input. */
internal class OfflineCommandComposer(private val activity: Activity, private val entry: OfflineCli,
    private val onPrepared: (LinuxCommandItem) -> Unit, private val onClosed: () -> Unit) {
    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
    private val arguments = mutableListOf<EditText>()
    private lateinit var argumentBody: LinearLayout
    private lateinit var preview: TextView
    private lateinit var error: TextView
    private lateinit var review: Button
    private lateinit var admin: CheckBox
    private var closed = false

    fun show() {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xfff5f8f5.toInt())
            isFocusableInTouchMode = true
        }
        BlackCatStyle.applySystemBarInsets(root)
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        header.addView(label("Prepare ${entry.invocation}", 20f, true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("Close") { dismiss() }, LinearLayout.LayoutParams(-2, -2))
        root.addView(header)
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(16)) }
        val scroll = ScrollView(activity).apply { isFillViewport = true; addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(label(entry.summary.takeIf { it.any(Char::isLetterOrDigit) } ?: "Review the owning tool and installed documentation before choosing arguments.", 14f))
        body.addView(label("Each field is one literal argument. Quotes, spaces and shell symbols stay inside that argument. The program can still interpret arguments itself. Maximum 512 characters in the final command.", 13f))
        argumentBody = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(argumentBody)
        body.addView(button("Add argument") { if (arguments.size < 24) addArgument() })
        body.addView(button("Remove last argument") {
            if (arguments.isNotEmpty()) { val field = arguments.removeAt(arguments.lastIndex); field.text.clear(); argumentBody.removeViewAt(argumentBody.childCount - 1); refresh() }
        })
        admin = CheckBox(activity).apply { text = "Prefix with sudo · a password may be requested"; minHeight = dp(48); setOnCheckedChangeListener { _, _ -> refresh() } }
        body.addView(admin)
        body.addView(label("Exact command preview", 15f, true))
        preview = label("", 15f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(Color.WHITE) }
        body.addView(preview)
        body.addView(label("Effects and network destinations depend on the selected tool and arguments. Prepared tasks start at HIGH risk; review the actual computer and task before typing.", 13f))
        body.addView(label("Documented options (names only)", 15f, true))
        body.addView(label(entry.flags.take(80).joinToString(", ").ifBlank { "No options were extracted. Consult the installed manual; do not guess required arguments." }, 13f))
        error = label("", 14f).apply { setTextColor(0xffa22424.toInt()); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        body.addView(error)
        review = button("Review exact command") {
            try {
                val item = prepared()
                dismiss()
                onPrepared(item)
            } catch (invalid: IllegalArgumentException) { error.text = invalid.message; refresh() }
        }
        BlackCatStyle.styleButton(activity, review, true)
        review.minHeight = dp(56)
        root.addView(review, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(6), dp(12), dp(10)) })
        dialog.setContentView(root)
        dialog.setOnDismissListener {
            closed = true
            arguments.forEach { it.text.clear() }; arguments.clear()
            preview.text = ""; onClosed()
        }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.show(); dialog.window?.setLayout(-1, -1)
        refresh()
    }
    private fun addArgument() {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        row.addView(label("Argument ${arguments.size + 1}", 13f, true))
        val field = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true); minHeight = dp(52)
            hint = "One value; an empty field means an empty argument"
            setTextColor(Color.BLACK)
        }
        arguments.add(field); row.addView(field, LinearLayout.LayoutParams(-1, -2)); argumentBody.addView(row)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
            override fun afterTextChanged(s: Editable?) {}
        })
        field.requestFocus(); refresh()
    }
    private fun prepared() = OfflineWorkflows.prepare(entry, arguments.map { it.text.toString() }, admin.isChecked)
    private fun refresh() {
        if (closed || !::review.isInitialized) return
        try {
            preview.text = prepared().command
            error.text = ""
            review.isEnabled = true
            review.text = if (arguments.isEmpty()) "Review command without arguments" else "Review exact command"
        } catch (invalid: IllegalArgumentException) {
            preview.text = "Resolve the argument error to preview the exact command."
            error.text = invalid.message
            review.isEnabled = false
        }
    }
    fun dismiss() { if (dialog.isShowing) dialog.dismiss() }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(0xff142b23.toInt()); setPadding(0, dp(6), 0, dp(6))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(value: String, action: () -> Unit) = Button(activity).apply {
        text = value; isAllCaps = false; minHeight = dp(48); setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
