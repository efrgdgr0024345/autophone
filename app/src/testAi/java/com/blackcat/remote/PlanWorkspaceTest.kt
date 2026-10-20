package com.blackcat.remote

import org.junit.Assert.*
import org.junit.Test

class PlanWorkspaceTest {
    private fun command(text: String = "id") = SuggestedCommand("Check identity", text,
        "Read account information.", "low", false, "Account information.", emptyList())
    private fun plan(text: String = "id") = CommandPlan("Inspect the system.", emptyList(), emptyList(), listOf(command(text)))

    @Test fun proposalDoesNotReplacePlanOrConfirmSuccess() {
        val state = PlanWorkspace().apply { replace(plan()) }
        val token = state.touch()
        assertTrue(state.stage(token, plan("whoami")))
        assertEquals("id", state.plan!!.commands.single().command)
        assertTrue(state.confirmedSteps.isEmpty())
        assertTrue(state.accept(token))
        assertEquals("whoami", state.plan!!.commands.single().command)
        assertFalse(state.accept(token))
    }

    @Test fun changedFeedbackRejectsLateResponseAndOldApplyAction() {
        val state = PlanWorkspace().apply { replace(plan()) }
        val token = state.touch()
        state.touch()
        assertFalse(state.stage(token, plan("whoami")))
        assertFalse(state.accept(token))
        assertEquals("id", state.plan!!.commands.single().command)
    }

    @Test fun backgroundOrCancellationDiscardsPendingUpdateWithoutLosingPlan() {
        val state = PlanWorkspace().apply { replace(plan()) }
        val token = state.touch()
        state.stage(token, plan("whoami"))
        state.touch()
        assertNull(state.proposal)
        assertEquals("id", state.plan!!.commands.single().command)
    }

    @Test fun editsRequireFreshReviewAndInvalidateOnlyEditedOutcome() {
        val state = PlanWorkspace().apply {
            replace(CommandPlan("Checks", emptyList(), emptyList(), listOf(command(), command("whoami"))))
        }
        val oldRevision = state.revision
        assertTrue(state.confirmResult(0, oldRevision))
        assertTrue(state.confirmResult(1, oldRevision))
        state.edit(0, "Check processes", "ps", "Read process list.", "Processes.")
        assertTrue(state.revision > oldRevision)
        assertEquals(setOf(1), state.confirmedSteps)
        assertEquals("high", state.plan!!.commands.first().risk)
        assertFalse(state.confirmResult(0, oldRevision))
    }

    @Test fun replacementCannotInheritSuccessFromDifferentCommands() {
        val state = PlanWorkspace().apply { replace(plan()); confirmResult(0, revision) }
        val token = state.touch()
        state.stage(token, plan("whoami"))
        state.accept(token)
        assertTrue(state.confirmedSteps.isEmpty())
    }

    @Test fun hiddenControlsAreRejectedBeforeAnEditChangesState() {
        val state = PlanWorkspace().apply { replace(plan()) }
        val revision = state.revision
        for (text in listOf("id\nwhoami", "id\t", "id\u001b", "id\u202e")) {
            try { state.edit(0, "Edited", text, "Check", "Result"); fail("Control text accepted") }
            catch (_: PlanException) {}
        }
        assertEquals(revision, state.revision)
        assertEquals("id", state.plan!!.commands.single().command)
    }

    @Test fun librarySelectionIsLocalPlanDataAndPreservesConfirmedSteps() {
        val state = PlanWorkspace().apply { replace(plan()); confirmResult(0, revision) }
        state.append(command("whoami"))
        assertEquals(listOf("id", "whoami"), state.plan!!.commands.map { it.command })
        assertEquals(setOf(0), state.confirmedSteps)
        assertNull(state.proposal)
    }

    @Test fun completeGuideAppendsInOrderWithOneRevisionAndNoNewConfirmedResults() {
        val state = PlanWorkspace().apply { replace(plan()); confirmResult(0, revision) }
        val before = state.revision
        state.appendAll(listOf(command("whoami"), command("uname -r")))
        assertEquals(listOf("id", "whoami", "uname -r"), state.plan!!.commands.map { it.command })
        assertEquals(before + 1, state.revision)
        assertEquals(setOf(0), state.confirmedSteps)
    }

    @Test fun invalidOrOversizedGuideCannotPartiallyChangeThePlan() {
        val state = PlanWorkspace().apply { replace(plan()); confirmResult(0, revision) }
        val before = state.revision
        for (items in listOf(listOf(command("whoami"), command("id\nwhoami")), List(40) { command() }, emptyList())) {
            try { state.appendAll(items); fail("Invalid guide accepted") } catch (_: PlanException) {}
            assertEquals(before, state.revision)
            assertEquals(listOf("id"), state.plan!!.commands.map { it.command })
            assertEquals(setOf(0), state.confirmedSteps)
        }
    }

