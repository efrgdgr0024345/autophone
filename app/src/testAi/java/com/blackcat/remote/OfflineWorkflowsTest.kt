package com.blackcat.remote

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfflineWorkflowsTest {
    private fun root(): JSONObject = JSONObject(listOf(File("../catalog/offline-catalog.json"), File("catalog/offline-catalog.json")).first { it.exists() }.readText())
    private val data by lazy { OfflineLearningCatalog.parse(root().toString()) }

    @Test fun everyKaliCommandAndToolHasAUsableSourceLinkedRoute() {
        assertEquals(820, data.workflows.kaliToolIds.size)
        assertEquals(3055, data.workflows.kaliEntryIds.size)
        assertEquals(824, data.workflows.tools.size)
        assertEquals(3448, data.workflows.commands.size)
        for (id in data.workflows.kaliEntryIds) {
            val entry = data.entryById[id]!!
            val guide = OfflineWorkflows.commandGuide(data, entry)
            assertEquals(entry.id, guide.entryId)
            assertTrue(guide.commands.size in 3..4)
            assertTrue(guide.commands.all { CommandPolicy.problem(it.command) == null })
            assertTrue(guide.sources.isNotEmpty())
            assertTrue(guide.lesson.steps.all { it.onError.isNotBlank() })
            assertEquals(guide.commands.map { it.id }, guide.lesson.steps.map { it.commandId })
            assertEquals(LinuxCommandRisk.HIGH, OfflineWorkflows.prepare(entry, listOf("--example-literal")).risk)
        }
        for (id in data.workflows.kaliToolIds) {
            val tool = data.toolById[id]!!
            assertTrue(tool.packages.isNotEmpty())
            for (pkg in tool.packages) assertEquals(3, OfflineWorkflows.packageGuide(tool, pkg).commands.size)
        }
    }
    @Test fun packageOnlyGroupsRemainUsefulWithoutInventedExecutables() {
        val tools = data.workflows.tools.values.filter { it.toolId in data.workflows.kaliToolIds && it.mode == "package_only" }
        assertEquals(38, tools.size)
        for (tool in tools) {
            assertTrue(tool.entries.isEmpty())
            assertTrue(OfflineWorkflows.packageGuide(data.toolById[tool.toolId]!!, tool.packages.first()).commands.all { it.command.startsWith("dpkg-query ") })
        }
    }
    @Test fun subcommandsLocateTheBackingBinaryAndUseTheirOwnManual() {
        val entry = data.entries.first { it.invocation == "git add" }
        val guide = OfflineWorkflows.commandGuide(data, entry)
        assertEquals("command -v git", guide.commands[0].command)
        assertEquals("man -f -- git-add", guide.commands[1].command)
        assertEquals("man -P cat -- git-add | head -n 40", guide.commands[2].command)
        assertEquals("git add 'a file'", OfflineWorkflows.prepare(entry, listOf("a file")).command)
    }
    @Test fun taskArgumentsCannotBecomeOuterShellSyntaxAndEmptyArgumentsSurvive() {
        val entry = data.entries.first { it.invocation == "printf" }
        assertEquals("printf '%s' 'a'\\''b' '\$(id); touch /tmp/never-run' ''",
            OfflineWorkflows.prepare(entry, listOf("%s", "a'b", "\$(id); touch /tmp/never-run", "")).command)
    }
    @Test fun controlsAndLongArgumentsAreRejectedBeforePreparation() {
        val entry = data.entries.first { it.invocation == "nmap" }
        for (value in listOf("a\nb", "a\rb", "a\tb", "a\u001bb", "é", "x".repeat(513))) {
            try { OfflineWorkflows.prepare(entry, listOf(value)); fail("Accepted invalid argument") }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun missingHelpIsNotInventedAndKnownHelpMustMatchTheInvocation() {
        for (entry in data.entries.filter { it.id in data.workflows.commands }) {
            val workflow = data.workflows.commands[entry.id]!!
            workflow.help?.let { assertTrue(it in entry.help); assertTrue(OfflineWorkflows.isObservedHelp(workflow.tokens, it)) }
        }
        assertNull(data.workflows.commands[data.entries.first { it.invocation == "git add" }.id]!!.help)
    }
    @Test(expected = IllegalArgumentException::class) fun aMissingWorkflowCannotMasqueradeAsFullCoverage() {
        val root = root();root.getJSONObject("workflow_support").getJSONArray("command_workflows").remove(0)
        OfflineLearningCatalog.parse(root.toString())
    }
    @Test(expected = IllegalArgumentException::class) fun sourceDataCannotReplaceADiscoveryCheckWithAnOperation() {
        val root = root();root.getJSONObject("workflow_support").getJSONArray("command_workflows").getJSONObject(0)
            .getJSONArray("discovery_commands").put(0, "rm /tmp/never-run")
        OfflineLearningCatalog.parse(root.toString())
    }
    @Test(expected = IllegalArgumentException::class) fun falseCoverageNumbersAreRejected() {
        val root = root();root.getJSONObject("workflow_support").getJSONObject("coverage").put("supported_kali_commands", 1)
        OfflineLearningCatalog.parse(root.toString())
    }
    @Test(expected = IllegalArgumentException::class) fun fakeExecutableCannotBeSubstitutedIntoAWorkflow() {
        val root = root();root.getJSONObject("workflow_support").getJSONArray("command_workflows").getJSONObject(0)
            .getJSONArray("invocation_tokens").put(0, "sh")
        OfflineLearningCatalog.parse(root.toString())
    }
}
