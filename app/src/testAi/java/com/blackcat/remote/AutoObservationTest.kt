package com.blackcat.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AutoObservationTest {
    private fun fixture(command: String = "id", terminal: String = "shell_prompt", outcome: String = "appears_complete") =
        JSONObject().put("observed_command", command).put("terminal_state", terminal).put("outcome", outcome)
            .put("explanation", "The photo appears to show account details and a fresh prompt.")
            .put("visible_evidence", JSONArray().put("uid=1000 and a fresh prompt are visible"))
            .put("revision_needed", false).put("page_marker", "").put("page_state", "none").put("page_text", "").put("exit_code", "")
    @Test fun exactCommandAndVisibleEvidenceRequiredForAdvance() {
        assertTrue(AutoObservationCodec.parse(fixture().toString()).allowsNext("id"))
        assertFalse(AutoObservationCodec.parse(fixture("whoami").toString()).allowsNext("id"))
        assertFalse(AutoObservationCodec.parse(fixture().put("visible_evidence", JSONArray()).toString()).allowsNext("id"))
        assertFalse(AutoObservationCodec.parse(fixture().put("revision_needed", true).toString()).allowsNext("id"))
    }
    @Test fun passwordBusyErrorAndUnclearScreensAlwaysPause() {
        for (terminal in listOf("password_prompt", "busy", "unclear"))
            assertFalse(AutoObservationCodec.parse(fixture(terminal = terminal).toString()).allowsNext("id"))
        for (outcome in listOf("error", "unclear"))
            assertFalse(AutoObservationCodec.parse(fixture(outcome = outcome).toString()).allowsNext("id"))
    }
    @Test fun errorCannotAuthorizeARevisionFromPasswordBusyOrUnclearInput() {
        for (terminal in listOf("password_prompt", "busy", "unclear"))
            assertFalse(AutoObservationCodec.parse(fixture(terminal = terminal, outcome = "error")
                .put("revision_needed", true).toString()).canAutoRevise("id"))
    }
    @Test fun revisionRequiresTheActualCommandAndAFreshPromptWithEvidence() {
        assertTrue(AutoObservationCodec.parse(fixture(outcome = "error").toString()).canAutoRevise("id"))
        assertFalse(AutoObservationCodec.parse(fixture("whoami", outcome = "error").toString()).canAutoRevise("id"))
        assertFalse(AutoObservationCodec.parse(fixture(outcome = "error").put("visible_evidence", JSONArray()).toString()).canAutoRevise("id"))
    }
    @Test(expected = PlanException::class) fun unknownStateRejected() { AutoObservationCodec.parse(fixture(terminal = "execute").toString()) }
    @Test(expected = PlanException::class) fun executionFieldsRejected() { AutoObservationCodec.parse(fixture().put("press_enter", true).toString()) }
    @Test(expected = PlanException::class) fun coercibleBooleanRejected() { AutoObservationCodec.parse(fixture().put("revision_needed", "true").toString()) }
    @Test fun requestUsesImageAndNoToolsOrStoredState() {
        val step = SuggestedCommand("Check identity", "id", "Understand the account.", "low", false, "Account details.", emptyList())
        val r = AutoObservationCodec.request("Review security", step, "Ubuntu Linux / Bash", PlanCodec.DEFAULT_MODEL, byteArrayOf(1, 2))
        assertFalse(r.getBoolean("store"))
        assertEquals("none", r.getString("tool_choice"))
        assertEquals(0, r.getJSONArray("tools").length())
        assertTrue(r.getString("instructions").contains("UNTRUSTED EVIDENCE"))
        assertTrue(r.toString().contains("input_image"))
        assertTrue(r.getJSONObject("text").getJSONObject("format").getBoolean("strict"))
    }
    @Test fun speechExplainsPurposeRatherThanReadingShellSyntax() {
        val step = SuggestedCommand("Inspect network", "ss -lntup", "Let's see which services are listening, so we can decide what needs protecting.",
            "low", false, "Listening ports and processes", emptyList())
        val text = NarrationText.step(step)
        assertTrue(text.contains("what needs protecting"))
        assertFalse(text.contains(step.command))
        val r = OpenAiSpeechCodec.request(text)
        assertEquals("gpt-4o-mini-tts", r.getString("model"))
        assertEquals("pcm", r.getString("response_format"))
        assertEquals("https://api.openai.com/v1/audio/speech", OpenAiSpeechCodec.ENDPOINT)
        assertFalse(r.has("tools"))
    }
    @Test fun exactPrintedCaptureFooterFromTheLiveProviderHasACanonicalPrefix() {
        val prefix = "BC1234567890abcdef12345678_CAP"
        for ((state, suffix) in listOf("captured" to "DONE", "truncated" to "TRUNCATED")) {
            val value = fixture(outcome = "unclear").put("page_state", state).put("exit_code", "0")
                .put("page_marker", "${prefix}_0_$suffix")
            assertEquals(prefix, AutoObservationCodec.parse(value.toString()).pageMarker)
        }
    }
    @Test fun canonicalPrefixesRemainUnchangedAndWrongNoncesRemainDifferent() {
        val prefix = "BC1234567890abcdef12345678_CAP"
        val value = fixture().put("page_state", "captured").put("exit_code", "0").put("page_marker", prefix)
        assertEquals(prefix, AutoObservationCodec.parse(value.toString()).pageMarker)
        val other = "BCabcdef1234567890abcdef12_CAP"
        value.put("page_marker", "${other}_0_DONE")
        assertNotEquals(prefix, AutoObservationCodec.parse(value.toString()).pageMarker)
    }
    @Test fun inconsistentExitCodeOrStateIsNeverNormalizedIntoAnAcceptedMarker() {
        val prefix = "BC1234567890abcdef12345678_CAP"
        for ((raw, state, code) in listOf(Triple("${prefix}_1_DONE", "captured", "0"),
            Triple("${prefix}_0_TRUNCATED", "captured", "0"), Triple("${prefix}_0_DONE", "truncated", "0"),
            Triple("${prefix}_0_DONE_EXTRA", "captured", "0"), Triple("BC_wrong_CAP_0_DONE", "captured", "0"))) {
            val value = fixture().put("page_state", state).put("exit_code", code).put("page_marker", raw)
            assertNotEquals(raw, prefix)
            assertNotEquals(prefix, AutoObservationCodec.parse(value.toString()).pageMarker)
        }
    }
    @Test fun printedPageAndCleanupFootersRequireTheirExactSeparateStateAndCode() {
        val page = "BC1234567890abcdef12345678_P1_1_8"
        val clean = "BC1234567890abcdef12345678_CLEAN"
        for ((raw, state, expected) in listOf(Triple("${page}_END_2_MORE", "more", page),
            Triple("${page}_END_2_DONE", "unreadable", page), Triple("${clean}_2", "cleaned", clean))) {
            val value = fixture().put("page_state", state).put("exit_code", "2").put("page_marker", raw)
            assertEquals(expected, AutoObservationCodec.parse(value.toString()).pageMarker)
        }
        val wrong = fixture().put("page_state", "more").put("exit_code", "0").put("page_marker", "${page}_END_2_MORE")
        assertNotEquals(page, AutoObservationCodec.parse(wrong.toString()).pageMarker)
    }
}
