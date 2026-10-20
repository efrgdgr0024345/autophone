package com.blackcat.remote

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlanResponseStreamTest {
    private fun proposal() = JSONObject().put("explanation", "Inspect the host first.\nExplain clearly.")
        .put("roadmap", JSONArray().put(JSONObject().put("title", "Discover").put("purpose", "Read basic facts.").put("checks", JSONArray())))
        .put("assumptions", JSONArray()).put("decision", JSONObject().put("mode", "commands").put("questions", JSONArray())
            .put("commands", JSONArray().put(JSONObject().put("title", "Check identity").put("command", "id")
                .put("explanation", "Read the account.").put("risk", "low").put("requires_admin", false)
                .put("expected_result", "Account facts.").put("warnings", JSONArray()))))
    private fun response(text: String) = JSONObject().put("status", "completed").put("output", JSONArray().put(
        JSONObject().put("type", "message").put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", text)))))
    private fun event(type: String, vararg fields: Pair<String, Any>) = "data: " + JSONObject().put("type", type).apply {
        fields.forEach { put(it.first, it.second) }
    }.toString() + "\r\n\r\n"
    private fun delta(text: String, index: Int = 0) = event("response.output_text.delta", "output_index" to index, "content_index" to 0, "delta" to text)
    private fun completion(text: String) = event("response.completed", "response" to response(text))
    private fun bytes(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
    private fun rejected(wire: String) = runBlocking {
        try { PlanResponseStream.read(bytes(wire)) {}; fail("Invalid stream accepted") }
        catch (e: PlanException) { assertFalse(e.message.orEmpty().contains("PRIVATE")) }
    }

    @Test fun fragmentedUtf8SseProducesDraftBeforeStrictFinalPlan() = runBlocking {
        val text = proposal().put("explanation", "Read café’s status 🐈.").toString()
        val parts = text.codePoints().toArray().toList().chunked(7).map { String(it.toIntArray(), 0, it.size) }
        val wire = ": keepalive\r\n\r\n" + parts.joinToString("") { delta(it) } +
            event("response.output_text.done", "text" to text) + completion(text)
        val drafts = mutableListOf<PlanStreamDraft>()
        val fragmentedBytes = object : ByteArrayInputStream(wire.toByteArray(Charsets.UTF_8)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, minOf(1, length))
        }
        val plan = PlanResponseStream.read(fragmentedBytes) { drafts += it }
        assertEquals("id", plan.commands.single().command)
        assertTrue(drafts.isNotEmpty())
        assertEquals("Read café’s status 🐈.", drafts.last().explanation)
        assertEquals("id", drafts.last().commands.single().command)
    }
    @Test fun prefixesKeepProseCodeAndPhasesSeparateWithoutRepairingExecutablePlans() {
        assertTrue(PlanDraftPreview.read(proposal().toString()).explanation.contains("\nExplain clearly."))
        val prefix = """{"explanation":"Friendly \\""" // unfinished escape stays display-only
        assertTrue(PlanDraftPreview.read(prefix).commands.isEmpty())
        val text = """{"explanation":"Say \"hello\".","roadmap":[{"title":"Read","purpose":"Learn"}],"decision":{"mode":"commands","commands":[{"title":"Who","command":"printf 'a'","explanation":"Read \\ output"},{"title":"Next","command":"un"""
        val draft = PlanDraftPreview.read(text)
        assertEquals("Say \"hello\".", draft.explanation)
        assertEquals("Read", draft.phases.single().first)
        assertEquals("printf 'a'", draft.commands.first().command)
        assertEquals("un", draft.commands.last().command)
        assertTrue(draft.questions.isEmpty())
    }
    @Test fun eofFailureRefusalAndMismatchedCompletionCannotPublish() {
        val text = proposal().toString()
        rejected(delta(text))
        rejected(delta("{") + completion(text))
        rejected(delta(text) + event("response.failed", "message" to "PRIVATE"))
        rejected(delta(text) + event("response.incomplete"))
        rejected(event("error", "message" to "PRIVATE"))
        rejected(event("response.refusal.delta", "delta" to "PRIVATE"))
        rejected(delta(text) + event("response.output_text.done", "text" to "different"))
        rejected(delta("a", 0) + delta("b", 1) + completion(text))
    }
    @Test fun malformedAndMixedDecisionsStillUseTheProductionValidation() {
        rejected("data: NOT JSON\n\n")
        val mixed = proposal().apply { getJSONObject("decision").put("questions", JSONArray(listOf("Question?"))) }.toString()
        rejected(delta(mixed) + completion(mixed))
        val oversized = "x".repeat(PlanResponseStream.MAX_TEXT + 1)
        rejected(delta(oversized))
        rejected("data: " + "x".repeat(600001))
    }
    @Test fun reasoningEventsAreNeverPresentedAsUserFacingThoughts() = runBlocking {
        val text = proposal().toString()
        val previews = mutableListOf<PlanStreamDraft>()
        PlanResponseStream.read(bytes(event("response.reasoning_text.delta", "delta" to "PRIVATE") + delta(text) + completion(text))) { previews += it }
        assertTrue(previews.none { it.toString().contains("PRIVATE") })
    }
    @Test fun oversizedDraftFieldsCannotFloodThePhoneViewOrPassFinalValidation() {
        val root = proposal()
        val command = root.getJSONObject("decision").getJSONArray("commands").getJSONObject(0)
        command.put("title", "x".repeat(5000)).put("command", "x".repeat(5000))
        val draft = PlanDraftPreview.read(root.toString())
        assertEquals(160, draft.commands.single().title.length)
        assertEquals(512, draft.commands.single().command.length)
        rejected(delta(root.toString()) + completion(root.toString()))
    }
    @Test fun fortyStepPlansHaveRoomForTokenEventMetadataWithoutRelaxingTextBounds() = runBlocking {
        val root = proposal()
        val commands = JSONArray()
        repeat(40) { i -> commands.put(JSONObject(root.getJSONObject("decision").getJSONArray("commands").getJSONObject(0).toString())
            .put("title", "Inspect $i").put("explanation", "Read-only context. ".repeat(40)).put("expected_result", "Visible facts. ".repeat(20))) }
        root.getJSONObject("decision").put("commands", commands)
        val text = root.toString()
        val wire = text.chunked(4).joinToString("") { chunk -> event("response.output_text.delta", "output_index" to 0,
            "content_index" to 0, "item_id" to "msg_${"a".repeat(200)}", "delta" to chunk) } + completion(text)
        assertTrue(wire.length > 3000000)
        assertTrue(text.length < PlanResponseStream.MAX_TEXT)
        assertEquals(40, PlanResponseStream.read(bytes(wire)) {}.commands.size)
    }
    @Test fun deeplyNestedOrUnboundedKeyPrefixesStopBeforeRecursiveParsingOrPathGrowth() {
        val prefix = """{"explanation":"Do not keep invalid drafts","unknown":""" + "[".repeat(4096)
        assertTrue(PlanDraftPreview.read(prefix).explanation.isEmpty())
        val longKey = """{"explanation":"Do not keep invalid drafts","""" + "x".repeat(1000) + "\":\"value\"}"
        assertTrue(PlanDraftPreview.read(longKey).explanation.isEmpty())
        val text = proposal().toString()
        val outer = "data: {\"type\":\"response.in_progress\",\"unknown\":" + "[".repeat(64) + "0" + "]".repeat(64) + "}\n\n"
        rejected(outer + delta(text) + completion(text))
        val nestedPlan = "{\"unknown\":" + "[".repeat(4096) + "0" + "]".repeat(4096) + "}"
        rejected(delta(nestedPlan) + completion(nestedPlan))
    }
}
