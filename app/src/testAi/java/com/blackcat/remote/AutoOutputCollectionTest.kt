package com.blackcat.remote

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AutoOutputCollectionTest {
    private fun step(command: String) = SuggestedCommand("Inspect", command, "Understand the system.", "low", false, "Useful evidence", emptyList())
    private fun shell(command: String): String {
        val process = ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(output, 0, process.exitValue())
        return output
    }
    private fun observed(op: AutoOutputCollection.Operation, state: String, text: String = "page", code: String = "0") =
        AutoObservation("", "shell_prompt", "appears_complete", "Page visible", listOf("Both markers and the prompt visible"),
            false, op.marker, state, text, code)

    @Test fun capturesOnceThenReadsAllPagesWithoutRepeatingTheCommandAndCleansUp() {
        val input = Files.createTempFile("bc-fixture", ".txt")
        val content = (1..19).joinToString("\n") { "Evidence line $it" } // Last line has no newline.
        input.toFile().writeText(content)
        val collector = AutoOutputCollection()
        val prepared = collector.prepare(step("cat $input"))
        val initial = collector.operation(prepared.command)!!
        try {
            assertTrue(prepared.command.length <= 512)
            val header = shell(prepared.command)
            assertTrue(header.contains("${initial.marker}_0"))
            initial.ticket.exitCode = "0"
            assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(java.nio.file.Path.of(initial.ticket.path))))
            // Destroy the input: subsequent pages must come from the original captured snapshot.
            Files.delete(input)
            var op = initial
            val pages = mutableListOf<String>()
            repeat(3) { i ->
                val command = collector.nextPage(op)!!
                assertFalse(command.command.contains("cat $input"))
                assertTrue(command.command.length <= 512)
                op = collector.operation(command.command)!!
                val output = shell(command.command)
                val state = if (i == 2) "DONE" else "MORE"
                assertTrue(output, output.contains("${op.marker}_END_0_$state"))
                val body = output.substringAfter("${op.marker}_BEGIN\n").substringBefore("\n${op.marker}_END_")
                pages.add(body.trimEnd('\n'))
                collector.retain(op, observed(op, state.lowercase(), body))
            }
            assertEquals(content, pages.joinToString("\n"))
            shell(collector.cleanup(op).command)
            assertFalse(Files.exists(java.nio.file.Path.of(initial.ticket.path)))
            assertTrue(collector.context(op).contains("Evidence line 1"))
            assertTrue(collector.evidence(op).contains("Evidence line 19"))
        } finally {
            Files.deleteIfExists(input)
            cleanup(initial.ticket.path)
        }
    }
    @Test fun preservesRealFailureCodeAndEscapesTerminalControlBytes() {
        val input = Files.createTempFile("bc-controls", ".txt")
        input.toFile().writeText("\u001b[31mUNTRUSTED\u0007\n")
        val collector = AutoOutputCollection()
        val capture = collector.prepare(step("cat $input"))
        val op = collector.operation(capture.command)!!
        try {
            shell(capture.command)
            val output = shell(collector.nextPage(op)!!.command)
            assertFalse(output.contains('\u001b'))
            assertFalse(output.contains('\u0007'))
            assertTrue(output.contains("^[[31mUNTRUSTED^G"))
        } finally { Files.deleteIfExists(input); cleanup(op.ticket.path) }
        val fail = collector.prepare(step("ls /bc-fixture-nonexistent-path"))
        val failed = collector.operation(fail.command)!!
        try {
            assertTrue(shell(fail.command).contains("${failed.marker}_2"))
            val page = collector.nextPage(failed)!!
            assertTrue(shell(page.command).contains("_END_2_DONE"))
        } finally { cleanup(failed.ticket.path) }
    }
    @Test fun ownershipCannotBeForgedAndUnapprovedScriptsAreNeverWrapped() {
        val value = AutoOutputCollection()
        for (command in listOf("id; whoami", "sh -c id", "cat /etc/shadow", "rm -rf /")) {
            val input = step(command)
            assertSame(input, value.prepare(input))
            assertFalse(value.owns(command))
        }
        val prepared = value.prepare(step("id"))
        assertTrue(value.owns(prepared.command))
        assertFalse(value.owns(prepared.command + " "))
        assertFalse(AutoRunPolicy.canRunReviewed(prepared))
        value.clear()
        assertFalse(value.owns(prepared.command))
    }
    @Test fun markerExitCodePromptAndPageOrderAreRequired() {
        val value = AutoOutputCollection()
        val capture = value.prepare(step("id"))
        val initial = value.operation(capture.command)!!
        assertTrue(observed(initial, "captured").collectionReady(initial))
        assertFalse(observed(initial, "done").collectionReady(initial))
        initial.ticket.exitCode = "2"
        assertFalse(observed(initial, "captured").collectionReady(initial))
        val page = value.operation(value.nextPage(initial)!!.command)!!
        assertFalse(observed(page, "unreadable", code = "2").collectionReady(page))
        assertFalse(AutoObservation("", "busy", "unclear", "", listOf("evidence"), false, page.marker, "done", "text", "2").collectionReady(page))
        assertFalse(AutoObservation("", "shell_prompt", "appears_complete", "", listOf("evidence"), false, "wrong", "done", "text", "2").collectionReady(page))
        value.retain(page, observed(page, "done", code = "2"))
        try { value.retain(page, observed(page, "done", code = "2")); fail("Duplicate page accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun pageLimitIsExplicitAndCleanupRemovesTemporaryDataInsteadOfLooping() {
        val input = Files.createTempFile("bc-limit", ".txt")
        input.toFile().writeText((1..200).joinToString("\n") { "Line $it" })
        val value = AutoOutputCollection()
        val prepared = value.prepare(step("cat $input"))
        val first = value.operation(prepared.command)!!
        try {
            shell(prepared.command)
            var op = first
            repeat(AutoOutputCollection.MAX_PAGES) { n ->
                val next = value.nextPage(op)!!
                assertTrue("Page ${n + 1} length ${next.command.length}", next.command.length <= 512)
                op = value.operation(next.command)!!
                val text = shell(next.command)
                if (n == AutoOutputCollection.MAX_PAGES - 1) assertTrue(text, text.contains("_LIMIT"))
            }
            assertNull(value.nextPage(op))
            shell(value.cleanup(op).command)
            assertFalse(Files.exists(java.nio.file.Path.of(first.ticket.path)))
        } finally { Files.deleteIfExists(input); cleanup(first.ticket.path) }
    }
    @Test fun partiallyVisiblePageCanBeRedisplayedInSmallerSlicesWithoutLosingLines() {
        val input = Files.createTempFile("bc-resize", ".txt")
        input.toFile().writeText((1..10).joinToString("\n") { "Row $it" })
        val collector = AutoOutputCollection()
        val capture = collector.prepare(step("cat $input"))
        val first = collector.operation(capture.command)!!
        try {
            shell(capture.command)
            val big = collector.operation(collector.nextPage(first)!!.command)!!
            shell(big.command) // Unreadable page was displayed, but source output must remain.
            val small = collector.operation(collector.smallerPage(big)!!.command)!!
            assertEquals(big.from, small.from)
            assertEquals(4, small.to)
            assertNotEquals(big.marker, small.marker)
            assertTrue(shell(small.command).contains("Row 4"))
            collector.retain(small, observed(small, "more", "Rows 1-4"))
            val next = collector.operation(collector.nextPage(small)!!.command)!!
            assertEquals(5, next.from)
            assertEquals(8, next.to)
            assertTrue(shell(next.command).contains("Row 5"))
            shell(collector.cleanup(next).command)
        } finally { Files.deleteIfExists(input); cleanup(first.ticket.path) }
    }
    @Test fun quotedSudoStillUsesNoninteractiveAuthentication() {
        assertEquals("sudo -n 'ufw' 'status'", AutoRunPolicy.nonInteractive("'sudo' ufw status"))
        assertEquals("sudo -n 'ufw' 'status'", AutoRunPolicy.nonInteractive("sudo -n ufw status"))
    }
    @Test fun autoRevisionIncludesOriginalGoalAllEvidenceAndMissingFactsBecomeDiagnostics() {
        val request = AutoRevisionContext.goal("Secure my Linux host", step("id"), listOf(step("uname -r")), "PAGE 1: first\nPAGE 2: second", listOf("sudo -n ufw allow 22/tcp"))
        assertTrue(request.contains("Secure my Linux host"))
        assertTrue(request.contains("PAGE 1: first"))
        assertTrue(request.contains("PAGE 2: second"))
        assertTrue(request.contains("Do not ask the person to scroll"))
        assertTrue(request.contains("Never repeat a transmitted change"))
        assertTrue(request.contains("untrusted DATA"))
    }
    private fun cleanup(path: String) {
        val dir = java.nio.file.Path.of(path)
        if (Files.exists(dir)) Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }
}
