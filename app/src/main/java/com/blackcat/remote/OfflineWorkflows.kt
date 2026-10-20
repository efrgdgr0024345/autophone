package com.blackcat.remote

import org.json.JSONObject

internal class OfflineCliWorkflow(val entryId: String, val tokens: List<String>, val manual: String,
    val help: String?, val discovery: List<String>)
internal class OfflineToolWorkflow(val toolId: String, val packages: List<String>, val entries: List<String>, val mode: String)
internal class OfflineWorkflowSupport(val commands: Map<String, OfflineCliWorkflow>,
    val tools: Map<String, OfflineToolWorkflow>, val kaliToolIds: Set<String>, val kaliEntryIds: Set<String>) {
    companion object {
        fun parse(root: JSONObject, tools: List<OfflineTool>, entries: List<OfflineCli>): OfflineWorkflowSupport {
            require(root.getInt("version") == 1)
            fun JSONObject.strings(name: String): List<String> = getJSONArray(name).let { a ->
                require(a.length() <= 20000); (0 until a.length()).map { a.getString(it) }
            }
            val executable = entries.filter { it.kind in setOf("binary", "subcommand", "shell_builtin") }.associateBy { it.id }
            val commandRows = root.getJSONArray("command_workflows")
            require(commandRows.length() == executable.size)
            val commands = linkedMapOf<String, OfflineCliWorkflow>()
            for (i in 0 until commandRows.length()) {
                val row = commandRows.getJSONObject(i)
                val entry = requireNotNull(executable[row.getString("entry_id")])
                require(!commands.containsKey(entry.id) && !row.getBoolean("automatic_execution"))
                val tokens = row.strings("invocation_tokens")
                require(tokens == OfflineWorkflows.tokens(entry) && row.getString("executable") == tokens.first())
                val help = if (row.isNull("observed_help_command")) null else row.getString("observed_help_command")
                require(help == null || (help in entry.help && OfflineWorkflows.isObservedHelp(tokens, help)))
                require(row.getString("manual_topic") == entry.name)
                val discovery = OfflineWorkflows.discovery(tokens.first(), entry.name, help)
                require(row.strings("discovery_commands") == discovery)
                require(discovery.all { CommandPolicy.problem(it) == null })
                val builder = row.getJSONObject("task_builder")
                require(builder.getString("default_risk") == "high" && builder.getInt("maximum_command_length") == CommandPolicy.MAX_LENGTH)
                require(builder.getString("argument_mode") == "literal_arguments" && builder.getBoolean("requires_user_preparation") && builder.getBoolean("user_selected_sudo_prefix"))
                commands[entry.id] = OfflineCliWorkflow(entry.id, tokens, entry.name, help, discovery)
            }
            val toolRows = root.getJSONArray("tool_workflows")
            require(toolRows.length() == tools.size)
            val toolById = tools.associateBy { it.id }
            val supportedTools = linkedMapOf<String, OfflineToolWorkflow>()
            for (i in 0 until toolRows.length()) {
                val row = toolRows.getJSONObject(i)
                val tool = requireNotNull(toolById[row.getString("tool_id")])
                require(!supportedTools.containsKey(tool.id) && !row.getBoolean("automatic_execution"))
                val packages = row.strings("package_names")
                val cli = tool.entries.filter { it in executable }
                val mode = if (cli.isEmpty()) "package_only" else if (packages.isEmpty()) "commands" else "commands_and_packages"
                require(packages == tool.packages && packages.all { OfflineWorkflows.packageName(it) })
                require(row.strings("command_entry_ids") == cli && row.getString("mode") == mode)
                require(packages.isNotEmpty() || cli.isNotEmpty())
                supportedTools[tool.id] = OfflineToolWorkflow(tool.id, packages, cli, mode)
            }
            val kaliTools = tools.filter { "kali" in it.distributions }.map { it.id }.toSet()
            val kaliEntries = executable.values.filter { e -> e.toolIds.any { it in kaliTools } }.map { it.id }.toSet()
            val coverage = root.getJSONObject("coverage")
            val expected = mapOf("kali_tools" to kaliTools.size, "kali_commands" to kaliEntries.size,
                "supported_kali_tools" to kaliTools.size, "supported_kali_commands" to kaliEntries.size,
                "package_only_kali_groups" to supportedTools.values.count { it.toolId in kaliTools && it.mode == "package_only" },
                "all_supported_tools" to tools.size, "all_supported_commands" to executable.size)
            expected.forEach { (key, value) -> require(coverage.getInt(key) == value) }
            return OfflineWorkflowSupport(commands, supportedTools, kaliTools, kaliEntries)
        }
    }
}

internal class OfflineGuide(val lesson: OfflineLesson, val commands: List<LinuxCommandItem>,
    val sources: List<String>, val entryId: String? = null)

