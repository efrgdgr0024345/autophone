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
            .put("revision_needed", false)
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
}
