package com.blackcat.remote

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
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

/**
 * Mobile-first AI surface. It observes the already-registered HID manager but never owns its
 * lifecycle. Model output remains data. Only the explicit TYPE ONLY action reaches CommandTarget.
 */
private class AiPanel(
    private val activity: Activity,
    manager: HidManager,
    private val onClosed: () -> Unit
) {
    private enum class Tab { PLAN, STEP, FEEDBACK, PREVIEW }

    private val target = AiTransportScope(activity, manager) { dismiss() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val planner = OpenAiPlanner()
    private val vault = ApiKeyVault(activity.applicationContext)
    private val dialog = Dialog(activity)

    private var confirmation: AlertDialog? = null
    private var job: Job? = null
    private var busy = false
    private var requestEpoch = 0L
    private var currentTab = Tab.PLAN
    private var selected = -1
    private var plan: CommandPlan? = null
    private var goalText = ""
    private var targetText = "Ubuntu Linux / Bash"
    private var dirty = false
    private var transientKey = ""
    private var selectedModel = PlanCodec.DEFAULT_MODEL
    private var statusText = "Set your goal, then build a plan."

    private lateinit var page: LinearLayout
    private lateinit var statusBadge: TextView
    private lateinit var statusDetail: TextView
    private lateinit var stopButton: Button
    private lateinit var navPlan: Button
    private lateinit var navStep: Button
    private lateinit var navFeedback: Button
    private lateinit var navPreview: Button
    private var goalField: EditText? = null
    private var targetField: EditText? = null

    private val green = Color.rgb(24, 111, 68)
    private val greenDark = Color.rgb(13, 72, 43)
    private val greenSoft = Color.rgb(232, 244, 235)
    private val ink = Color.rgb(18, 34, 27)
    private val muted = Color.rgb(91, 111, 102)
    private val canvas = Color.rgb(246, 249, 246)
    private val card = Color.WHITE
    private val line = Color.rgb(205, 219, 211)
    private val warning = Color.rgb(152, 96, 19)

    fun show() {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(canvas)
            isSaveEnabled = false
        }
        root.addView(buildTopBar())
        root.addView(buildStatusStrip())
        page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(ScrollView(activity).apply {
            isFillViewport = true
            addView(page, LinearLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buildBottomNav())

        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            confirmation?.dismiss()
            planner.cancel(); job?.cancel(); scope.cancel()
            target.close()
            transientKey = ""
            goalField?.text?.clear(); targetField?.text?.clear()
            plan = null
            onClosed()
        }
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        target.start()
        try { dialog.show() } catch (e: Exception) { target.close(); throw e }
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        renderPage()
        scope.launch {
            while (isActive) {
                refreshStatus()
                delay(350)
            }
        }
    }

    fun dismiss() { if (dialog.isShowing) dialog.dismiss() }

    private fun buildTopBar(): View {
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        val brand = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("BLACK CAT", 18f, Typeface.BOLD, ink))
        brand.addView(label("Linux assistant", 11f, Typeface.NORMAL, muted))
        bar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(action("Settings", secondary = true) { showSettings() }, LinearLayout.LayoutParams(-2, dp(44)))
        return bar
    }

    private fun buildStatusStrip(): View {
        val row = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(10), dp(7))
            setBackgroundColor(Color.rgb(240, 246, 241))
        }
        statusBadge = label("READY", 11f, Typeface.BOLD, green).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = round(greenSoft, 999)
        }
        statusDetail = label("", 11f, Typeface.NORMAL, muted).apply { setPadding(dp(10), 0, dp(4), 0) }
        stopButton = action("STOP", secondary = true) { stopWork() }.apply { visibility = View.GONE }
        row.addView(statusBadge)
        row.addView(statusDetail, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(stopButton, LinearLayout.LayoutParams(-2, dp(38)))
        return row
    }

    private fun buildBottomNav(): View {
        val row = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(5))
            setBackgroundColor(Color.WHITE)
        }
        navPlan = navButton("Plan", Tab.PLAN)
        navStep = navButton("Step", Tab.STEP)
        navFeedback = navButton("Feedback", Tab.FEEDBACK)
        navPreview = navButton("Preview", Tab.PREVIEW)
        listOf(navPlan, navStep, navFeedback, navPreview).forEach {
            row.addView(it, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        return row
    }

    private fun navButton(text: String, tab: Tab) = Button(activity).apply {
        this.text = text
        textSize = 12f
        isAllCaps = false
        setPadding(dp(2), 0,²È="251°¥¹¬¤¹…ÁÁ±äìÍ•ÑA…‘‘¥¹œ À°‘À ÄÈ¤°€À°‘À Ğ¤¤ô¤(€€€€€€€Ù…°ÍÁ¥¹¹•È€ôMÁ¥¹¹•È¡…Ñ¥Ù¥Ñä¤¹…ÁÁ±äì(€€€€€€€€€€€…‘…ÁÑ•È€ôÉÉ…å‘…ÁÑ•È¡…Ñ¥Ù¥Ñä°…¹‘É½¥¹H¹±…å½ÕĞ¹Í¥µÁ±•}ÍÁ¥¹¹•É}‘É½Á‘½İ¹}¥Ñ•´°A±…¹½‘•Œ¹5=1L¤(€€€€€€€€€€€Í•ÑM•±•Ñ¥½¸¡A±…¹½‘•Œ¹5=1L¹¥¹‘•á=˜¡Í•±•Ñ•‘5½‘•°¤¹½•É•Ñ1•…ÍĞ À¤¤(€€€€€€€ô(€€€€€€€‰½‘ä¹…‘‘Y¥•Ü¡ÍÁ¥¹¹•È¤(€€€€€€€±…Ñ•¥¹¥ĞÙ…ÈÍ•ÑÑ¥¹Í¥…±½œè±•ÉÑ¥…±½œ(€€€€€€€Ù…°ÕÍ”€ô…Ñ¥½¸ ‰UÍ”­•äÑ¡¥ÌÍ•ÍÍ¥½¸ˆ¤ì(€€€€€€€€€€€Ù…°­•ä€ô­•å¥•±¹Ñ•áĞ¹Ñ½MÑÉ¥¹œ ¤¹ÑÉ¥´ ¤(€€€€€€€€€€€¥˜€¡­•ä¹¥Í9½ÑµÁÑä ¤€˜˜€…=Á•¹¥A±…¹¹•È¹Ù…±¥‘-•ä¡­•ä¤¤ì­•å¥•±¹•ÉÉ½È€ô€‰%¹Ù…±¥A$­•ä™½Éµ…ĞˆìÉ•ÑÕÉ¹…Ñ¥½¸ô(€€€€€€€€€€€¥˜€¡­•ä¹¥Í9½ÑµÁÑä ¤¤ÑÉ…¹Í¥•¹Ñ-•ä€ô­•ä(€€€€€€€€€€€Í•±•Ñ•‘5½‘•°€ôA±…¹½‘•Œ¹5=1MmÍÁ¥¹¹•È¹Í•±•Ñ•‘%Ñ•µA½Í¥Ñ¥½¸¹½•É•%¸¡A±…¹½‘•Œ¹5=1L¹¥¹‘¥•Ì¥t(€€€€€€€€€€€­•å¥•±¹Ñ•áĞ¹±•…È ¤ìÍ•ÑÑ¥¹Í¥…±½œ¹‘¥Íµ¥ÍÌ ¤ìÍÑ…ÑÕÍQ•áĞ€ô€‰M•ÑÑ¥¹ÌÕÁ‘…Ñ•¸ˆ(€€€€€€€€€€€É•™É•Í¡MÑ…ÑÕÌ ¤(€€€€€€€ô(€€€€€€€Ù…°Í…Ù”€ô…Ñ¥½¸ ‰M…Ù”•¹ÉåÁÑ•½¸Ñ¡¥ÌÁ¡½¹”ˆ°Í•½¹‘…Éä€ôÑÉÕ”¤ì(€€€€€€€€€€€Ù…°­•ä€ô­•å¥•±¹Ñ•áĞ¹Ñ½MÑÉ¥¹œ ¤¹ÑÉ¥´ ¤(€€€€€€€€€€€¥˜€ …=Á•¹¥A±…¹¹•È¹Ù…±¥‘-•ä¡­•ä¤¤ì­•å¥•±¹•ÉÉ½È€ô€‰¹Ñ•È„Ù…±¥A$­•ä™¥ÉÍĞˆìÉ•ÑÕÉ¹…Ñ¥½¸ô(€€€€€€€€€€€Í•±•Ñ•‘5½‘•°€ôA±…¹½‘•Œ¹5=1MmÍÁ¥¹¹•È¹Í•±•Ñ•‘%Ñ•µA½Í¥Ñ¥½¸¹½•É•%¸¡A±…¹½‘•Œ¹5=1L¹¥¹‘¥•Ì¥t(€€€€€€€€€€€Í½Á”¹±…Õ¹ ì(€€€€€€€€€€€€€€€ÑÉäìİ¥Ñ¡½¹Ñ•áĞ¡¥ÍÁ…Ñ¡•ÉÌ¹%<¤ìÙ…Õ±Ğ¹Í…Ù”¡­•ä¤ôìÑÉ…¹Í¥•¹Ñ-•ä€ô€ˆˆìÍÑ…ÑÕÍQ•áĞ€ô€‰-•äÍ…Ù••¹ÉåÁÑ•½¸Ñ¡¥ÌÁ¡½¹”¸ˆì­•å¥•±¹Ñ•áĞ¹±•…È ¤ìÍ•ÑÑ¥¹Í¥…±½œ¹‘¥Íµ¥ÍÌ ¤ô(€€€€€€€€€€€€€€€…Ñ €¡|èá•ÁÑ¥½¸¤ìÍÑ…ÑÕÍQ•áĞ€ô€‰-•ä½Õ±¹½Ğ‰”Í…Ù•Í•ÕÉ•±ä¸9¼Á±…¥¹Ñ•áĞ™…±±‰…¬İ…ÌÕÍ•¸ˆô(€€€€€€€€€€€€€€€É•™É•Í¡MÑ…ÑÕÌ ¤(€€€€€€€€€€€ô(€€€€€€€ô(€€€€€€€Ù…°™½É•Ğ€ô…Ñ¥½¸ ‰½É•ĞÍ…Ù•­•äˆ°Í•½¹‘…Éä€ôÑÉÕ”¤ì(€€€€€€€€€€€Í½Á”¹±…Õ¹ ì(€€€€€€€€€€€€€€€ÑÉäìİ¥Ñ¡½¹Ñ•áĞ¡¥ÍÁ…Ñ¡•ÉÌ¹%<¤ìÙ…Õ±Ğ¹™½É•Ğ ¤ôìÑÉ…¹Í¥•¹Ñ-•ä€ô€ˆˆìÍÑ…ÑÕÍQ•áĞ€ô€‰M…Ù•­•ä‘•±•Ñ•™É½´Ñ¡¥ÌÁ¡½¹”¸ˆìÍ•ÑÑ¥¹Í¥…±½œ¹‘¥Íµ¥ÍÌ ¤ô(€€€€€€€€€€€€€€€…Ñ €¡|èá•ÁÑ¥½¸¤ìÍÑ…ÑÕÍQ•áĞ€ô€‰½Õ±¹½Ğ™¥¹¥Í ‘•±•Ñ¥¹œÑ¡”Í…Ù•­•ä¸ˆô(€€€€€€€€€€€€€€€É•™É•Í¡MÑ…ÑÕÌ ¤(€€€€€€€€€€€ô(€€€€€€€ô(€€€€€€€‰½‘ä¹…‘‘Y¥•Ü¡ÕÍ”°1¥¹•…É1…å½ÕĞ¹1…å½ÕÑA…É…µÌ ´Ä°‘À Ğà¤¤¹…ÁÁ±äìÑ½Á5…É¥¸€ô‘À ÄÈ¤ì‰½ÑÑ½µ5…É¥¸€ô‘À Ü¤ô¤(€€€€€€€‰½‘ä¹…‘‘Y¥•Ü¡Í…Ù”°1¥¹•…É1…å½ÕĞ¹1…å½ÕÑA…É…µÌ ´Ä°‘À Ğà¤¤¹…ÁÁ±äì‰½ÑÑ½µ5…É¥¸€ô‘À Ü¤ô¤(€€€€€€€‰½‘ä¹…‘‘Y¥•Ü¡™½É•Ğ°1¥¹•…É1…å½ÕĞ¹1…å½ÕÑA…É…µÌ ´Ä°‘À Ğà¤¤¤(€€€€€€€Í•ÑÑ¥¹Í¥…±½œ€ô±•ÉÑ¥…±½œ¹	Õ¥±‘•È¡…Ñ¥Ù¥Ñä¤¹Í•ÑQ¥Ñ±” ‰M•ÑÑ¥¹Ìˆ¤¹Í•ÑY¥•Ü¡MÉ½±±Y¥•Ü¡…Ñ¥Ù¥Ñä¤¹…ÁÁ±äì…‘‘Y¥•Ü¡‰½‘ä¤ô¤(€€€€€€€€€€€€¹Í•Ñ9•…Ñ¥Ù•	ÕÑÑ½¸ ‰1=Mˆ°¹Õ±°¤¹É•…Ñ” ¤(€€€€€€€Í•ÑÑ¥¹Í¥…±½œ¹Í¡½Ü ¤(€€€€€€€Í•ÑÑ¥¹Í¥…±½œ¹İ¥¹‘½Üü¹…‘‘±…Ì¡]¥¹‘½İ5…¹…•È¹1…å½ÕÑA…É…µÌ¹1}MUI¤(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸ÍÑ½Á]½É¬ ¤ì(€€€€€€€É•ÅÕ•ÍÑÁ½ ¬¬(€€€€€€€Á±…¹¹•È¹…¹•° ¤ì©½ˆü¹…¹•° ¤ì½¹™¥Éµ…Ñ¥½¸ü¹‘¥Íµ¥ÍÌ ¤(€€€€€€€‰ÕÍä€ô™…±Í”(€€€€€€€ÍÑ…ÑÕÍQ•áĞ€ô€‰MÑ½ÁÁ•¸%¹ÍÁ•Ğ…¹äÁ…ÉÑ¥…°±…ÁÑ½ÀÑ•áĞµ…¹Õ…±±äì¹¼…ÕÑ½µ…Ñ¥ŒÉ•Á±…ä¥ÌÅÕ•Õ•¸ˆ(€€€€€€€É•™É•Í¡MÑ…ÑÕÌ ¤ìÉ•¹‘•ÉA…” ¤(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸İ½É­¥¹œ¡Ù…±Õ”è	½½±•…¸°µ•ÍÍ…”èMÑÉ¥¹œ¤ì(€€€€€€€‰ÕÍä€ôÙ…±Õ”(€€€€€€€ÍÑ…ÑÕÍQ•áĞ€ôµ•ÍÍ…”(€€€€€€€É•™É•Í¡MÑ…ÑÕÌ ¤(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸É•™É•Í¡MÑ…ÑÕÌ ¤ì(€€€€€€€¥˜€ „èéÍÑ…ÑÕÍ	…‘”¹¥Í%¹¥Ñ¥…±¥é•¤É•ÑÕÉ¸(€€€€€€€Ù…°¡½ÍĞ€ôÑ…É•Ğ¹ÕÉÉ•¹Ğ ¤(€€€€€€€Ù…°¡…Í-•ä€ôÑÉ…¹Í¥•¹Ñ-•ä¹¥Í9½ÑµÁÑä ¤ñğÙ…Õ±Ğ¹•á¥ÍÑÌ ¤(€€€€€€€ÍÑ…ÑÕÍ	…‘”¹Ñ•áĞ€ôİ¡•¸ì(€€€€€€€€€€€‰ÕÍä€´ø€‰]=I-%9ˆ(€€€€€€€€€€€€…¡…Í-•ä€´ø€‰A$-dˆ(€€€€€€€€€€€•±Í”€´ø€‰Idˆ(€€€€€€€ô(€€€€€€€ÍÑ…ÑÕÍ	…‘”¹Í•ÑQ•áÑ½±½È¡¥˜€ …¡…Í-•ä€˜˜€…‰ÕÍä¤İ…É¹¥¹œ•±Í”É••¸¤(€€€€€€€ÍÑ…ÑÕÍ•Ñ…¥°¹Ñ•áĞ€ô‰Õ¥±‘MÑÉ¥¹œì(€€€€€€€€€€€…ÁÁ•¹¡ÍÑ…ÑÕÍQ•áĞ¤(€€€€€€€€€€€¥˜€¡¡½ÍĞ€„ô¹Õ±°¤…ÁÁ•¹ ˆƒ
Ü!%½¹¹•Ñ•ˆ¤(€€€€€€€ô(€€€€€€€ÍÑ½Á	ÕÑÑ½¸¹Ù¥Í¥‰¥±¥Ñä€ô¥˜€¡‰ÕÍä¤Y¥•Ü¹Y%M%	1•±Í”Y¥•Ü¹=9(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸…ÁÑÕÉ•%¹ÁÕÑÌ ¤ì(€€€€€€€½…±¥•±ü¹±•Ğì½…±Q•áĞ€ô¥Ğ¹Ñ•áĞ¹Ñ½MÑÉ¥¹œ ¤¹ÑÉ¥´ ¤ô(€€€€€€€Ñ…É•Ñ¥•±ü¹±•ĞìÑ…É•ÑQ•áĞ€ô¥Ğ¹Ñ•áĞ¹Ñ½MÑÉ¥¹œ ¤¹ÑÉ¥´ ¤ô(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸¥¹ÁÕÑ]…Ñ¡•È¡…ÍÍ¥¸è€¡MÑÉ¥¹œ¤€´øU¹¥Ğ¤€ô½‰©•Ğ€èQ•áÑ]…Ñ¡•Èì(€€€€€€€½Ù•ÉÉ¥‘”™Õ¸‰•™½É•Q•áÑ¡…¹•¡Ìè¡…ÉM•ÅÕ•¹”ü°ÍÑ…ÉĞè%¹Ğ°½Õ¹Ğè%¹Ğ°…™Ñ•Èè%¹Ğ¤íô(€€€€€€€½Ù•ÉÉ¥‘”™Õ¸½¹Q•áÑ¡…¹•¡Ìè¡…ÉM•ÅÕ•¹”ü°ÍÑ…ÉĞè%¹Ğ°‰•™½É”è%¹Ğ°½Õ¹Ğè%¹Ğ¤ì(€€€€€€€€€€€Ù…°¹•áĞ€ôÌü¹Ñ½MÑÉ¥¹œ ¤¹½ÉµÁÑä ¤(€€€€€€€€€€€…ÍÍ¥¸¡¹•áĞ¤(€€€€€€€€€€€¥˜€¡Á±…¸€„ô¹Õ±°¤‘¥ÉÑä€ôÑÉÕ”(€€€€€€€ô(€€€€€€€½Ù•ÉÉ¥‘”™Õ¸…™Ñ•ÉQ•áÑ¡…¹•¡Ìè‘¥Ñ…‰±”ü¤íô(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸¥¹ÁÕĞ¡¡¥¹ÑQ•áĞèMÑÉ¥¹œ°±¥µ¥Ğè%¹Ğ°Á…ÍÍİ½Éè	½½±•…¸€ô™…±Í”¤€ô‘¥ÑQ•áĞ¡…Ñ¥Ù¥Ñä¤¹…ÁÁ±äì(€€€€€€€¡¥¹Ğ€ô¡¥¹ÑQ•áĞ(€€€€€€€Í•ÑQ•áÑ½±½È¡¥¹¬¤ìÍ•Ñ!¥¹ÑQ•áÑ½±½È¡½±½È¹Éˆ ÄÌÌ°€ÄÔÀ°€ÄĞÄ¤¤ìÑ•áÑM¥é”€ô€ÄÕ˜(€€€€€€€Í•ÑA…‘‘¥¹œ¡‘À ÄÈ¤°‘À ÄÀ¤°‘À ÄÈ¤°‘À ÄÀ¤¤(€€€€€€€‰…­É½Õ¹€ôÉ½Õ¹¡½±½È¹Éˆ ÈÔÄ°€ÈÔÌ°€ÈÔÄ¤°€ÄÈ°±¥¹”¤(€€€€€€€™¥±Ñ•ÉÌ€ô…ÉÉ…å=˜¡%¹ÁÕÑ¥±Ñ•È¹1•¹Ñ¡¥±Ñ•È¡±¥µ¥Ğ¤¤(€€€€€€€¥¹ÁÕÑQåÁ”€ô¥˜€¡Á…ÍÍİ½É¤%¹ÁÕÑQåÁ”¹QeA}1MM}QaP½È%¹ÁÕÑQåÁ”¹QeA}QaQ}YI%Q%=9}AMM]=I(€€€€€€€€€€€•±Í”%¹ÁÕÑQåÁ”¹QeA}1MM}QaP½È%¹ÁÕÑQåÁ”¹QeA}QaQ}1}5U1Q%}1%9½È%¹ÁÕÑQåÁ”¹QeA}QaQ}1}9=}MUMQ%=9L(€€€€€€€¥ÍM…Ù•¹…‰±•€ô™…±Í”(€€€€€€€¥µÁ½ÉÑ…¹Ñ½ÉÕÑ½™¥±°€ôY¥•Ü¹%5A=IQ9Q}=I}UQ=%11}9=}a1U}M99QL(€€€€€€€¥µ•=ÁÑ¥½¹Ì€ô‘¥Ñ½É%¹™¼¹%5}1}9=}AIM=91%i}1I9%9(€€€€€€€¥˜€¡Á…ÍÍİ½É¤Í•ÑM¥¹±•1¥¹”¡ÑÉÕ”¤(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸…Ñ¥½¸¡Ñ•áĞèMÑÉ¥¹œ°Í•½¹‘…Éäè	½½±•…¸€ô™…±Í”°…Ñ¥½¸è€ ¤€´øU¹¥Ğ¤€ô	ÕÑÑ½¸¡…Ñ¥Ù¥Ñä¤¹…ÁÁ±äì(€€€€€€€Ñ¡¥Ì¹Ñ•áĞ€ôÑ•áĞ(€€€€€€€¥Í±±…ÁÌ€ô™…±Í”(€€€€€€€Ñ•áÑM¥é”€ô€ÄÑ˜(€€€€€€€Í•ÑQåÁ•™…”¡ÑåÁ•™…”°QåÁ•™…”¹	=1¤(€€€€€€€Í•ÑQ•áÑ½±½È¡¥˜€¡Í•½¹‘…Éä¤É••¹…É¬•±Í”½±½È¹]!%Q¤(€€€€€€€‰…­É½Õ¹€ôÉ½Õ¹¡¥˜€¡Í•½¹‘…Éä¤½±½È¹]!%Q•±Í”É••¸°€ÄĞ°¥˜€¡Í•½¹‘…Éä¤±¥¹”•±Í”É••¸¤(€€€€€€€Í•Ñ=¹±¥­1¥ÍÑ•¹•Èì…Ñ¥½¸ ¤ô(€€€€€€€µ¥¹!•¥¡Ğ€ô‘À ĞĞ¤(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸±…‰•°¡Ù…±Õ”èMÑÉ¥¹œ°Í¥é”è±½…Ğ°ÍÑå±”è%¹Ğ°½±½Èè%¹Ğ¤€ôQ•áÑY¥•Ü¡…Ñ¥Ù¥Ñä¤¹…ÁÁ±äì(€€€€€€€Ñ•áĞ€ôÙ…±Õ”ìÑ•áÑM¥é”€ôÍ¥é”ìÍ•ÑQ•áÑ½±½È¡½±½È¤ìÍ•ÑQåÁ•™…”¡ÑåÁ•™…”°ÍÑå±”¤(€€€€€€€Í•Ñ1¥¹•MÁ…¥¹œ Á˜°€Ä¸Àá˜¤(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸½‘”¡Ù…±Õ”èMÑÉ¥¹œ¤€ôQ•áÑY¥•Ü¡…Ñ¥Ù¥Ñä¤¹…ÁÁ±äì(€€€€€€€Ñ•áĞ€ôÙ…±Õ”ìÑ•áÑM¥é”€ô€ÄÑ˜ìÍ•ÑQ•áÑ½±½È¡½±½È¹Éˆ ÈÈä°€ÈĞÜ°€ÈÌĞ¤¤ìÑåÁ•™…”€ôQåÁ•™…”¹5=9=MA(€€€€€€€Í•ÑA…‘‘¥¹œ¡‘À ÄÈ¤°‘À ÄÈ¤°‘À ÄÈ¤°‘À ÄÈ¤¤ì‰…­É½Õ¹€ôÉ½Õ¹¡½±½È¹Éˆ ÄÌ°€ÌÀ°€ÈÌ¤°€ÄÈ¤(€€€€€€€Í•ÑQ•áÑ%ÍM•±•Ñ…‰±”¡ÑÉÕ”¤(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸Í•Ñ¥½¹Q¥Ñ±”¡Ù…±Õ”èMÑÉ¥¹œ¤€ô±…‰•°¡Ù…±Õ”°€ÄÙ˜°QåÁ•™…”¹	=1°¥¹¬¤¹…ÁÁ±äìÍ•ÑA…‘‘¥¹œ¡‘À È¤°‘À ÄĞ¤°‘À È¤°‘À à¤¤ô((€€€ÁÉ¥Ù…Ñ”™Õ¸¥¹™½…É¡Ù…±Õ”èMÑÉ¥¹œ°İ…É¹¥¹œè	½½±•…¸€ô™…±Í”¤€ô±…‰•°¡Ù…±Õ”°€ÄÑ˜°QåÁ•™…”¹9=I50°¥˜€¡İ…É¹¥¹œ¤½±½È¹Éˆ ÄÀà°€Øà°€ÄÈ¤•±Í”¥¹¬¤¹…ÁÁ±äì(€€€€€€€Í•ÑA…‘‘¥¹œ¡‘À ÄÌ¤°‘À ÄÈ¤°‘À ÄÌ¤°‘À ÄÈ¤¤(€€€€€€€‰…­É½Õ¹€ôÉ½Õ¹¡¥˜€¡İ…É¹¥¹œ¤½±½È¹Éˆ ÈÔÔ°€ÈĞà°€ÈÌÈ¤•±Í”…É°€ÄØ°¥˜€¡İ…É¹¥¹œ¤½±½È¹Éˆ ÈÈä°€Äää°€ÄÌÜ¤•±Í”±¥¹”¤(€€€€€€€±…å½ÕÑA…É…µÌ€ô1¥¹•…É1…å½ÕĞ¹1…å½ÕÑA…É…µÌ ´Ä°€´È¤¹…ÁÁ±äì‰½ÑÑ½µ5…É¥¸€ô‘À à¤ô(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸ÍÑå±•9…Ù¥…Ñ¥½¸ ¤ì(€€€€€€€¥˜€ „èé¹…ÙA±…¸¹¥Í%¹¥Ñ¥…±¥é•¤É•ÑÕÉ¸(€€€€€€€±¥ÍÑ=˜¡Q…ˆ¹A18Ñ¼¹…ÙA±…¸°Q…ˆ¹MQ@Ñ¼¹…ÙMÑ•À°Q…ˆ¹	,Ñ¼¹…Ù••‘‰…¬°Q…ˆ¹AIY%\Ñ¼¹…ÙAÉ•Ù¥•Ü¤¹™½É… ì€¡Ñ…ˆ°‰ÕÑÑ½¸¤€´ø(€€€€€€€€€€€Ù…°…Ñ¥Ù”€ôÑ…ˆ€ôôÕÉÉ•¹ÑQ…ˆ(€€€€€€€€€€€‰ÕÑÑ½¸¹Í•ÑQ•áÑ½±½È¡¥˜€¡…Ñ¥Ù”¤É••¹…É¬•±Í”µÕÑ•¤(€€€€€€€€€€€‰ÕÑÑ½¸¹‰…­É½Õ¹€ôÉ½Õ¹¡¥˜€¡…Ñ¥Ù”¤É••¹M½™Ğ•±Í”½±½È¹]!%Q°€ÄÌ¤(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸É½Õ¹¡½±½Èè%¹Ğ°É…‘¥ÕÍÀè%¹Ğ°ÍÑÉ½­”è%¹Ğü€ô¹Õ±°¤€ôÉ…‘¥•¹ÑÉ…İ…‰±” ¤¹…ÁÁ±äì(€€€€€€€Í•Ñ½±½È¡½±½È¤ì½É¹•ÉI…‘¥ÕÌ€ô‘À¡É…‘¥ÕÍÀ¤¹Ñ½±½…Ğ ¤(€€€€€€€¥˜€¡ÍÑÉ½­”€„ô¹Õ±°¤Í•ÑMÑÉ½­”¡‘À Ä¤°ÍÑÉ½­”¤(€€€ô((€€€ÁÉ¥Ù…Ñ”™Õ¸‘À¡Ù…±Õ”è%¹Ğ¤€ô€¡Ù…±Õ”€¨…Ñ¥Ù¥Ñä¹É•Í½ÕÉ•Ì¹‘¥ÍÁ±…å5•ÑÉ¥Ì¹‘•¹Í¥Ñä¤¹Ñ½%¹Ğ ¤)ô(