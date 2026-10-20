package com.blackcat.remote

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Button

/** Stable, selectable text/code cards. No approval, click sender, keys, persistence or reasoning text. */
internal class LivePlanView(context: Context) : LinearLayout(context) {
    private val ink = Color.rgb(223, 235, 228)
    private val green = Color.rgb(103, 240, 104)
    private val status: TextView
    private val explanation: TextView
    private val phaseHost: LinearLayout
    private val commandHost: LinearLayout
    private val question: TextView
    private val phaseToggle: Button
    private val phases = mutableListOf<Pair<TextView, TextView>>()
    private data class Row(val title: TextView, val code: TextView, val explanation: TextView)
    private val commands = mutableListOf<Row>()

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = shape(Color.rgb(19, 31, 25), 16)
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(green)
            contentDescription = "Receiving plan"
        }, LayoutParams(dp(32), dp(32)).apply { rightMargin = dp(10) })
        status = text("Building your plan…", 17f, true)
        heading.addView(status, LayoutParams(0, -2, 1f))
        addView(heading, LayoutParams(-1, -2))
        addView(text("LIVE DRAFT · Commands become available after final validation.", 12f).apply {
            setTextColor(green); setPadding(0, dp(8), 0, dp(10))
        })
        explanation = text("", 15f).also { addView(it) }
        phaseHost = LinearLayout(context).apply { orientation = VERTICAL }.also { addView(it, LayoutParams(-1, -2)) }
        phaseToggle = Button(context).apply {
            isAllCaps = false; textSize = 13f; minHeight = dp(48); setTextColor(green)
            background = shape(Color.rgb(29, 45, 36), 10)
            setOnClickListener { phaseHost.visibility = if (phaseHost.visibility == GONE) VISIBLE else GONE }
        }.also { addView(it, LayoutParams(-1, -2)) }
        commandHost = LinearLayout(context).apply { orientation = VERTICAL }.also { addView(it, LayoutParams(-1, -2)) }
        question = text("", 15f).also { addView(it) }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun update(draft: PlanStreamDraft, updating: String?) {
        status.text = when {
            draft.commands.isNotEmpty() -> "Drafting step ${draft.commands.size}…"
            updating != null -> "Updating $updating…"
            draft.phases.isNotEmpty() -> "Organizing the plan…"
            else -> "Building your plan…"
        }
        explanation.text = draft.explanation
        phaseToggle.visibility = if (draft.phases.isEmpty()) GONE else VISIBLE
        phaseToggle.text = "Roadmap · ${draft.phases.size} phases · show / hide"
        if (commands.isEmpty() && draft.commands.isNotEmpty()) phaseHost.visibility = GONE
        while (phases.size < draft.phases.size) {
            val box = LinearLayout(context).apply { orientation = VERTICAL; setPadding(0, dp(10), 0, 0) }
            val title = text("", 14f, true).apply { setTextColor(green) }
            val purpose = text("", 13f)
            box.addView(title); box.addView(purpose); phaseHost.addView(box)
            phases += title to purpose
        }
        draft.phases.forEachIndexed { index, phase ->
            phases[index].first.text = "${index + 1}. ${phase.first}"
            phases[index].second.text = phase.second
        }
        while (commands.size < draft.commands.size) {
            val box = LinearLayout(context).apply {
                orientation = VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
                background = shape(Color.rgb(29, 45, 36), 12)
            }
            val title = text("", 15f, true)
            val code = text("", 14f).apply {
                typeface = Typeface.MONOSPACE
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = shape(Color.rgb(8, 17, 12), 8)
                breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE
                hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            }
            val why = text("", 14f)
            box.addView(title); box.addView(code, LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) }); box.addView(why)
            commandHost.addView(box, LayoutParams(-1, -2).apply { topMargin = dp(10) })
            commands += Row(title, code, why)
        }
        draft.commands.forEachIndexed { i, command ->
            commands[i].title.text = "${i + 1}. ${command.title}"
            commands[i].title.setTextColor(if (i == draft.commands.lastIndex) green else ink)
            commands[i].code.text = command.command
            commands[i].code.visibility = if (command.command.isEmpty()) GONE else VISIBLE
            commands[i].explanation.text = command.explanation
        }
        question.text = draft.questions.joinToString("\n\n") { "Question: $it" }
    }

    fun reset() {
        phases.clear(); commands.clear(); phaseHost.removeAllViews(); commandHost.removeAllViews()
        phaseHost.visibility = VISIBLE
        explanation.text = ""; question.text = ""
    }

    fun followLatest() {
        val target = commandHost.getChildAt(commandHost.childCount - 1) ?: return
        target.post { if (isAttachedToWindow) target.requestRectangleOnScreen(android.graphics.Rect(0, 0, target.width, dp(150)), false) }
    }

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(ink); setTextIsSelectable(true)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
}
