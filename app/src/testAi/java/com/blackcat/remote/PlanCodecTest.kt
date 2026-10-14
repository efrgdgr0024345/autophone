package com.blackcat.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlanCodecTest {
    private fun plan(command:String="id") = JSONObject()
        .put("explanation","Check account.")
        .put("assumptions",JSONArray()).put("roadmap",JSONArray())
        .put("decision",JSONObject().put("mode","commands").put("questions",JSONArray())
        .put("commands",JSONArray().put(JSONObject()
            .put("title","Check identity")
            .put("command",command)
            .put("explanation","Read-only check.")
            .put("risk","low")
            .put("requires_admin",false)
            .put("expected_result","Account information.")
            .put("warnings",JSONArray()))))

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
        val proposal=plan()
        proposal.getJSONObject("decision").put("questions",JSONArray().put("Which OS?"))
        PlanCodec.parsePlan(proposal.toString())
    }
    @Test fun requestHasNoToolsOrStoredState() {
        val r=PlanCodec.request("create alice","Ubuntu Linux Bash",PlanCodec.DEFAULT_MODEL)
        assertFalse(r.getBoolean("store"))
        assertEquals(0,r.getJSONArray("tools").length())
        assertEquals("none",r.getString("tool_choice"))
        assertTrue(r.getJSONObject("text").getJSONObject("format").getBoolean("strict"))
        assertEquals("gpt-6.1-sol",r.getString("model"))
        assertEquals("low",r.getJSONObject("reasoning").getString("effort"))
        assertEquals(16384,r.getInt("max_output_tokens"))
    }
    @Test fun endpointIsFixedHttps() { assertEquals("https://api.openai.com/v1/responses",OpenAiPlanner.ENDPOINT) }
    @Test fun keyCannotInjectHeader() { assertFalse(OpenAiPlanner.validKey("sk-"+"x".repeat(30)+"\r\nX:y")) }

    @Test fun clarificationBranchReturnsQuestionsWithoutCommands() {
        val proposal=plan()
        proposal.put("decision",JSONObject().put("mode","clarify")
            .put("questions",JSONArray().put("Which account should be inspected?"))
            .put("commands",JSONArray()))
        val result=PlanCodec.parsePlan(proposal.toString())
        assertTrue(result.commands.isEmpty())
        assertEquals(1,result.questions.size)
    }

    @Test fun detailedRoadmapSurvivesClarificationWithoutInventingExecutableChanges() {
        val proposal = plan().put("roadmap", JSONArray().put(JSONObject()
            .put("title", "Protect remote access").put("purpose", "Avoid lockout while strengthening SSH.")
            .put("checks", JSONArray().put("Back up configuration and verify a second session before changing access."))))
        proposal.put("decision", JSONObject().put("mode", "clarify")
            .put("questions", JSONArray().put("Which SSH port and recovery path must remain available?"))
            .put("commands", JSONArray()))
        val result = PlanCodec.parsePlan(proposal.toString())
        assertEquals("Protect remote access", result.roadmap.single().title)
        assertTrue(result.roadmap.single().checks.single().contains("second session"))
        assertTrue(result.commands.isEmpty())
    }

    @Test fun largePlanKeepsEveryOrderedStepAndDetailedInstructions() {
        val proposal = plan()
        val array = proposal.getJSONObject("decision").getJSONArray("commands")
        val item = array.getJSONObject(0)
        repeat(39) { array.put(JSONObject(item.toString()).put("title", "Check ${it + 2}")) }
        assertEquals(40, PlanCodec.parsePlan(proposal.toString()).commands.size)
        val instructions = PlanCodec.request("Lock down the system from hackers", "Ubuntu Linux / Bash", PlanCodec.DEFAULT_MODEL).getString("instructions")
        for (expected in listOf("backups", "rollback", "firewall", "logging", "lockout", "12-30", "complete roadmap"))
            assertTrue(expected, instructions.contains(expected))
    }

    @Test(expected = PlanException::class) fun roadmapCannotInjectExecutionFields() {
        val proposal = plan().put("roadmap", JSONArray().put(JSONObject()
            .put("title", "Check").put("purpose", "Read-only").put("checks", JSONArray()).put("run", true)))
        PlanCodec.parsePlan(proposal.toString())
    }

    @Test(expected=PlanException::class) fun emptyClarificationBranchRejected() {
        val proposal=plan()
        proposal.put("decision",JSONObject().put("mode","clarify")
            .put("questions",JSONArray()).put("commands",JSONArray()))
        PlanCodec.parsePlan(proposal.toString())
    }

    @Test(expected=PlanException::class) fun unexpectedDecisionModeRejected() {
        val proposal=plan()
        proposal.getJSONObject("decision").put("mode","execute")
        PlanCodec.parsePlan(proposal.toString())
    }

    @Test(expected=PlanException::class) fun commandsBeyondFortyRejected() {
        val proposal=plan()
        val commands=proposal.getJSONObject("decision").getJSONArray("commands")
        val item=commands.getJSONObject(0)
        repeat(40) { commands.put(item) }
        PlanCodec.parsePlan(proposal.toString())
    }

    @Test(expected=PlanException::class) fun extraRootFieldRejected() {
        PlanCodec.parsePlan(plan().put("run_automatically",true).toString())
    }

    @Test(expected=PlanException::class) fun extraCommandFieldRejected() {
        val proposal=plan()
        proposal.getJSONObject("decision").getJSONArray("commands").getJSONObject(0).put("press_enter",true)
        PlanCodec.parsePlan(proposal.toString())
    }

    @Test(expected=PlanException::class) fun unicodeControlRejected() {
        PlanCodec.parsePlan(plan("id\u202e").toString())
    }

    @Test(expected=PlanException::class) fun commandInProseCannotBypassControlPolicy() {
        PlanCodec.parsePlan(plan("```bash\nid\n```").toString())
    }

    @Test fun responseLimitHasUsefulFeedbackWithoutAcceptingPartialPlan() {
        try {
            PlanCodec.parseResponse(response(plan()).put("status","incomplete")
                .put("incomplete_details",JSONObject().put("reason","max_output_tokens")).toString())
            fail("Partial output must not be accepted")
        } catch(e:PlanException) {
            assertTrue(e.message.orEmpty().contains("smaller task"))
            assertTrue(e.message.orEmpty().contains("Nothing was typed"))
        }
    }

    @Test fun schemaMakesQuestionsAndCommandsMutuallyExclusive() {
        val schema=PlanCodec.schema()
        assertEquals("object",schema.getString("type"))
        assertFalse(schema.has("anyOf"))
        val branches=schema.getJSONObject("properties").getJSONObject("decision").getJSONArray("anyOf")
        val clarification=branches.getJSONObject(0).getJSONObject("properties")
        assertEquals(1,clarification.getJSONObject("questions").getInt("minItems"))
        assertEquals(0,clarification.getJSONObject("commands").getInt("maxItems"))
        val commands=branches.getJSONObject(1).getJSONObject("properties")
        assertEquals(0,commands.getJSONObject("questions").getInt("maxItems"))
        assertEquals(40,commands.getJSONObject("commands").getInt("maxItems"))
    }

    @Test(expected=IllegalArgumentException::class) fun unsupportedModelCannotReachRequest() {
        PlanCodec.request("Check account","Ubuntu Linux / Bash","unreviewed-model")
    }

    @Test fun quotaAndRateLimitFeedbackAreDistinct() {
        assertTrue(OpenAiPlanner.failureMessage(429,"insufficient_quota").contains("billing"))
        assertTrue(OpenAiPlanner.failureMessage(429,"rate_limit_exceeded").contains("rate limit"))
        assertFalse(OpenAiPlanner.failureMessage(403,"").contains("incorrect"))
    }
}
