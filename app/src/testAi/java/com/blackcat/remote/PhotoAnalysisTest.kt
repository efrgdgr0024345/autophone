package com.blackcat.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhotoAnalysisTest {
    private fun command(command: String = "id") = JSONObject()
        .put("title", "Check identity")
        .put("command", command)
        .put("explanation", "Read-only check")
        .put("risk", "low")
        .put("requires_admin", false)
        .put("expected_result", "User identity")
        .put("warnings", JSONArray())

    @Test fun parsesReadableAnalysis() {
        val json = JSONObject()
            .put("summary", "A shell prompt is visible.")
            .put("observations", JSONArray(listOf("Prompt appears ready.")))
            .put("questions", JSONArray())
            .put("commands", JSONArray().put(command()))
        val result = PhotoAnalysisCodec.parseAnalysis(json.toString())
        assertEquals("A shell prompt is visible.", result.summary)
        assertEquals(1, result.commands.size)
        assertEquals("id", result.commands.single().command)
    }

    @Test fun allowsUnreadableImageToAskWithoutCommands() {
        val json = JSONObject()
            .put("summary", "The image is not readable enough.")
            .put("observations", JSONArray())
            .put("questions", JSONArray(listOf("Please retake the terminal closer.")))
            .put("commands", JSONArray())
        val result = PhotoAnalysisCodec.parseAnalysis(json.toString())
        assertTrue(result.commands.isEmpty())
        assertEquals(1, result.questions.size)
    }

    @Test(expected = PlanException::class)
    fun rejectsQuestionsMixedWithCommands() {
        val json = JSONObject()
            .put("summary", "Uncertain")
            .put("observations", JSONArray())
            .put("questions", JSONArray(listOf("Which host is this?")))
            .put("commands", JSONArray().put(command()))
        PhotoAnalysisCodec.parseAnalysis(json.toString())
    }

    @Test(expected = PlanException::class)
    fun rejectsControlCharactersInCommand() {
        val json = JSONObject()
            .put("summary", "Prompt")
            .put("observations", JSONArray())
            .put("questions", JSONArray())
            .put("commands", JSONArray().put(command("id\nwhoami")))
        PhotoAnalysisCodec.parseAnalysis(json.toString())
    }

    @Test fun requestContainsReviewedImageAndNoTools() {
        val request = PhotoAnalysisCodec.request(
            "Understand this terminal.",
            "Ubuntu Linux / Bash",
            PlanCodec.DEFAULT_MODEL,
            byteArrayOf(1, 2, 3)
        )
        assertFalse(request.getBoolean("store"))
        assertEquals("none", request.getString("tool_choice"))
        assertEquals(0, request.getJSONArray("tools").length())
        val content = request.getJSONArray("input").getJSONObject(0).getJSONArray("content")
        val image = content.getJSONObject(1)
        assertEquals("input_image", image.getString("type"))
        assertEquals("high", image.getString("detail"))
        assertTrue(image.getString("image_url").startsWith("data:image/jpeg;base64,"))
    }
}
