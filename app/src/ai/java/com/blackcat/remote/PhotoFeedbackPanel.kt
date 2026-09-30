package com.blackcat.remote

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.*

internal class PhotoFeedbackPanel(
    private val activity: Activity,
    private val manager: HidManager,
    private val onClosed: () -> Unit
) {
    private enum class State { CAPTURE, REVIEW, ANALYZING, RESULT }

    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val vault = ApiKeyVault(activity.applicationContext)
    private val analyzer = OpenAiPhotoAnalyzer()
    private val target = AiTransportScope(activity, manager) { dismiss() }

    private var targetStarted = false
    private var camera: PhotoCaptureController? = null
    private var jpeg: ByteArray? = null
    private var analysis: PhotoAnalysis? = null
    private var selectedCommand = 0
    private var state = State.CAPTURE
    private var busy = false

    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var goal: EditText
    private lateinit var texture: TextureView
    private lateinit var image: ImageView

    fun show() {
        root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(buildHeader())
        status = label("Take a clear photo of the terminal or error screen.", 12f, MUTED).apply {
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }
        root.addView(status)
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(18))
        }
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            camera?.close()
            camera = null
            analyzer.cancel()
            scope.cancel()
            if (targetStarted) target.close()
            jpeg?.fill(0)
            jpeg = null
            analysis = null
            onClosed()
        }
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window?.statusBarColor = Color.WHITE
        dialog.window?.navigationBarColor = Color.WHITE
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        render()
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    private fun buildHeader(): View {
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(10), dp(8), dp(10), dp(4))
        }
        val portrait = ImageView(activity).apply {
            setImageResource(R.drawable.black_cat_portrait)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Black Cat"
        }
        frame.addView(portrait, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.START or Gravity.CENTER_VERTICAL))
        val title = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(102), dp(15), dp(46), dp(10))
            addView(label("Photo Feedback", 22f, Color.BLACK, Typeface.BOLD))
            addView(label("Show Black Cat what the computer is displaying.", 12f, Color.DKGRAY))
        }
        frame.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val close = Button(activity).apply {
            text = "×"
            textSize = 22f
            isAllCaps = false
            background = ColorDrawable(Color.TRANSPARENT)
            setTextColor(Color.BLACK)
            setOnClickListener { dismiss() }
        }
        frame.addView(close, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END or Gravity.TOP))
        return frame
    }

    private fun render() {
        body.removeAllViews()
        when (state) {
            State.CAPTURE -> renderCapture()
            State.REVIEW -> renderReview()
            State.ANALYZING -> renderAnalyzing()
            State.RESULT -> renderResult()
        }
    }

    private fun renderCapture() {
        val panel = panel()
        panel.addView(label("What are you trying to do?", 15f, Color.WHITE, Typeface.BOLD))
        goal = EditText(activity).apply {
            setText("Understand this terminal screen and suggest the next safe step.")
            setTextColor(Color.WHITE)
            setHintTextColor(0xff9eaaa5.toInt())
            textSize = 14f
            minHeight = dp(76)
            background = round(PANEL_SOFT, 12, PANEL_LINE, 1)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        panel.addView(goal, matchWrap(top = 8, bottom = 10))
        texture = TextureView(activity).apply {
            background = round(Color.BLACK, 14)
        }
        panel.addView(texture, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)).apply { bottomMargin = dp(10) })

        val cameraPermission = activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val action = Button(activity).apply {
            text = if (cameraPermission) "Start camera" else "Enable camera"
            BlackCatStyle.styleButton(activity, this, primary = true)
            setOnClickListener {
                if (!cameraPermission) {
                    activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
                    setStatus("Grant camera access, then tap Start camera.")
                } else {
                    startCamera()
                    text = "Take photo"
                    setOnClickListener { camera?.capture() }
                }
            }
        }
        panel.addView(action, matchWrap())
        panel.addView(label("The photo stays in memory until you explicitly send it to OpenAI. It is not saved to the gallery by this app.", 11f, PANEL_MUTED).apply {
            setPadding(0, dp(9), 0, 0)
        })
        body.addView(panel)
    }

    private fun startCamera() {
        if (!targetStarted) {
            target.start()
            targetStarted = true
        }
        camera?.close()
        camera = PhotoCaptureController(activity, texture, { bytes ->
            if (bytes.size > MAX_JPEG_BYTES) {
                bytes.fill(0)
                setStatus("Photo is too large. Retake it.")
                return@PhotoCaptureController
            }
            jpeg?.fill(0)
            jpeg = bytes
            camera?.close()
            camera = null
            state = State.REVIEW
            setStatus("Review exactly what will be sent.")
            render()
        }, { error ->
            setStatus(error)
        }).also { it.start() }
        setStatus("Camera ready. Frame the terminal or error clearly.")
    }

    private fun renderReview() {
        val bytes = jpeg ?: run {
            state = State.CAPTURE
            render()
            return
        }
        val panel = panel()
        panel.addView(label("Review photo", 18f, Color.WHITE, Typeface.BOLD))
        panel.addView(label("Only send it if this is the screen you want OpenAI to analyze.", 12f, PANEL_MUTED).apply {
            setPadding(0, dp(4), 0, dp(9))
        })
        image = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            setImageBitmap(bitmap)
        }
        panel.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)).apply { bottomMargin = dp(9) })
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val retake = Button(activity).apply {
            text = "Retake"
            BlackCatStyle.styleButton(activity, this, primary = false)
            setOnClickListener {
                jpeg?.fill(0); jpeg = null; analysis = null; state = State.CAPTURE; render()
            }
        }
        val use = Button(activity).apply {
            text = "Send photo to OpenAI"
            BlackCatStyle.styleButton(activity, this, primary = true)
            setOnClickListener { analyzePhoto() }
        }
        buttons.addView(retake, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        buttons.addView(use, LinearLayout.LayoutParams(0, dp(48), 2f))
        panel.addView(buttons)
        body.addView(panel)
    }

    private fun analyzePhoto() {
        if (busy) return
        val bytes = jpeg ?: return
        val requestedGoal = if (::goal.isInitialized) goal.text.toString().trim() else
            "Understand this terminal screen and suggest the next safe step."
        val prefs = activity.getSharedPreferences("blackcat_ai_ui", Activity.MODE_PRIVATE)
        val targetSystem = prefs.getString("target_system", "Ubuntu Linux / Bash")?.trim().orEmpty().ifBlank { "Ubuntu Linux / Bash" }
        busy = true
        state = State.ANALYZING
        setStatus("Sending the reviewed photo to OpenAI. Nothing is being typed.")
        render()
        scope.launch {
            try {
                val key = withContext(Dispatchers.IO) { vault.read().orEmpty() }
                if (!OpenAiPlanner.validKey(key)) throw PlanException("No saved OpenAI key. Open Linux Assistant → Settings and save your key first.")
                analysis = withTimeout(75000) {
                    analyzer.analyze(requestedGoal, targetSystem, PlanCodec.DEFAULT_MODEL, key, bytes)
                }
                selectedCommand = 0
                state = State.RESULT
                setStatus("Photo analysis ready. Review it before typing anything.")
            } catch (_: TimeoutCancellationException) {
                analyzer.cancel()
                state = State.REVIEW
                setStatus("Photo analysis timed out. Nothing was typed.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = State.REVIEW
                setStatus(e.message ?: "Photo analysis failed.")
            } finally {
                busy = false
                render()
            }
        }
    }

    private fun renderAnalyzing() {
        val panel = panel()
        panel.gravity = Gravity.CENTER
        panel.addView(label("Analyzing photo…", 20f, Color.WHITE, Typeface.BOLD).apply { gravity = Gravity.CENTER })
        panel.addView(label("The image and your task context are being sent to OpenAI. No keyboard input is happening.", 13f, PANEL_MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
        })
        val stop = Button(activity).apply {
            text = "Cancel"
            BlackCatStyle.styleButton(activity, this, primary = false)
            setOnClickListener {
                analyzer.cancel()
                busy = false
                state = State.REVIEW
                setStatus("Cancelled. Nothing was typed.")
                render()
            }
        }
        panel.addView(stop, matchWrap(top = 8))
        body.addView(panel)
    }

    private fun renderResult() {
        val value = analysis ?: run {
            state = State.REVIEW
            render()
            return
        }
        val panel = panel()
        panel.addView(label("AI analysis", 19f, Color.WHITE, Typeface.BOLD))
        panel.addView(label(value.summary, 14f, Color.WHITE).apply { setPadding(0, dp(7), 0, dp(8)) })

        if (value.observations.isNotEmpty()) {
            panel.addView(label("What the photo appears to show", 12f, GREEN, Typeface.BOLD))
            value.observations.forEach { panel.addView(label("• $it", 13f, PANEL_TEXT).apply { setPadding(0, dp(4), 0, 0) }) }
        }
        if (value.questions.isNotEmpty()) {
            panel.addView(label("Need more information", 12f, GREEN, Typeface.BOLD).apply { setPadding(0, dp(12), 0, 0) })
            value.questions.forEach { panel.addView(label("• $it", 13f, PANEL_TEXT).apply { setPadding(0, dp(4), 0, 0) }) }
        }

        if (value.commands.isEmpty()) {
            panel.addView(label("No command is available until the uncertainty above is resolved.", 12f, PANEL_MUTED).apply {
                setPadding(0, dp(12), 0, 0)
            })
        } else {
            panel.addView(label("Suggested next command", 12f, GREEN, Typeface.BOLD).apply { setPadding(0, dp(12), 0, dp(5)) })
            value.commands.forEachIndexed { index, cmd ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = round(if (index == selectedCommand) PANEL_SELECTED else PANEL_SOFT, 12, PANEL_LINE, 1)
                    isClickable = true
                    setOnClickListener { selectedCommand = index; render() }
                }
                row.addView(label("${index + 1}. ${cmd.title}", 14f, Color.WHITE, Typeface.BOLD))
                row.addView(label(cmd.command, 13f, COMMAND_TEXT).apply {
                    typeface = Typeface.MONOSPACE
                    setPadding(0, dp(5), 0, dp(3))
                    setTextIsSelectable(true)
                })
                row.addView(label(cmd.explanation, 12f, PANEL_MUTED))
                panel.addView(row, matchWrap(top = 6))
            }
            val host = if (targetStarted) target.current() else null
            val type = Button(activity).apply {
                text = if (host == null) "Connect Bluetooth to type" else "Review & TYPE ONLY"
                BlackCatStyle.styleButton(activity, this, primary = true)
                isEnabled = host != null
                setOnClickListener { confirmType() }
            }
            panel.addView(type, matchWrap(top = 10))
            panel.addView(label("TYPE ONLY never presses Enter. You inspect the computer and press Enter yourself.", 11f, PANEL_MUTED).apply {
                setPadding(0, dp(7), 0, 0)
            })
        }

        val retake = Button(activity).apply {
            text = "Take another photo"
            BlackCatStyle.styleButton(activity, this, primary = false)
            setOnClickListener {
                jpeg?.fill(0); jpeg = null; analysis = null; state = State.CAPTURE; render()
            }
        }
        panel.addView(retake, matchWrap(top = 12))
        body.addView(panel)
    }

    private fun confirmType() {
        val cmd = analysis?.commands?.getOrNull(selectedCommand) ?: return
        val host = target.current() ?: return setStatus("Bluetooth host is not ready.")
        val problem = CommandPolicy.problem(cmd.command)
        if (problem != null) return setStatus(problem)
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
            addView(label("Destination", 12f, Color.DKGRAY, Typeface.BOLD))
            addView(label(host.label, 15f, Color.BLACK, Typeface.BOLD).apply { setPadding(0, dp(4), 0, dp(10)) })
            addView(label(cmd.command, 14f, Color.WHITE).apply {
                typeface = Typeface.MONOSPACE
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = round(COMMAND_BG, 12)
                setTextIsSelectable(true)
            })
            addView(label("This button types this exact text once. It does not press Enter.", 12f, GREEN, Typeface.BOLD).apply {
                setPadding(0, dp(9), 0, 0)
            })
        }
        val confirm = AlertDialog.Builder(activity)
            .setTitle("Review before typing")
            .setView(view)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("TYPE ONLY", null)
            .create()
        confirm.setOnShowListener {
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (target.current() != host) {
                    confirm.dismiss()
                    setStatus("Bluetooth connection changed. Review again.")
                    return@setOnClickListener
                }
                confirm.dismiss()
                busy = true
                setStatus("Typing reviewed text only. Enter excluded.")
                scope.launch {
                    try {
                        val outcome = ApprovedCommandSender.send(CommandApproval(cmd.command, host), target)
                        setStatus(if (outcome == SendOutcome.REPORTS_ACCEPTED)
                            "Text typed. Inspect the computer and press Enter yourself if satisfied."
                        else "Typing stopped ($outcome). Inspect any partial text.")
                    } finally {
                        busy = false
                        render()
                    }
                }
            }
        }
        confirm.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        confirm.show()
    }

    private fun setStatus(value: String) {
        if (::status.isInitialized) status.text = value
    }

    private fun panel() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = round(PANEL, 18, PANEL_LINE, 1)
    }

    private fun label(value: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, style)
        includeFontPadding = false
    }

    private fun round(fill: Int, radius: Int, stroke: Int? = null, strokeDp: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null && strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

    private fun matchWrap(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        private const val CAMERA_REQUEST = 4107
        private const val MAX_JPEG_BYTES = 4_500_000
        private val MUTED = Color.rgb(86, 110, 103)
        private val PANEL = Color.rgb(18, 26, 23)
        private val PANEL_SOFT = Color.rgb(28, 39, 35)
        private val PANEL_SELECTED = Color.rgb(30, 67, 47)
        private val PANEL_LINE = Color.rgb(51, 70, 63)
        private val PANEL_TEXT = Color.rgb(235, 241, 238)
        private val PANEL_MUTED = Color.rgb(166, 184, 176)
        private val GREEN = Color.rgb(86, 220, 91)
        private val COMMAND_BG = Color.rgb(11, 20, 17)
        private val COMMAND_TEXT = Color.rgb(207, 242, 194)
    }
}
