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
    private enum class Level { CATEGORIES, TOPICS, COMMANDS, SEARCH, DETAIL }

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

    private lateinit var search: EditText
    private lateinit var breadcrumb: TextView
    private lateinit var connectionPill: TextView
    private lateinit var connectionText: TextView
    private lateinit var contentBody: LinearLayout

    fun show() {
        try {
            items = LinuxCommandCatalog.load(activity.applicationContext)
        } catch (_: Exception) {
            Toast.makeText(
                activity,
                "The offline Linux command library could not be loaded.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

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
            textSize = 34f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            setOnClickListener { navigateBack() }
        }
        top.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        top.addView(
            text("Offline Linux Library", 18f, Color.BLACK, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        val close = TextView(activity).apply {
            text = "×"
            textSize = 29f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Close"
            setOnClickListener { dismiss() }
        }
        top.addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(top)

        val hero = FrameLayout(activity).apply {
            setBackgroundColor(Color.WHITE)
            clipChildren = false
            clipToPadding = false
        }
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
            "${items.size} commands · no API · no network",
            12f,
            Color.DKGRAY,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = round(0xffeaf6e8.toInt(), 22)
        }
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
        listOf("Network", "Disk space", "Memory", "Logs", "Permissions").forEach { value ->
            val chip = Button(activity).apply {
                text = value
                isAllCaps = false
                textSize = 11f
                setTextColor(GREEN)
                background = round(0xff1a3026.toInt(), 24, 0xff38664c.toInt(), 1)
                minHeight = dp(38)
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
                    dp(38)
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
        updateBreadcrumb()

        when {
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

        LinuxCommandCatalog.categories(items).forEach { group ->
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
                level = Level.DETAIL
                render()
            }, matchWrap(bottom = 7))
        }
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
        if (item.tags.isNotEmpty()) {
            infoBlock(card, "Search terms", item.tags.joinToString(", "))
        }
        contentBody.addView(card, matchWrap(bottom = 9))

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
            setOnClickListener { copyCommand(item.command) }
        }
        contentBody.addView(copy, LinearLayout.LayoutParams(-1, dp(50)).apply {
            bottomMargin = dp(8)
        })

        val host = target.current()
        val type = Button(activity).apply {
            text = when {
                busy -> "Typing…"
                host == null -> "Connect Bluetooth first"
                else -> "Preview & type only"
            }
            BlackCatStyle.styleButton(activity, this, true)
            isEnabled = !busy && host != null
            setOnClickListener { showTypeConfirmation(item) }
        }
        contentBody.addView(type, LinearLayout.LayoutParams(-1, dp(54)).apply {
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

    private fun showTypeConfirmation(item: LinuxCommandItem) {
        if (busy || confirmation?.isShowing == true) return
        val problem = CommandPolicy.problem(item.command)
        if (problem != null) {
            Toast.makeText(activity, problem, Toast.LENGTH_LONG).show()
            return
        }
        val host = target.current()
        if (host == null) {
            Toast.makeText(
                activity,
                "Connect the phone as a Bluetooth keyboard first.",
                Toast.LENGTH_LONG
            ).show()
            refreshConnectionState()
            render()
            return
        }

        val detail = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(11), dp(18), dp(8))
            addView(text("Destination", 11f, Color.DKGRAY, Typeface.BOLD))
            addView(text(host.label, 15f, Color.BLACK, Typeface.BOLD).apply {
                setPadding(0, dp(4), 0, dp(10))
            })
            addView(text("Command", 11f, Color.DKGRAY, Typeface.BOLD))
            addView(commandBox(item.command), matchWrap(top = 6, bottom = 8))
            addView(
                text(
                    "One-use approval: type this exact text only. Enter is excluded.",
                    12f,
                    BlackCatStyle.GREEN,
                    Typeface.BOLD
                )
            )
        }

        val confirm = AlertDialog.Builder(activity)
            .setTitle("Review before typing")
            .setView(ScrollView(activity).apply { addView(detail) })
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("TYPE ONLY", null)
            .create()
        confirmation = confirm
        confirm.setCanceledOnTouchOutside(false)
        confirm.setOnShowListener {
            confirm.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (busy || target.current() != host ||
                    CommandPolicy.problem(item.command) != null
                ) {
                    Toast.makeText(
                        activity,
                        "The Bluetooth destination changed. Review again.",
                        Toast.LENGTH_LONG
                    ).show()
                    confirm.dismiss()
                    refreshConnectionState()
                    render()
                    return@setOnClickListener
                }

                confirm.dismiss()
                busy = true
                connectionText.text = "Typing reviewed text only · Enter excluded"
                render()
                val approval = CommandApproval(item.command, host)
                scope.launch {
                    try {
                        val outcome = ApprovedCommandSender.send(approval, target)
                        val message = when (outcome) {
                            SendOutcome.REPORTS_ACCEPTED ->
                                "Text typed. Check the computer and press Enter manually."
                            SendOutcome.HOST_CHANGED ->
                                "Typing stopped because the Bluetooth destination changed."
                            SendOutcome.REJECTED ->
                                "Typing was rejected. Inspect any partial text on the computer."
                            SendOutcome.INVALID ->
                                "The command failed the local send policy."
                            SendOutcome.ALREADY_USED ->
                                "This one-use approval was already consumed."
                        }
                        Toast.makeText(
                            activity,
                            message,
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(
                            activity,
                            "Typing stopped. Inspect any partial text manually.",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        busy = false
                        refreshConnectionState()
                        render()
                    }
                }
            }
        }
        confirm.setOnDismissListener { confirmation = null }
        confirm.show()
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
            search.text?.isNotBlank() == true || level == Level.SEARCH -> "Search results"
            level == Level.COMMANDS -> "$selectedCategory  ›  $selectedTopic"
            level == Level.TOPICS -> selectedCategory
            else -> "All categories"
        }
    }

    private fun navigateBack() {
        when {
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