    @Test(expected = PlanException::class) fun localPlanHasSameFortyStepLimit() {
        val state = PlanWorkspace()
        repeat(41) { state.append(command()) }
    }

    @Test fun confirmationRequiresAnExistingStepAndCurrentRevision() {
        val state = PlanWorkspace().apply { replace(plan()) }
        assertFalse(state.confirmResult(1, state.revision))
        assertFalse(state.confirmResult(0, state.revision - 1))
        assertTrue(state.confirmResult(0, state.revision))
    }

    @Test fun removingAStepCannotReuseItsOutcomeOrApprovalRevision() {
        val state = PlanWorkspace().apply { replace(CommandPlan("Checks", emptyList(), emptyList(), listOf(command(), command("whoami")))) }
        val old = state.revision
        state.confirmResult(0, old)
        state.confirmResult(1, old)
        state.remove(0)
        assertEquals("whoami", state.plan!!.commands.single().command)
        assertEquals(setOf(0), state.confirmedSteps)
        assertFalse(state.confirmResult(0, old))
    }

    @Test fun feedbackCarriesTheActualRemainingAndHumanConfirmedCommands() {
        val request = PlanFeedbackRequest.goal("Inspect services", command("systemctl --failed"), setOf(0),
            "Permission denied", listOf(command("id"), command("systemctl --failed")))
        val context = org.json.JSONObject(request.substringAfter('\n'))
        assertEquals("id", context.getJSONArray("human_confirmed_commands").getString(0))
        assertEquals(1, context.getJSONArray("human_confirmed_commands").length())
        assertEquals("systemctl --failed", context.getJSONArray("remaining_plan_commands").getString(0))
        assertEquals(1, context.getJSONArray("remaining_plan_commands").length())
    }

    @Test fun revisionRequestContainsUntrustedEvidenceAndHumanContext() {
        val request = PlanFeedbackRequest.goal("Inspect services", command("systemctl --failed"), setOf(0), "Permission denied")
        assertTrue(request.contains("UNTRUSTED EVIDENCE"))
        assertTrue(request.contains("Permission denied"))
        assertTrue(request.contains("human_confirmed_step_numbers"))
        val api = PlanCodec.request(request, "Ubuntu Linux / Bash", PlanCodec.DEFAULT_MODEL)
        assertFalse(api.getBoolean("store"))
        assertEquals("none", api.getString("tool_choice"))
        assertEquals(0, api.getJSONArray("tools").length())
    }

    @Test fun largePlanRevisionKeepsAllFortyCommandsWithoutTruncation() {
        val commands = (1..40).map { command("printf 'check-${it}-" + "x".repeat(80) + "'") }
        val request = PlanFeedbackRequest.goal("Review system security", commands.first(), emptySet(), "A service failed", commands)
        assertTrue(request.length > 2000)
        assertEquals(40, org.json.JSONObject(request.substringAfter('\n')).getJSONArray("remaining_plan_commands").length())
        assertNotNull(PlanCodec.request(request, "Ubuntu Linux / Bash", PlanCodec.DEFAULT_MODEL))
    }

    @Test fun editingAndRemovingCommandsRetainTheRoadmap() {
        val roadmap = listOf(PlanPhase("Recovery", "Avoid lockout", listOf("Verify a second session")))
        val state = PlanWorkspace().apply { replace(CommandPlan("Checks", emptyList(), emptyList(), listOf(command(), command("whoami")), roadmap)) }
        state.edit(0, "Identity", "id", "Check identity", "Account information")
        assertSame(roadmap, state.plan!!.roadmap)
        state.remove(0)
        assertSame(roadmap, state.plan!!.roadmap)
    }

    @Test(expected = PlanException::class) fun oversizedFeedbackIsNotSilentlyTruncated() {
        PlanFeedbackRequest.goal("Inspect services", command(), emptySet(), "x".repeat(8001))
    }

    @Test fun unclearPhotoCanOnlyStageQuestionsAndNeverConfirmsAnOutcome() {
        val state = PlanWorkspace().apply { replace(plan()) }
        val value = PhotoAnalysis("Unreadable screen", emptyList(), listOf("Can you retake the photo?"), emptyList())
        val token = state.touch()
        assertTrue(state.stage(token, PlanFeedbackRequest.fromPhoto(value)))
        assertTrue(state.proposal!!.commands.isEmpty())
        assertEquals("id", state.plan!!.commands.single().command)
        assertTrue(state.confirmedSteps.isEmpty())
    }
}
