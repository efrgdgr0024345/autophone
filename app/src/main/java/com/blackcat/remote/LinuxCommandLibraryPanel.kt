package com.blackcat.remote

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phone-first, completely offline Linux command browser.
 *
 * The catalogue never calls a network API. Selecting a command sends nothing. The user must open
 * the detail screen, preview the exact command and destination, and approve TYPE ONLY. Enter is
 * deliberately excluded by CommandPolicy and ApprovedCommandSender.
 */
class LinuxCommandLibraryPanel(
    private val activity: Activity,
    private val manager: HidManager,
    private val onClosed: () -> Unit = {},
    private val onSelectForPlan: ((LinuxCommandItem) -> Unit)? = null
) {
    private enum class Level { CATEGORIES, TOPICS, COMMANDS, SEARCH, DETAIL, TOOLS, TOOL, CLI, LESSONS, LESSON }

    private val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val target = LibraryHidTarget(
        activity = activity,
        manager = manager,
        onConnectionChanged = {
            refreshConnectionState()
            render()
        },
        onLeave = { dismiss() }
    )

    private var items = emptyList<LinuxCommandItem>()
    private var level = Level.CATEGORIES
    private var detailReturnLevel = Level.COMMANDS
    private var selectedCategory = ""
    private var selectedTopic = ""
    private var selectedItem: LinuxCommandItem? = null
    private var busy = false
    private var confirmation: AlertDialog? = null
    private lateinit var learning: OfflineLearningData
    private var selectedTool: OfflineTool? = null
    private var selectedCli: OfflineCli? = null
    private var selectedLesson: OfflineLesson? = null
    private var lessonStep = 0
    private var toolPage = 0
    private var entryPage = 0
    private var allFlags = false
    private val guideCommands = mutableMapOf<String, LinuxCommandItem>()
    private lateinit var countBadge: TextView
    private lateinit var headerHero: View
    private lateinit var contentScroll: ScrollView
    private var lastRenderedLevel: Level? = null

    private lateinit var search: EditText
    private lateinit var breadcrumb: TextView
    private lateinit var connectionPill: TextView
    private lateinit var connectionText: TextView
    private lateinit var contentBody: LinearLayout

    fun show() {
        dialog.setContentView(buildRoot())
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                navigateBack()
                true
            } else {
                false
            }
        }
        dialog.setOnDismissListener {
            confirmation?.dismiss()
            target.close()
            scope.cancel()
            onClosed()
        }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window?.statusBarColor = Color.WHITE
        dialog.window?.navigationBarColor = DARK

        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        target.start()
        render()
        refreshConnectionState()
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val loaded = OfflineLearningCatalog.load(activity.applicationContext)
                    LinuxCommandCatalog.load(activity.applicationContext)
                    loaded
                }
                if (!dialog.isShowing) return@launch
                learning = data
                items = data.commands
                countBadge.text = "${items.size} examples · ${data.tools.size} tool groups · no API · no network"
                render()
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                Toast.makeText(activity, "The offline learning catalogue could not be loaded.", Toast.LENGTH_LONG).show()
                dismiss()
            }
        }
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    private fun buildRoot(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        BlackCatStyle.applySystemBarInsets(root)
        root.addView(buildHeader(), matchWrap())
        root.addView(buildSearchPanel(), matchWrap())
        root.addView(buildStatusBar(), matchWrap())

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(Color.WHITE)
        }
        contentScroll = scroll
        contentBody = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(24))
            setBackgroundColor(Color.WHITE)
        }
        scroll.addView(contentBody, matchWrap())
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        (activity as? MainActivity)?.let { main ->
            root.addView(main.createBottomNavigation("AI") { dismiss() })
        }
        return root
    }

    private fun buildHeader(): View {
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(3), dp(6), 0)
        }
        val back = TextView(activity).apply {
            text = "‹"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 34f)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            setOnClickListener { navigateBack() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(
            text("Offline Linux Library", 18f, Color.BLACK, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val close = TextView(activity).apply {
            text = "×"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 29f)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Close"
            setOnClickListener { dismiss() }
        }
        top.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(top)

        val hero = FrameLayout(activity).apply {
            setBackgroundColor(Color.WHITE)
            clipChildren = false
            clipToPadding = false
        }
        headerHero = hero
        hero.addView(
            ImageView(activity).apply {
                setImageResource(R.drawable.black_cat_peek)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "Black Cat"
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(112)
            )
        )
        val offline = text(
            "Loading bundled reference · no API · no network",
            12f,
            Color.DKGRAY,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = round(0xffeaf6e8.toInt(), 22)
        }
        countBadge = offline
        hero.addView(
            offline,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(4) }
        )
        header.addView(hero, LinearLayout.LayoutParams(-1, dp(122)))
        breadcrumb = text("All categories", 12f, Color.BLACK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = round(0xffedf5ec.toInt(), 0)
            maxLines = 2
        }
        header.addView(breadcrumb, LinearLayout.LayoutParams(-1, -2))
        return header
    }

    private fun buildSearchPanel(): View {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(9))
            background = round(DARK, 0)
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        search = EditText(activity).apply {
            hint = "Search Wi-Fi, disk space, ports, memory…"
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(100))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            setTextColor(Color.WHITE)
            setHintTextColor(0xffaebdb7.toInt())
            textSize = 15f
            background = round(0xff1c2723.toInt(), 13, BORDER, 1)
            setPadding(dp(13), dp(9), dp(13), dp(9))
        }
        row.addView(search, LinearLayout.LayoutParams(0, dp(50), 1f))
        val clear = Button(activity).apply {
            text = "×"
            isAllCaps = false
            textSize = 22f
            setTextColor(Color.WHITE)
            background = round(0xff1c2723.toInt(), 13, BORDER, 1)
            minWidth = 0
            setPadding(0, 0, 0, 0)
            contentDescription = "Clear search"
            setOnClickListener {
                search.text.clear()
                search.requestFocus()
            }
        }
        row.addView(clear, LinearLayout.LayoutParams(dp(50), dp(50)).apply {
            leftMargin = dp(7)
        })
        panel.addView(row)

        val quick = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
        }
        val chips = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, 0)
        }
        listOf("Kali", "Network", "Disk space", "Memory", "Logs", "Permissions").forEach { value ->
            val chip = Button(activity).apply {
                text = value
                isAllCaps = false
                textSize = 11f
                setTextColor(GREEN)
                background = round(0xff1a3026.toInt(), 24, 0xff38664c.toInt(), 1)
                minHeight = dp(48)
                minWidth = 0
                setPadding(dp(11), 0, dp(11), 0)
                setOnClickListener {
                    search.setText(value)
                    search.setSelection(search.text.length)
                }
            }
            chips.addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48)
                ).apply { rightMargin = dp(6) }
            )
        }
        quick.addView(chips, matchWrap())
        panel.addView(quick, matchWrap())

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                value: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                value: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                selectedItem = null
                selectedLesson = null
                selectedTool = null
                level = if (value.isNullOrBlank()) Level.CATEGORIES else Level.SEARCH
                render()
            }

            override fun afterTextChanged(value: Editable?) {}
        })
        return panel
    }

    private fun buildStatusBar(): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = round(0xff111916.toInt(), 0, BORDER, 1)
        }
        connectionPill = text("●  CHECKING", 11f, GREEN, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = round(0xff1d432e.toInt(), 24)
        }
        bar.addView(connectionPill)
        connectionText = text(
            "Browse freely. Nothing is typed until final approval.",
            11f,
            0xffd1ddd7.toInt()
        ).apply {
            setPadding(dp(10), 0, 0, 0)
            maxLines = 2
        }
        bar.addView(
            connectionText,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        return bar
    }

    private fun render() {
        if (!::contentBody.isInitialized) return
        contentBody.removeAllViews()
        if (lastRenderedLevel != level) scrollTop()
        lastRenderedLevel = level
        updateBreadcrumb()
        if (!::learning.isInitialized) {
            contentBody.addView(pageIntro("Loading offline reference", "Reading bundled data. No network request or typing occurs."), matchWrap())
            return
        }
        headerHero.visibility = if (level == Level.CATEGORIES) View.VISIBLE else View.GONE

        when {
            level == Level.TOOLS -> renderTools()
            level == Level.TOOL -> renderTool()
            level == Level.CLI -> renderCli()
            level == Level.LESSONS -> renderLessons()
            level == Level.LESSON -> renderLesson()
            level == Level.DETAIL -> renderDetail()
            search.text?.isNotBlank() == true || level == Level.SEARCH -> renderSearch()
            level == Level.TOPICS -> renderTopics()
            level == Level.COMMANDS -> renderCommands()
            else -> renderCategories()
        }
    }

    private fun renderCategories() {
        level = Level.CATEGORIES
        val intro = darkCard()
        intro.addView(text("Browse by task", 20f, Color.WHITE, Typeface.BOLD))
        intro.addView(
            text(
                "Open a category, then a topic, then review one exact command. This is a reference library, not an automatic agent.",
                13f,
                MUTED
            ).apply { setPadding(0, dp(6), 0, 0) }
        )
        contentBody.addView(intro, matchWrap(bottom = 8))

        contentBody.addView(navigationRow("Kali Linux", "System readiness, local inspection and tool discovery", "Kali") {
            selectedCategory = "Kali Linux"; selectedTopic = ""; selectedLesson = null; level = Level.TOPICS; render()
        }, matchWrap(bottom = 7))
        contentBody.addView(navigationRow("Guided lessons", "Checks, explanations, verification and cleanup", learning.lessons.size.toString()) {
            selectedLesson = null; guideCommands.clear(); level = Level.LESSONS; render()
        }, matchWrap(bottom = 7))
        contentBody.addView(navigationRow("Tool and app directory", "Documented programs, subcommands and options", learning.tools.size.toString()) {
            kaliToolsOnly = false; toolPage = 0; level = Level.TOOLS; render()
        }, matchWrap(bottom = 7))

        LinuxCommandCatalog.categories(items).filter { it.name != "Kali Linux" }.forEach { group ->
            val topics = LinuxCommandCatalog.topics(items, group.name).size
            contentBody.addView(
                navigationRow(
                    title = group.name,
                    subtitle = "$topics topics · ${group.count} commands",
                    badge = group.count.toString()
                ) {
                    selectedCategory = group.name
                    selectedTopic = ""
                    level = Level.TOPICS
                    render()
                },
                matchWrap(bottom = 7)
            )
        }
    }

    private fun renderTopics() {
        val groups = LinuxCommandCatalog.topics(items, selectedCategory)
        if (groups.isEmpty()) {
            level = Level.CATEGORIES
            render()
            return
        }
        contentBody.addView(
            pageIntro(
                selectedCategory,
                "Choose a topic to open its command list."
            ),
            matchWrap(bottom = 8)
        )
        if (selectedCategory == "Kali Linux") contentBody.addView(navigationRow("Kali Linux tool directory", "Browse Kali's documented tool collections", "Tools") {
            kaliToolsOnly = true; toolPage = 0; level = Level.TOOLS; render()
        }, matchWrap(bottom = 8))
        groups.forEach { group ->
            contentBody.addView(
                navigationRow(
                    title = group.name,
                    subtitle = "${group.count} reviewable commands",
                    badge = group.count.toString()
                ) {
                    selectedTopic = group.name
                    level = Level.COMMANDS
                    render()
                },
                matchWrap(bottom = 7)
            )
        }
    }

    private fun renderCommands() {
        val commands = LinuxCommandCatalog.commands(items, selectedCategory, selectedTopic)
        if (commands.isEmpty()) {
            level = Level.TOPICS
            render()
            return
        }
        contentBody.addView(
            pageIntro(
                selectedTopic,
                "${commands.size} commands in $selectedCategory. Tap one to read what it does."
            ),
            matchWrap(bottom = 8)
        )
        commands.forEach { item ->
            contentBody.addView(commandRow(item) {
                detailReturnLevel = Level.COMMANDS
                selectedItem = item
                selectedLesson = null
                level = Level.DETAIL
                render()
            }, matchWrap(bottom = 7))
        }
    }

    private fun renderSearch() {
        val query = search.text?.toString().orEmpty()
        val results = LinuxCommandCatalog.search(items, query)
        val intro = pageIntro(
            if (results.isEmpty()) "No matches" else "${results.size} search results",
            if (results.isEmpty()) {
                "Try a task such as “network status”, “disk space”, “CPU temperature”, “ports”, or “failed services”."
            } else {
                "Results include titles, command text, explanations, topics, and helpful synonyms."
            }
        )
        contentBody.addView(intro, matchWrap(bottom = 8))
        results.forEach { item ->
            contentBody.addView(commandRow(item) {
                detailReturnLevel = Level.SEARCH
                selectedItem = item
                selectedLesson = null
                level = Level.DETAIL
                render()
            }, matchWrap(bottom = 7))
        }
        val clean = query.trim().lowercase(java.util.Locale.ROOT)
        if (clean.length >= 2) {
            val matches = learning.entries.filter { it.invocation.lowercase(java.util.Locale.ROOT).contains(clean) }.take(30)
            if (matches.isNotEmpty()) contentBody.addView(pageIntro("Documented command references", "Up to 30 matches. References do not approve or run a command."), matchWrap(top = 8, bottom = 8))
            matches.forEach { entry -> contentBody.addView(navigationRow(entry.invocation, entry.kind.replace('_', ' '), "Info") {
                selectedCli = entry; selectedTool = null; allFlags = false; level = Level.CLI; render()
            }, matchWrap(bottom = 7)) }
            val tools = learning.tools.filter { it.name.lowercase(java.util.Locale.ROOT).contains(clean) }.take(20)
            tools.forEach { tool -> contentBody.addView(navigationRow(tool.name, "Tool/package collection", "Tool") {
                selectedTool = tool; entryPage = 0; level = Level.TOOL; render()
            }, matchWrap(bottom = 7)) }
        }
    }

    private fun lessonButton(title: String, action: () -> Unit): Button = Button(activity).apply {
        text = title
        BlackCatStyle.styleButton(activity, this, false)
        isEnabled = !busy
        setOnClickListener { if (!busy) action() }
    }

    private fun scrollTop() {
        if (::contentScroll.isInitialized) contentScroll.post { if (dialog.isShowing) contentScroll.scrollTo(0, 0) }
    }

    private fun renderLessons() {
        contentBody.addView(pageIntro("Guided lessons", "Choose a goal. Each step explains why it follows, what to inspect, and when to stop."), matchWrap(bottom = 8))
        learning.lessons.forEach { lesson ->
            contentBody.addView(navigationRow(lesson.title, "${lesson.category} · ${lesson.steps.size} explained steps", lesson.steps.size.toString()) {
                selectedLesson = lesson; guideCommands.clear(); level = Level.LESSON; render()
            }, matchWrap(bottom = 7))
        }
    }

    private fun renderLesson() {
        val lesson = selectedLesson ?: run { level = Level.LESSONS; render(); return }
        contentBody.addView(pageIntro(lesson.title, lesson.goal), matchWrap(bottom = 8))
        if (lesson.prerequisites.isNotEmpty()) {
            val card = darkCard()
            infoBlock(card, "Complete these lessons first", lesson.prerequisites.mapNotNull { learning.lessonById[it]?.title }.joinToString("\n"))
            contentBody.addView(card, matchWrap(bottom = 8))
        }
        lesson.steps.forEachIndexed { index, step ->
            val command = learning.commandById[step.commandId] ?: guideCommands[step.commandId]
            if (command != null) contentBody.addView(navigationRow("${index + 1}. ${command.title}", command.command, "Read") {
                openLessonStep(index)
            }, matchWrap(bottom = 7))
        }
        contentBody.addView(pageIntro("Review actual results", "Next opens an explanation; it never sends text or confirms success. Pause on unexpected output. Each command keeps its separate TYPE ONLY approval."), matchWrap(top = 8))
    }

    private fun openLessonStep(index: Int) {
        val lesson = selectedLesson ?: return
        val step = lesson.steps.getOrNull(index) ?: return
        val command = learning.commandById[step.commandId] ?: guideCommands[step.commandId] ?: return
        lessonStep = index
        selectedItem = command
        detailReturnLevel = Level.LESSON
        level = Level.DETAIL
        render()
        scrollTop()
    }

    private var kaliToolsOnly = false
    private fun renderTools() {
        val tools = learning.tools.filter { !kaliToolsOnly || "kali" in it.distributions }
        toolPage = toolPage.coerceIn(0, ((tools.size - 1).coerceAtLeast(0)) / 40)
        contentBody.addView(pageIntro(if (kaliToolsOnly) "Kali Linux tool directory" else "Tool and app directory",
            "${tools.size} tool/package groups. References include documented binaries and subcommands; versions describe the source snapshot."), matchWrap(bottom = 8))
        tools.drop(toolPage * 40).take(40).forEach { tool ->
            contentBody.addView(navigationRow(tool.name, tool.summary.ifBlank { "Documented tool/package group" }, tool.entries.size.toString()) {
                selectedTool = tool; entryPage = 0; level = Level.TOOL; render()
            }, matchWrap(bottom = 7))
        }
        pager(toolPage, tools.size) { toolPage = it; render() }
    }

    private fun pager(page: Int, total: Int, select: (Int) -> Unit) {
        val pages = ((total + 39) / 40).coerceAtLeast(1)
        if (pages <= 1) return
        contentBody.addView(text("Page ${page + 1} of $pages · $total references", 13f, Color.BLACK), matchWrap(top = 8, bottom = 8))
        if (page > 0) contentBody.addView(lessonButton("Previous page") { select(page - 1); scrollTop() }, matchWrap(bottom = 7))
        if (page + 1 < pages) contentBody.addView(lessonButton("Next page") { select(page + 1); scrollTop() }, matchWrap(bottom = 7))
    }

    private fun renderTool() {
        val tool = selectedTool ?: run { level = Level.TOOLS; render(); return }
        val entries = tool.entries.mapNotNull { learning.entryById[it] }.sortedBy { it.invocation }
        val card = darkCard()
        card.addView(text(tool.name, 22f, Color.WHITE, Typeface.BOLD))
        infoBlock(card, "About this collection", tool.summary.ifBlank { "This source did not supply a collection summary." })
        infoBlock(card, "Source version", tool.version)
        infoBlock(card, "Compatibility", tool.distributions.joinToString(", ") + ". Check the installed version and package availability on your computer.")
        infoBlock(card, "Sources", tool.sources.mapNotNull { learning.sources[it] }.joinToString("\n\n"))
        contentBody.addView(card, matchWrap(bottom = 8))
        val entry = entries.firstOrNull { it.kind == "binary" && it.name == tool.name }
            ?: entries.firstOrNull { it.kind == "binary" && literalName(it.name) }
        if (entry != null && literalName(entry.name)) contentBody.addView(lessonButton("Learn this tool · check, read, choose a task") {
            openToolGuide(entry)
        }, matchWrap(bottom = 8))
        contentBody.addView(lessonButton("Choose a task lesson") { selectedLesson = null; guideCommands.clear(); level = Level.LESSONS; render() }, matchWrap(bottom = 8))
        entryPage = entryPage.coerceIn(0, ((entries.size - 1).coerceAtLeast(0)) / 40)
        entries.drop(entryPage * 40).take(40).forEach { item ->
            contentBody.addView(navigationRow(item.invocation, item.kind.replace('_', ' '), "Info") {
                selectedCli = item; allFlags = false; level = Level.CLI; render()
            }, matchWrap(bottom = 7))
        }
        pager(entryPage, entries.size) { entryPage = it; render() }
    }

    private fun renderCli() {
        val item = selectedCli ?: run { level = Level.TOOLS; render(); return }
        val card = darkCard()
        card.addView(text(item.invocation, 22f, Color.WHITE, Typeface.BOLD))
        infoBlock(card, "Reference type", item.kind.replace('_', ' '))
        infoBlock(card, "Synopsis", item.summary.ifBlank { "No individual synopsis was supplied. Read the owning collection and documented options before choosing a task." })
        infoBlock(card, "Documented options", (if (allFlags) item.flags else item.flags.take(80)).joinToString(", ").ifBlank { "No long-option names extracted from this source." })
        if (!allFlags && item.flags.size > 80) card.addView(lessonButton("Show all ${item.flags.size} documented options") { allFlags = true; render() }, matchWrap(top = 8))
        infoBlock(card, "Help examples observed in the source", item.help.joinToString("\n").ifBlank { "No simple help invocation was extracted; no flag was invented." })
        infoBlock(card, "Use in this app", "This is reference information. Opening it creates no typing approval and does not widen Auto's supported commands.")
        contentBody.addView(card, matchWrap(bottom = 8))
        if (item.kind == "binary" && literalName(item.name)) contentBody.addView(lessonButton("Learn this command · availability and local help") { openToolGuide(item) }, matchWrap(bottom = 8))
        item.toolIds.mapNotNull { learning.toolById[it] }.forEach { tool ->
            contentBody.addView(navigationRow(tool.name, "Open the owning collection and its source references", "Tool") {
                selectedTool = tool; entryPage = 0; level = Level.TOOL; render()
            }, matchWrap(bottom = 7))
        }
    }

    private fun literalName(name: String) = Regex("[A-Za-z0-9_][A-Za-z0-9+_.:-]{0,119}").matches(name)

    private fun openToolGuide(entry: OfflineCli) {
        if (!literalName(entry.name)) return
        guideCommands.clear()
        val lines = mutableListOf("command -v ${entry.name}", "man -f ${entry.name}")
        val safeShape = Regex(Regex.escape(entry.name) + " (?:--help|-h|--version|-V)")
        entry.help.firstOrNull { safeShape.matches(it) && CommandPolicy.problem(it) == null }?.let { lines.add(it) }
        val steps = lines.mapIndexed { index, command ->
            val id = "guide-${entry.id}-$index"
            val explanation = when (index) {
                0 -> "Check whether this exact executable is available before trying a task."
                1 -> "Read a local manual summary. Missing documentation does not prove the executable is absent."
                else -> "This help invocation appears in the retrieved documentation. Review the installed tool's behavior; initialization or plugins may run before help appears."
            }
            guideCommands[id] = LinuxCommandItem(id, "Tool discovery", entry.name, when (index) {
                0 -> "Locate ${entry.name}"; 1 -> "Read local documentation"; else -> "Review documented help"
            }, command, explanation, if (index == 0) "A resolved executable path, or a missing-command result." else "Local documentation or an informative availability/version error.",
                if (index < 2) LinuxCommandRisk.LOW else LinuxCommandRisk.MEDIUM, false,
                "Linux; package, manual and help availability depend on the installed release.", listOf(entry.name))
            OfflineStep(id + "-step", id, explanation, guideCommands[id]!!.expectedResult,
                "Pause on unexpected output. Check package availability and privileges instead of blindly retrying or launching an operational task.")
        }
        selectedLesson = OfflineLesson("tool-guide-${entry.id}", "Learn ${entry.name}", "Tool discovery",
            "Locate the executable, inspect its local documentation, then choose a task-specific lesson. Nothing runs by opening this guide.", emptyList(), steps, emptyList())
        level = Level.LESSON
        render()
    }

    private fun renderDetail() {
        val item = selectedItem ?: run {
            level = detailReturnLevel
            render()
            return
        }

        val card = darkCard()
        val riskLabel = riskLabel(item)
        card.addView(riskLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        card.addView(
            text(item.title, 22f, Color.WHITE, Typeface.BOLD).apply {
                setPadding(0, dp(10), 0, 0)
            }
        )
        card.addView(
            text("${item.category}  ›  ${item.topic}", 12f, GREEN, Typeface.BOLD).apply {
                setPadding(0, dp(5), 0, dp(10))
            }
        )
        card.addView(text(item.explanation, 14f, 0xffd6e1dc.toInt()))
        selectedLesson?.let { lesson ->
            val step = lesson.steps.getOrNull(lessonStep)
            infoBlock(card, "Lesson · ${lessonStep + 1} of ${lesson.steps.size}", lesson.title)
            if (step != null) {
                infoBlock(card, "Before continuing", step.lookFor)
                infoBlock(card, "If the result differs", step.onError)
            }
        }
        card.addView(commandBox(item.command), matchWrap(top = 12, bottom = 10))
        infoBlock(card, "Expected result", item.expectedResult)
        infoBlock(
            card,
            "Access",
            if (item.requiresAdmin) {
                "Administrator access may be requested. Read the command before approving it."
            } else {
                "No administrator requirement is marked for this command."
            }
        )
        infoBlock(card, "Compatibility", item.compatibility)
        learning.notes[item.id]?.let { note ->
            infoBlock(card, "Prerequisites", note.prerequisites.joinToString("\n"))
            infoBlock(card, "Computer network effect", note.network.replace('_', ' '))
            infoBlock(card, "Reference status", "A reference example, not proof this command is installed or executed successfully.")
            val sources = note.sourceIds.mapNotNull { learning.sources[it] }
            if (sources.isNotEmpty()) infoBlock(card, "Sources", sources.joinToString("\n\n"))
        }
        if (item.tags.isNotEmpty()) {
            infoBlock(card, "Search terms", item.tags.joinToString(", "))
        }
        contentBody.addView(card, matchWrap(bottom = 9))
        selectedLesson?.let { lesson ->
            if (lessonStep > 0) contentBody.addView(lessonButton("Previous explanation") { openLessonStep(lessonStep - 1) }, matchWrap(bottom = 8))
            if (lessonStep + 1 < lesson.steps.size) contentBody.addView(lessonButton("Next explanation · sends nothing") { openLessonStep(lessonStep + 1) }, matchWrap(bottom = 8))
            else lesson.next.mapNotNull { learning.lessonById[it] }.forEach { next ->
                contentBody.addView(lessonButton("Continue learning · ${next.title}") { selectedLesson = next; level = Level.LESSON; render() }, matchWrap(bottom = 8))
            }
            contentBody.addView(lessonButton("Lesson overview") { level = Level.LESSON; render() }, matchWrap(bottom = 8))
        }

        onSelectForPlan?.let { select ->
            val add = Button(activity).apply {
                text = "Add to current plan"
                BlackCatStyle.styleButton(activity, this, true)
                setOnClickListener {
                    // Selection changes plan data only. It never creates a typing approval.
                    select(item)
                    dismiss()
                }
            }
            contentBody.addView(add, matchWrap(bottom = 8))
        }

        val copy = Button(activity).apply {
            text = "Copy command"
            BlackCatStyle.styleButton(activity, this, false)
            minHeight = dp(50)
            setOnClickListener { copyCommand(item.command) }
        }
        contentBody.addView(copy, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        val host = target.current()
        contentBody.addView(text("Destination: ${host?.label ?: "No Bluetooth host"}", 14f, Color.BLACK, Typeface.BOLD), matchWrap(bottom = 7))
        val type = Button(activity).apply {
            text = when {
                busy -> "Typing…"
                host == null -> "Connect Bluetooth first"
                else -> "Approve & type once"
            }
            BlackCatStyle.styleButton(activity, this, true)
            minHeight = dp(54)
            isEnabled = !busy && host != null
            setOnClickListener { if (host != null) approveType(item, host) }
        }
        contentBody.addView(type, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(7)
        })
        contentBody.addView(
            text(
                "TYPE ONLY never presses Enter. Confirm the text on the computer, then press Enter yourself.",
                12f,
                Color.DKGRAY,
                Typeface.BOLD
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(6))
            },
            matchWrap()
        )
    }

    private fun commandRow(item: LinuxCommandItem, action: () -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = round(DARK_CARD, 16, BORDER, 1)
            isClickable = true
            isFocusable = true
            contentDescription = item.title
            setOnClickListener { action() }
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(item.title, 15f, Color.WHITE, Typeface.BOLD))
            addView(
                text("${item.category}  ›  ${item.topic}", 11f, MUTED).apply {
                    setPadding(0, dp(3), 0, 0)
                }
            )
        }
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(riskLabel(item))
        row.addView(top)
        row.addView(
            text(item.command, 12f, COMMAND_TEXT).apply {
                typeface = Typeface.MONOSPACE
                maxLines = 2
                setPadding(0, dp(9), 0, 0)
            }
        )
        return row
    }

    private fun navigationRow(
        title: String,
        subtitle: String,
        badge: String,
        action: () -> Unit
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), dp(11), dp(12), dp(11))
        background = round(DARK_CARD, 16, BORDER, 1)
        isClickable = true
        isFocusable = true
        contentDescription = title
        setOnClickListener { action() }

        val copy = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 16f, Color.WHITE, Typeface.BOLD))
            addView(text(subtitle, 12f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
        }
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        addView(
            text(badge, 12f, GREEN, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                background = round(0xff1c3f2c.toInt(), 24)
                setPadding(dp(10), dp(7), dp(10), dp(7))
            }
        )
        addView(
            text("›", 24f, 0xffc7d3cd.toInt(), Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, 0, 0)
            }
        )
    }

    private fun pageIntro(title: String, subtitle: String): View = darkCard().apply {
        addView(text(title, 20f, Color.WHITE, Typeface.BOLD))
        addView(text(subtitle, 13f, MUTED).apply { setPadding(0, dp(6), 0, 0) })
    }

    private fun riskLabel(item: LinuxCommandItem): TextView {
        val (name, color, fill) = when (item.risk) {
            LinuxCommandRisk.LOW -> Triple("LOW", GREEN, 0xff1c432d.toInt())
            LinuxCommandRisk.MEDIUM -> Triple("MEDIUM", 0xffffc661.toInt(), 0xff4a391b.toInt())
            LinuxCommandRisk.HIGH -> Triple("HIGH", 0xffff8790.toInt(), 0xff4a2228.toInt())
        }
        return text(
            if (item.requiresAdmin) "$name · ADMIN" else name,
            10f,
            color,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.CENTER
            background = round(fill, 24)
            setPadding(dp(9), dp(6), dp(9), dp(6))
        }
    }

    private fun infoBlock(parent: LinearLayout, title: String, value: String) {
        parent.addView(
            View(activity).apply { setBackgroundColor(BORDER) },
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(10)
                bottomMargin = dp(9)
            }
        )
        parent.addView(text(title, 11f, MUTED, Typeface.BOLD))
        parent.addView(text(value, 13f, Color.WHITE).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun commandBox(command: String): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = round(COMMAND_BG, 13, 0xff315246.toInt(), 1)
        addView(text(command, 14f, COMMAND_TEXT).apply {
            typeface = Typeface.MONOSPACE
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE
            }
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            setTextIsSelectable(true)
        })
    }

    private fun darkCard(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = round(DARK_CARD, 18, BORDER, 1)
    }

    private fun copyCommand(command: String) {
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Linux command", command))
        Toast.makeText(activity, "Command copied. Nothing was typed.", Toast.LENGTH_SHORT).show()
    }

    /** The detail page already shows the command and destination; approve once there. */
    private fun approveType(item: LinuxCommandItem, host: HostSession) {
        if (busy || !dialog.isShowing || level != Level.DETAIL || selectedItem !== item || target.current() != host) {
            Toast.makeText(activity, "The command or destination changed. Review again.", Toast.LENGTH_LONG).show()
            return
        }
        CommandPolicy.problem(item.command)?.let { Toast.makeText(activity, it, Toast.LENGTH_LONG).show(); return }
        busy = true
        connectionText.text = "Typing reviewed text only · Enter excluded"
        render()
        val approval = CommandApproval(item.command, host)
        scope.launch {
            try {
                val outcome = ApprovedCommandSender.send(approval, target)
                val message = if (outcome == SendOutcome.REPORTS_ACCEPTED)
                    "Text typed. Check the computer and press Enter manually."
                    else "Typing stopped ($outcome). Inspect any partial text; no automatic retry."
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { Toast.makeText(activity, "Typing stopped. Inspect any partial text manually.", Toast.LENGTH_LONG).show() }
              finally { busy = false; refreshConnectionState(); render() }
        }
    }

    private fun refreshConnectionState() {
        if (!::connectionPill.isInitialized) return
        val host = target.current()
        if (host == null) {
            connectionPill.text = "●  BROWSE"
            connectionPill.setTextColor(0xffffc661.toInt())
            connectionPill.background = round(0xff493819.toInt(), 24)
            if (!busy) {
                connectionText.text =
                    "Offline browsing is ready. Pair Bluetooth before TYPE ONLY."
            }
        } else {
            connectionPill.text = "●  READY"
            connectionPill.setTextColor(GREEN)
            connectionPill.background = round(0xff1d432e.toInt(), 24)
            if (!busy) connectionText.text = "Connected · ${host.label}"
        }
    }

    private fun updateBreadcrumb() {
        if (!::breadcrumb.isInitialized) return
        breadcrumb.text = when {
            level == Level.DETAIL -> selectedItem?.title ?: "Command"
            level in setOf(Level.LESSON, Level.LESSONS) -> selectedLesson?.title ?: "Guided lessons"
            level == Level.TOOLS -> "Tool and app directory"
            level == Level.TOOL -> selectedTool?.name ?: "Tool"
            level == Level.CLI -> selectedCli?.invocation ?: "Command reference"
            search.text?.isNotBlank() == true || level == Level.SEARCH -> "Search results"
            level == Level.COMMANDS -> "$selectedCategory  ›  $selectedTopic"
            level == Level.TOPICS -> selectedCategory
            else -> "All categories"
        }
    }

    private fun navigateBack() {
        when {
            level == Level.CLI -> { level = if (selectedTool != null) Level.TOOL else Level.SEARCH; render() }
            level == Level.TOOL -> { level = Level.TOOLS; render() }
            level == Level.TOOLS || level == Level.LESSONS -> { level = Level.CATEGORIES; selectedLesson = null; render() }
            level == Level.LESSON -> { level = if (guideCommands.isNotEmpty()) Level.TOOL else Level.LESSONS; render() }
            level == Level.DETAIL -> {
                level = detailReturnLevel
                selectedItem = null
                render()
            }
            search.text?.isNotBlank() == true || level == Level.SEARCH -> {
                search.text.clear()
                level = Level.CATEGORIES
                render()
            }
            level == Level.COMMANDS -> {
                selectedTopic = ""
                level = Level.TOPICS
                render()
            }
            level == Level.TOPICS -> {
                selectedCategory = ""
                level = Level.CATEGORIES
                render()
            }
            else -> dismiss()
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        style: Int = Typeface.NORMAL
    ) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, style)
        includeFontPadding = false
    }

    private fun round(
        fill: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeDp: Int = 0
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null && strokeDp > 0) {
            setStroke(dp(strokeDp), strokeColor)
        }
    }

    private fun matchWrap(
        top: Int = 0,
        bottom: Int = 0
    ) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        private val DARK = 0xff101714.toInt()
        private val DARK_CARD = 0xff151f1b.toInt()
        private val BORDER = 0xff34483f.toInt()
        private val MUTED = 0xffb5c4bd.toInt()
        private val GREEN = 0xff74f45b.toInt()
        private val COMMAND_BG = 0xff0b1411.toInt()
        private val COMMAND_TEXT = 0xffd8f1c8.toInt()
    }
}
