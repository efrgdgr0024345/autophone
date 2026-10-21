package com.blackcat.remote

import org.junit.Assert.*
import org.junit.Test

class AutoReliabilityTest {
    private val host = HostSession("test-only", 1, "Disposable fixture")
    private fun step(command: String) = SuggestedCommand("Inspect", command, "I'm checking the facts so we can choose a useful next step.",
        "low", false, "The requested evidence.", emptyList())
    private fun observation(marker: String = "", state: String = "none", text: String = "", code: String = "0",
                            outcome: String = "appears_complete", revision: Boolean = false) =
        AutoObservation("id", "shell_prompt", outcome, "The evidence shows the account information we need.",
            listOf("The complete marker and shell prompt are visible."), revision, marker, state, text, code)
    private fun observed(session: AutoRunSession) {
        val approval = session.approve(host, session.revision, false)!!
        session.transmissionStarting(); session.typed(approval)
        assertTrue(session.observed(session.captured()!!))
    }
    private fun collected(truncated: Boolean = false, code: String = "0", pageState: String = "done"): AutoRunSession {
        val session = AutoRunSession(listOf(step("id")), host, 1, false, 0, true)
        session.prepareCurrent { 2 }
        observed(session)
        var op = session.collection.operation(session.current!!.command)!!
        op.ticket.exitCode = code
        op.ticket.truncated = truncated
        val page = session.collection.nextPage(op)!!
        session.insertCollection(page, 3); observed(session)
        op = session.collection.operation(page.command)!!
        val result = observation(op.marker, pageState, "uid=1000", code)
        session.collection.retain(op, result)
        op.ticket.conclusion = result
        session.insertCollection(session.collection.cleanup(op), 4)
        observed(session)
        return session
    }
    @Test fun contractsRejectMutatingArgumentsEvenWhenTheModelSaysLowRisk() {
        for (command in listOf("chmod 777 /etc", "chown root /tmp/example", "cp /tmp/a /tmp/b", "install /tmp/a /tmp/b",
            "ufw allow 22/tcp", "ufw reset", "firewall-cmd --add-port=22/tcp", "sysctl net.ipv4.ip_forward=1",
            "systemctl restart ssh", "systemctl status --root=/tmp", "journalctl --rotate", "journalctl -n 999999",
            "journalctl -n 10 --output-file=/tmp/log", "ss -K", "ip route flush", "getent shadow",
            "cat /proc/self/environ", "cat /tmp/../etc/shadow", "cat /dev/zero", "apt-get install nginx")) {
            assertFalse(command, AutoRunPolicy.canRunReviewed(step(command)))
        }
    }
    @Test fun contractsAllowUsefulBoundedDiagnosticsWithLiteralArguments() {
        for (command in listOf("id", "cat /etc/os-release", "uname -a", "df -h", "free -h", "ss -lntup",
            "ip -brief addr", "ip route", "systemctl --failed --no-pager", "systemctl --failed --type=service --no-pager", "systemctl status nginx --no-pager",
            "journalctl -u nginx -n 30 --no-pager", "sudo -n ufw status verbose", "sysctl net.ipv4.ip_forward",
            "apt list --installed", "getent passwd", "sshd -T")) {
            assertTrue(command, AutoRunPolicy.canRunReviewed(step(command)))
            assertNotNull(AutoRunPolicy.contract(step(command)))
        }
    }
    @Test fun deadlineInvalidatesApprovalWithoutRenewingTheSessionClock() {
        var now = 50L
        val session = AutoRunSession(listOf(step("id")), host, 1, false, 0, true) { now }
        val approval = session.approve(host, 1, false)!!
        now += AutoRunSession.MAX_DURATION_MS
        assertTrue(session.expired)
        assertFalse(session.valid(host, 1))
        assertFalse(session.accepts(approval, host, 1))
    }
    @Test fun stoppingDuringTransmissionPreservesAnUncertainTaskRatherThanSuccess() {
        val session = AutoRunSession(listOf(step("id")), host, 1, false, 0, true)
        session.approve(host, 1, false)
        session.transmissionStarting(); session.stop()
        assertEquals(AutoTaskState.UNCERTAIN, session.history.tasks.single().state)
        assertTrue(session.handoff().remaining.isEmpty())
    }
    @Test fun countdownCancellationLeavesAnUnattemptedTaskPlanned() {
        val session = AutoRunSession(listOf(step("id")), host, 1, false, 0, true)
        session.approve(host, 1, false); session.stop()
        assertEquals(AutoTaskState.PLANNED, session.history.tasks.single().state)
        assertEquals("id", session.handoff().remaining.single().command)
    }
    @Test fun stoppingAndClearingHelperOwnershipPreservesTheOriginalDisplayTask() {
        val original = step("id")
        val session = AutoRunSession(listOf(original), host, 1, false, 0, true)
        session.prepareCurrent { 2 }
        val helper = session.current!!
        session.approve(host, 2, false); session.transmissionStarting(); session.stop(); session.collection.clear()
        assertSame(original, session.originalTask(helper))
        assertEquals(AutoTaskState.UNCERTAIN, session.history.tasks.single().state)
        assertFalse(session.canRun(helper))
    }
    @Test fun helpersShareOneStableTaskIdentityAndOnlyFinalEvidenceCompletesIt() {
        val session = collected()
        assertEquals(1, session.history.tasks.size)
        assertEquals(1, session.history.tasks.single().id)
        assertEquals("id", session.history.tasks.single().task.command)
        assertEquals(AutoTaskState.CHECKING, session.history.tasks.single().state)
        val op = session.collection.operation(session.current!!.command)!!
        assertTrue(session.recordEvidence(observation(op.marker, "cleaned")))
        assertEquals(AutoTaskState.OBSERVED, session.history.tasks.single().state)
    }
    @Test fun truncatedFailedAndLimitedEvidenceCannotBecomeSuccessEvenIfProviderClaimsCompletion() {
        for (session in listOf(collected(truncated = true), collected(code = "2"), collected(pageState = "limit"))) {
            val op = session.collection.operation(session.current!!.command)!!
            assertFalse(session.recordEvidence(observation(op.marker, "cleaned")))
            assertNotEquals(AutoTaskState.OBSERVED, session.history.tasks.single().state)
        }
    }
    @Test fun replacingAPlanKeepsEarlierTaskIdentitiesAndMarksOnlyUnstartedReplacements() {
        val session = AutoRunSession(listOf(step("id"), step("whoami")), host, 1, false, 0, true)
        observed(session)
        session.recordEvidence(observation())
        assertTrue(session.adopt(CommandPlan("Use OS evidence next", emptyList(), emptyList(), listOf(step("uname -a"))), 2))
        assertEquals(listOf(1, 2, 3), session.history.tasks.map { it.id })
        assertEquals(listOf(AutoTaskState.OBSERVED, AutoTaskState.REPLACED, AutoTaskState.PLANNED), session.history.tasks.map { it.state })
        assertEquals("uname -a", session.current!!.command)
    }
    @Test fun staleOrWrongCleanupMarkersCannotCompleteATask() {
        val session = collected()
        assertFalse(session.recordEvidence(observation("BC_stale", "cleaned")))
        assertNotEquals(AutoTaskState.OBSERVED, session.history.tasks.single().state)
    }
    @Test fun speechUsesMeaningfulExplanationWithoutRepeatedReassuranceOrInternalMarkers() {
        val text = NarrationText.step(step("id"))
        assertTrue(text.contains("choose a useful next step"))
        assertFalse(text.contains("stop me at any time"))
        assertFalse(text.contains("Here's what to look for"))
        assertFalse(text.contains("_CAP_"))
        assertTrue(OpenAiSpeechCodec.request(text).getString("instructions").contains("conversationally"))
    }
}
