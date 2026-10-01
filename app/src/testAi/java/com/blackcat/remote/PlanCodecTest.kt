package com.blackcat.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlanCodecTest {
    private fun plan(command:String="id") = JSONObject()
        .put("explanation","Check account.")
        .put("assumptions",JSONArray())
        .put("questions",JSONArray())
        .put("commands",JSONArray().put(JSONObject()
            .put("title","Check identity")
            .put("command",command)
            .put("explanation","Read-only check.")
            .put("risk","low")
            .put("requires_admin",false)
            .put("expected_result","Account information.")
            .put("warnings",JSONArray())))

    private fun response(p:JSONObject) = JSONObject().put("status","completed")
        .put("output",JSONArray().put(JSONObject().put("type","message")
            .put("content",JSONArray().put(JSONObject().put("type","output_text").put("text",p.toString())))))

    @Test fun completedResponseParses() {
        assertEquals("id",PlanCodec.parseResponse(response(plan()).toString()).commands.single().command)
    }
    @Test(expected=PlanException::class) fun incompleteRejected() {
        PlanCodec.parseResponse(response(plan()).put("status","incomplete").toString())
    }
    @Test(expected=PlanException::class) fun multilineRejected() { PlanCodec.parsePlan(plan("id\nwhoami").toString()) }
    @Test(expected=PlanException::class) fun questionsAndCommandsCannotMix() {
        PlanCodec.parsePlan(plan().put("questions",JSONArray().put("Which OS?")).toString())
    }
    @Test fun requestHasNoToolsOrStoredState() {
        val r=PlanCodec.request("create alice","Ubuntu Linux Bash",PlanCodec.DEFAULT_MODEL)
        assertFalse(r.getBoolean("store"))
        assertEquals(0,r.getJSONArray("tools").length())
        assertEquals("none",r.getString("tool_choice"))
        assertTrue(r.getJSONObject("text").getJSONObject("format").getBoolean("strict"))
    }
    @Test fun endpointIsFixedHttps() { assertEquals("https://api.openai.com/v1/responses",OpenAiPlanner.ENDPOINT) }
    @Test fun keyCannotInjectHeader() { assertFalse(OpenAiPlanner.validKey("sk-"+"x".repeat(30)+"\r\nX:y")) }
}
