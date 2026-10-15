package com.blackcat.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AutoRunSessionTest {
    private val host = HostSession("synthetic-host", 1, "Synthetic test target")
    private fun step(command: String = "id", risk: String = "low", admin: Boolean = false) =
        SuggestedCommand("Check", command, "Explain purpose.", risk, admin, "Expected result.", emptyList())
    private fun session(auto: Boolean = false, full: Boolean = false, steps: List<SuggestedCommand> = listOf(step())) =
        AutoRunSession(steps, host, 7, auto, 0, full)
    private class Target(var host: HostSession?) : CommandTarget {
        val reports = mutableListOf<Pair<Int, Int>>()
        var callback: (Int) -> Unit = {}
        var reject = false
        override fun current() = host
        override fun sendKey(session: HostSession, modifier: Int, usage: Int): Boolean {
            if (session != host || reject) return false
            reports.add(modifier to usage)
            callback(usage)
            return true
        }
    }

    @Test fun openingSessionNeverCreatesAnApprovalAndGuidedCannotAutoApprove() {
        val value = session()
        assertEquals(AutoStage.REVIEW_STEP, value.stage)
        assertNull(value.approve(host, 7, human = false))
        assertEquals(AutoStage.REVIEW_STEP, value.stage)
    }
    @Test fun readOnlyAllowlistDoesNotTrustAModelRiskLabel() {
        for (text in listOf("sudo ufw enable", "rm -rf /tmp/example", "id && whoami", "id\n", "curl https://example.com", "bash -c id"))
            assertFalse(text, AutoRunPolicy.canRun(step(text)))
        assertFalse(AutoRunPolicy.canRun(step("id", "high")))
        assertFalse(AutoRunPolicy.canRun(step("id", admin = true)))
        assertTrue(AutoRunPolicy.canRun(step("systemctl --failed --no-pager")))
    }
    @Test fun fullAutoSupportsReviewedChangesButRejectsDestructiveOrCompoundInput() {
        assertTrue(AutoRunPolicy.canRunReviewed(step("sudo -n ufw allow 22/tcp", "high", true)))
        for (text in listOf("sudo reboot", "rm -rf /", "apt-get purge openssh-server", "systemctl stop ssh",
            "systemctl 'stop' ssh", "systemctl st'o'p ssh", "systemctl \"stop\" ssh", "sudo chmod -R 000 /", "cat 'unfinished",
            "id; whoami", "echo $(id)", "sh -c id", "sudo /tmp/script", "find /tmp -exec sh {} +", "sed '1e id' /etc/hosts"))
            assertFalse(text, AutoRunPolicy.canRunReviewed(step(text)))
    }
    @Test fun changedHostEpochOrRevisionRejectsApproval() {
        val value = session(auto = true)
        assertNull(value.approve(host.copy(epoch = 2), 7, false))
        assertNull(value.approve(host, 8, false))
        assertNotNull(value.approve(host, 7, false))
        assertNull(value.approve(host, 7, false))
    }
    @Test fun guidedApprovalTypesWithoutEnter() = runBlocking {
        val value = session()
        val target = Target(host)
        val approval = value.approve(host, 7, true)!!
        assertEquals(SendOutcome.REPORTS_ACCEPTED, AutoReadOnlySender.send(approval, value, target) { 7 })
        assertTrue(target.reports.none { it.second == 40 })
        assertTrue(value.typed(approval))
        assertEquals(AutoStage.MANUAL_ENTER, value.stage)
        assertTrue(value.userRanCommand())
        assertFalse(value.userRanCommand())
    }
    @Test fun consentedReadOnlyAutoSendsEnterExactlyOnceAndNeverReplays() = runBlocking {
        val value = session(auto = true)
        val target = Target(host)
        val approval = value.approve(host, 7, false)!!
        assertEquals(SendOutcome.REPORTS_ACCEPTED, AutoReadOnlySender.send(approval, value, target) { 7 })
        assertEquals(1, target.reports.count { it.second == 40 })
        assertEquals(SendOutcome.ALREADY_USED, AutoReadOnlySender.send(approval, value, target) { 7 })
        assertEquals(1, target.reports.count { it.second == 40 })
        assertTrue(value.typed(approval))
        assertEquals(AutoStage.WAITING, value.stage)
    }
    @Test fun fullAutoRequiresSessionConsentForAReviewedChange() = runBlocking {
        val steps = listOf(step("sudo -n ufw allow 22/tcp", "high", true))
        assertNull(session(auto = true, steps = steps).approve(host, 7, false))
        val value = session(full = true, steps = steps)
        val target = Target(host)
        val approval = value.approve(host, 7, false)!!
        assertEquals(SendOutcome.REPORTS_ACCEPTED, AutoReadOnlySender.send(approval, value, target) { 7 })
        assertEquals(1, target.reports.count { it.second == 40 })
    }
    @Test fun stoppingBetweenTypingAndEnterBlocksExecution() = runBlocking {
        val value = session(auto = true)
        val target = Target(host).apply { callback = { usage -> if (usage == 0) value.stop() } }
        val approval = value.approve(host, 7, false)!!
        assertEquals(SendOutcome.HOST_CHANGED, AutoReadOnlySender.send(approval, value, target) { 7 })
        assertTrue(target.reports.none { it.second == 40 })
    }
    @Test fun hostChangeDuringTypingPreventsEnterAndNextCommand() = runBlocking {
        val value = session(auto = true)
        val target = Target(host).apply { callback = { if (reports.size == 1) host = host?.copy(epoch = 2) } }
        val approval = value.approve(host, 7, false)!!
        assertEquals(SendOutcome.HOST_CHANGED, AutoReadOnlySender.send(approval, value, target) { 7 })
        assertTrue(target.reports.none { it.second == 40 })
        assertFalse(value.valid(target.current(), 7))
    }
    @Test fun rejectionDoesNotExecuteOrRetry() = runBlocking {
        val value = session(auto = true)
        val target = Target(host).apply { reject = true }
        val approval = value.approve(host, 7, false)!!
        assertEquals(SendOutcome.REJECTED, AutoReadOnlySender.send(approval, value, target) { 7 })
        assertTrue(target.reports.isEmpty())
        assertEquals(SendOutcome.ALREADY_USED, AutoReadOnlySender.send(approval, value, target) { 7 })
    }
    private fun observe(value: AutoRunSession): Long {
        val approval = value.approve(host, value.revision, false)!!
        value.typed(approval)
        return value.captured()!!
    }
    @Test fun noStepAdvanceBeforePhotoOrAfterUncertainEvidence() {
        val value = session(auto = true, steps = listOf(step(), step("whoami")))
        assertFalse(value.next(true, false))
        val token = observe(value)
        assertFalse(value.observed(token + 1))
        assertTrue(value.observed(token))
        assertFalse(value.next(false, false))
        assertTrue(value.next(true, false))
        assertEquals(1, value.index)
        assertEquals(AutoStage.REVIEW_STEP, value.stage)
    }
    @Test fun stopInvalidatesLatePhotoAndAllApprovals() {
        val value = session(auto = true)
        val token = observe(value)
        value.stop()
        assertFalse(value.observed(token))
        assertFalse(value.next(true, true))
        assertNull(value.approve(host, 7, true))
    }
    @Test fun revisedCommandsCanAdaptToTheGoalButCannotRetryATransmittedChange() {
        val value = session(full = true, steps = listOf(step("sudo -n ufw allow 22/tcp", "high", true), step("whoami")))
        value.observed(observe(value))
        fun plan(command: String) = CommandPlan("Update", emptyList(), emptyList(), listOf(step(command)))
        assertTrue(value.canAdopt(plan("sudo -n ufw allow 443/tcp")))
        assertTrue(value.canAdopt(plan("journalctl -n 30 --no-pager")))
        assertFalse(value.canAdopt(plan("sudo -n ufw allow 22/tcp")))
        assertTrue(value.canAdopt(plan("id")))
        assertTrue(value.adopt(plan("id"), 8))
        assertEquals(8L, value.revision)
        assertEquals(1, value.index)
        assertEquals("id", value.current!!.command)
        assertFalse(value.valid(host, 7))
    }
    @Test fun guidedAndReadOnlySessionsCannotAutomaticallyAdoptPlans() {
        val plan = CommandPlan("Update", emptyList(), emptyList(), listOf(step()))
        assertFalse(session().canAdopt(plan))
        assertFalse(session(auto = true).canAdopt(plan))
    }
    @Test fun advancingOrStoppingInvalidatesAPlanRevisionFromThePreviousResult() {
        val candidate = CommandPlan("Update", emptyList(), emptyList(), listOf(step("id")))
        val value = session(full = true, steps = listOf(step(), step("whoami")))
        value.observed(observe(value))
        assertTrue(value.canAdopt(candidate))
        value.next(true, true)
        assertFalse(value.canAdopt(candidate))
        assertFalse(value.adopt(candidate, 8))
        value.stop()
        assertFalse(value.canAdopt(candidate))
    }
    @Test fun collectionStepsAreVisibleAndPreserveTheRemainingPlanAndRevisionBoundary() {
        val value = session(full = true, steps = listOf(step(), step("whoami")))
        var revision = 7L
        var visible = value.steps
        value.prepareCurrent { visible = it; ++revision }
        assertEquals(value.steps, visible)
        assertTrue(value.collection.owns(value.current!!.command))
        val approval = value.approve(host, revision, false)!!
        value.typed(approval)
        val oldToken = value.captured()!!
        value.observed(oldToken)
        val op = value.collection.operation(value.current!!.command)!!
        val page = value.collection.nextPage(op)!!
        assertTrue(value.insertCollection(page, ++revision))
        assertEquals(page.command, value.current!!.command)
        assertEquals("whoami", value.steps.last().command)
        assertFalse(value.observed(oldToken))
        assertFalse(value.valid(host, revision - 1))
        assertEquals(listOf("id"), value.transmitted)
    }
    @Test fun busyRecaptureCannotReplayAnApprovalAndHasABoundedPhotoBudget() {
        val value = session(full = true)
        val first = value.approve(host, 7, false)!!
        value.typed(first)
        var previous = -1L
        repeat(AutoRunSession.MAX_CAPTURES) {
            val token = value.captured()!!
            if (previous >= 0) assertFalse(value.observed(previous))
            assertTrue(value.observed(token))
            assertNull(value.approve(host, 7, false))
            assertTrue(value.waitAgain())
            previous = token
        }
        assertNull(value.captured())
        assertEquals(AutoStage.BLOCKED, value.stage)
    }
    @Test fun quotedOrNoninteractiveVariantsCannotReplayATransmittedChange() {
        val value = session(full = true, steps = listOf(step("sudo -n ufw allow 22/tcp")))
        value.observed(observe(value))
        val candidate = CommandPlan("Updated", emptyList(), emptyList(), listOf(step("sudo ufw 'allow' 22/tcp")))
        assertFalse(value.canAdopt(candidate))
        assertFalse(AutoRunPolicy.isReadOnly(step("journalctl --rotate")))
        assertFalse(AutoRunPolicy.isReadOnly(step("ss -K")))
        assertFalse(AutoRunPolicy.isReadOnly(step("systemctl restart status")))
    }
    @Test fun aReconciledEmptyRemainingPlanFinishesWithoutAskingForAnotherStep() {
        val value = session(full = true)
        value.observed(observe(value))
        val complete = CommandPlan("Evidence indicates no further commands are needed.", emptyList(), emptyList(), emptyList())
        assertTrue(value.adopt(complete, 8))
        assertEquals(AutoStage.FINISHED, value.stage)
    }

    @Test fun stoppingAndReopeningCannotAuthorizeOldCollectionShell() {
        val old = session(full = true, steps = listOf(step(), step("whoami")))
        var revision = 7L
        var published = old.steps
        old.prepareCurrent { published = it; ++revision }
        val oldCapture = old.current!!
        assertTrue(old.canRun(oldCapture))
        old.stop()
        old.collection.clear()

        // AiPanel retains the last published command list when Auto closes. A new
        // session deliberately has no ownership of that old generated shell.
        val reopened = AutoRunSession(published, host, revision, false, 0, true)
        reopened.prepareCurrent { error("Old helper scripts must never be rewrapped or replayed") }
        assertEquals(oldCapture.command, reopened.current!!.command)
        assertFalse(reopened.canRun(reopened.current!!))
        assertNull(reopened.approve(host, revision, human = false))
        assertEquals(AutoStage.REVIEW_STEP, reopened.stage)
    }

    @Test fun countdownStopHandsOffCanonicalCommandsForAFreshReviewedSession() {
        val old = session(full = true, steps = listOf(step(), step("whoami")))
        old.prepareCurrent { 8 }
        old.approve(host, 8, human = false)!! // No send boundary reached during the countdown.
        old.stop()
        val pending = old.handoff()
        assertEquals(listOf("id", "whoami"), pending.remaining.map { it.command })
        assertEquals(0, pending.attemptedCount)
        val fresh = AutoRunSession(pending.remaining, host, 9, false, 0, true)
        fresh.prepareCurrent { 10 }
        assertTrue(fresh.canRun(fresh.current!!))
        assertNotNull(fresh.approve(host, 10, human = false))
    }

    @Test fun interruptedTransmissionIsExcludedFromHandoffEvenBeforeTypedCompletes() = runBlocking {
        val old = session(full = true, steps = listOf(step(), step("whoami")))
        old.prepareCurrent { 8 }
        val target = Target(host).apply { callback = { if (reports.size == 1) old.stop() } }
        val approval = old.approve(host, 8, false)!!
        assertEquals(SendOutcome.HOST_CHANGED, AutoReadOnlySender.send(approval, old, target) { 8 })
        val pending = old.handoff()
        assertEquals(listOf("whoami"), pending.remaining.map { it.command })
        assertEquals("id", pending.lastAttempted!!.command)
        assertEquals(1, pending.attemptedCount)
        assertTrue(target.reports.none { it.second == 40 })
    }

    @Test fun stoppingDuringPagesDropsHelpersButPreservesLaterReadOnlyVerification() {
        val old = session(full = true, steps = listOf(step(), step("whoami"), step()))
        old.prepareCurrent { 8 }
        old.typed(old.approve(host, 8, false)!!)
        old.observed(old.captured()!!)
        val capture = old.collection.operation(old.current!!.command)!!
        old.insertCollection(old.collection.nextPage(capture)!!, 9)
        old.stop()
        val pending = old.handoff()
        assertEquals(listOf("whoami", "id"), pending.remaining.map { it.command })
        assertEquals(1, pending.attemptedCount)
        assertFalse(pending.finished)
        assertTrue(pending.remaining.none { it.command.contains("/tmp/bc-") })
    }

    @Test fun finishedHandoffHasNoRunnableCommandsAndDoesNotClaimHumanConfirmation() {
        val value = session(auto = true)
        value.typed(value.approve(host, 7, false)!!)
        value.observed(value.captured()!!)
        value.next(true, false)
        val result = value.handoff()
        assertTrue(result.finished)
        assertTrue(result.remaining.isEmpty())
        assertEquals(1, result.attemptedCount)
        assertEquals("id", result.lastAttempted!!.command)
    }

}