/** Pure preparation only: no network, process, sender, approval or execution. */
internal object OfflineWorkflows {
    private val name = Regex("[A-Za-z0-9_][A-Za-z0-9+_.:-]{0,119}")
    private val packagePattern = Regex("[a-z0-9][a-z0-9+.-]+")
    fun packageName(value: String) = packagePattern.matches(value)
    fun tokens(entry: OfflineCli): List<String> = entry.invocation.split(' ').also { words ->
        require(words.size in 1..2 && words.all { name.matches(it) || it in setOf(".", ":", "[") })
    }
    fun literal(value: String): String {
        require(value.all { it.code in 32..126 }) { "Arguments must contain printable ASCII; line breaks and controls cannot be typed." }
        return "'" + value.replace("'", "'\\''") + "'"
    }
    private fun word(value: String) = if (name.matches(value)) value else literal(value)
    fun isObservedHelp(tokens: List<String>, line: String): Boolean =
        setOf("--help", "-h", "--version", "-V").any { line == (tokens + it).joinToString(" ") }
    fun discovery(executable: String, manual: String, help: String?): List<String> =
        listOf("command -v ${word(executable)}", "man -f -- ${word(manual)}",
            "man -P cat -- ${word(manual)} | head -n 40") + listOfNotNull(help)

    fun commandGuide(data: OfflineLearningData, entry: OfflineCli): OfflineGuide {
        val workflow = requireNotNull(data.workflows.commands[entry.id])
        val why = listOf("Locate the backing executable before preparing ${entry.invocation}.",
            "Read the local manual summary for ${workflow.manual}. A missing manual does not prove the executable is absent.",
            "Read at most 40 lines of the installed manual without launching ${entry.invocation}.",
            "This help invocation is present in the source. Programs may initialize plugins or configuration before displaying help.")
        val titles = listOf("Locate ${workflow.tokens.first()}", "Read local documentation", "Read a bounded manual page", "Review documented help")
        return guide("command-${entry.id}", "Learn ${entry.invocation}", workflow.discovery, titles, why,
            entry.sources, entry.id, "Review the installed syntax, then build a task with explicit arguments and inspect the actual output.")
    }
    fun packageGuide(tool: OfflineTool, pkg: String): OfflineGuide {
        require(pkg in tool.packages && packageName(pkg))
        return guide("package-${tool.id}-$pkg", "Inspect $pkg", listOf("dpkg-query -W -- $pkg",
            "dpkg-query -s -- $pkg | head -n 40", "dpkg-query -L -- $pkg | head -n 40"),
            listOf("Check installed package version", "Read package status", "Inspect package files"),
            listOf("Read the installed version from the local Debian/Kali package database.",
                "Read a bounded package description and installation state; a missing package is not an invitation to install automatically.",
                "Inspect at most 40 installed-file paths. Libraries, firmware and metapackages may have no executable; choose a documented command or resource next."),
            tool.sources, null, "Inspect local package evidence, then choose a documented command or resource. These checks do not install or launch the package.")
    }
    private fun guide(id: String, title: String, lines: List<String>, titles: List<String>, why: List<String>,
        sources: List<String>, entry: String?, goal: String): OfflineGuide {
        val commands = lines.mapIndexed { i, line ->
            require(CommandPolicy.problem(line) == null)
            LinuxCommandItem("workflow-$id-$i", "Kali workflows", title, titles[i], line, why[i],
                if (i == 0) "An installed path/version or an explicit unavailable result; check the exit status." else "The relevant local documentation or an availability/permission error. A bounded view can be incomplete.",
                if (entry != null && i == 3) LinuxCommandRisk.MEDIUM else LinuxCommandRisk.LOW, false,
                if (entry == null) "Debian/Kali with dpkg-query; the package must be installed." else "Linux with the executable and local manual installed; versions and shell availability vary.", listOf(title))
        }
        val steps = commands.mapIndexed { i, c -> OfflineStep("$id-$i", c.id, why[i], c.expectedResult,
            "Pause on missing tools, documentation, permission errors or unexpected output. Inspect the actual error and revise the plan; do not assume success or retry with sudo.") }
        return OfflineGuide(OfflineLesson(id, title, "Kali workflows", goal, emptyList(), steps, emptyList()), commands, sources, entry)
    }
    fun prepare(entry: OfflineCli, arguments: List<String>, admin: Boolean = false): LinuxCommandItem {
        require(arguments.size <= 24) { "Use at most 24 argument fields." }
        val command = (if (admin) "sudo " else "") +
            (tokens(entry).map { word(it) } + arguments.map { literal(it) }).joinToString(" ")
        CommandPolicy.problem(command)?.let { throw IllegalArgumentException(it) }
        val purpose = entry.summary.takeIf { it.any(Char::isLetterOrDigit) }.orEmpty()
        return LinuxCommandItem("prepared-${entry.id}", "Kali workflows", entry.invocation, "Prepared ${entry.invocation}", command,
            "$purpose\nUser-prepared arguments for ${entry.invocation}. Review the target, installed syntax, privileges and effects. Shell quoting protects literal arguments; tools can interpret their contents themselves.",
            "Review the actual output and exit status for the chosen task. Preparing or typing a command does not prove execution or success.",
            LinuxCommandRisk.HIGH, admin, "Check the installed version, target system and required privileges before use.", listOf(entry.invocation))
    }
}
