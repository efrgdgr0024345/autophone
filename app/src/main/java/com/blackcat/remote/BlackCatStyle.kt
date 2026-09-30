package com.blackcat.remote

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object BlackCatStyle {
    val BG = Color.rgb(245, 248, 245)
    val PAPER = Color.WHITE
    val INK = Color.rgb(18, 38, 34)
    val MUTED = Color.rgb(86, 110, 103)
    val LINE = Color.rgb(212, 225, 217)
    val GREEN = Color.rgb(22, 97, 70)
    val GREEN_DARK = Color.rgb(13, 72, 43)
    val TINT = Color.rgb(234, 244, 231)
    val FIELD = Color.rgb(250, 252, 249)
    val FIELD_LINE = Color.rgb(190, 209, 198)
    val AMBER = Color.rgb(130, 83, 27)
    val AMBER_TINT = Color.rgb(255, 241, 219)
    val COMMAND_BG = Color.rgb(18, 44, 36)
    val COMMAND_TEXT = Color.rgb(224, 240, 199)

    fun dp(activity: Activity, value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    fun round(activity: Activity, fill: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(activity, radiusDp).toFloat()
            if (strokeColor != null && strokeDp > 0) setStroke(dp(activity, strokeDp), strokeColor)
        }

    fun styleButton(activity: Activity, button: Button, primary: Boolean = false, compact: Boolean = false) {
        button.isAllCaps = false
        button.textSize = if (compact) 12f else 14f
        button.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        button.setTextColor(if (primary) Color.WHITE else INK)
        button.background = round(activity, if (primary) GREEN else FIELD, 12, if (primary) GREEN else LINE, 1)
        button.minHeight = dp(activity, if (compact) 40 else 46)
        button.setPadding(dp(activity, 8), 0, dp(activity, 8), 0)
    }

    fun styleInput(activity: Activity, input: EditText) {
        input.setTextColor(INK)
        input.setHintTextColor(Color.rgb(113, 133, 124))
        input.textSize = 15f
        input.background = round(activity, FIELD, 11, FIELD_LINE, 1)
        input.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9))
    }

    fun label(activity: Activity, value: String, size: Float, color: Int = INK, bold: Boolean = false) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
        }

    fun card(activity: Activity) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
        background = round(activity, PAPER, 16, LINE, 1)
    }

    fun styleStatus(activity: Activity, view: TextView) {
        view.textSize = 12f
        view.setTextColor(GREEN)
        view.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        view.gravity = Gravity.CENTER
        view.background = round(activity, TINT, 22)
        view.setPadding(dp(activity, 10), dp(activity, 6), dp(activity, 10), dp(activity, 6))
    }

    @Suppress("DEPRECATION")
    fun applySystemBarInsets(view: View) {
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { v, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                top = bars.top
                bottom = bars.bottom
            } else {
                top = insets.systemWindowInsetTop
                bottom = insets.systemWindowInsetBottom
            }
            v.setPadding(
                baseLeft,
                baseTop + top,
                baseRight,
                baseBottom + bottom
            )
            insets
        }
        view.requestApplyInsets()
    }

    fun transparent(view: View) {
        view.background = ColorDrawable(Color.TRANSPARENT)
    }
}
