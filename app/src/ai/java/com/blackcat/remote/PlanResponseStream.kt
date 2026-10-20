package com.blackcat.remote

import java.io.InputStream
import java.io.InputStreamReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** Display-only draft. It cannot be passed to a sender or a workspace. */
internal data class PlanStreamDraft(val explanation: String = "", val phases: List<Pair<String, String>> = emptyList(),
    val commands: List<DraftCommand> = emptyList(), val questions: List<String> = emptyList())
internal data class DraftCommand(val title: String, val command: String, val explanation: String)

/** Bounded Responses API SSE reader. Only a completed, independently parsed response can return a plan. */
internal object PlanResponseStream {
    const val MAX_TEXT = 262144
    private const val MAX_EVENT = 600000
    // SSE repeats IDs/metadata for each token. Preserve room for a full 16k-token
    // detailed plan while bounding retained text/event sizes independently.
    private const val MAX_WIRE = 12000000

    suspend fun read(input: InputStream, onDraft: suspend (PlanStreamDraft) -> Unit): CommandPlan {
        val reader = InputStreamReader(input, Charsets.UTF_8).buffered()
        val data = StringBuilder()
        val text = StringBuilder()
        var wire = 0
        var events = 0
        var outputIndex: Int? = null
        var contentIndex: Int? = null
        var completed: CommandPlan? = null
        var lastPreview = 0L
        suspend fun dispatch() {
            if (data.isEmpty()) return
            if (++events > 20000) throw PlanException("API stream exceeded its limit. Your plan was kept.")
            val payload = data.toString()
            if (!withinDepth(payload, 16)) throw PlanException("API stream nesting exceeded its limit. Your plan was kept.")
            val event = try { JSONObject(payload) } catch (_: Exception) {
                throw PlanException("Invalid API stream. Your plan was kept; nothing was typed.")
            }
            data.setLength(0)
            when (event.optString("type")) {
                "response.output_text.delta" -> {
                    val oi = event.getInt("output_index")
                    val ci = event.getInt("content_index")
                    if (outputIndex != null && (oi != outputIndex || ci != contentIndex))
                        throw PlanException("Expected a single streamed proposal. Your plan was kept.")
                    outputIndex = oi; contentIndex = ci
                    val delta = event.getString("delta")
                    if (text.length + delta.length > MAX_TEXT) throw PlanException("API response too large.")
                    text.append(delta)
                    val now = System.nanoTime()
                    if (now - lastPreview >= 150000000L) {
                        onDraft(PlanDraftPreview.read(text.toString())); lastPreview = now
                    }
                }
                "response.output_text.done" -> {
                    if (event.getString("text") != text.toString()) throw PlanException("Stream text did not match. Your plan was kept.")
                }
                "response.refusal.delta", "response.refusal.done" -> throw PlanException("The model declined this request. Nothing was sent.")
                "response.completed" -> {
                    val response = event.getJSONObject("response")
                    // The final response is the authority, never a repaired prefix or a delta.
                    val output = response.getJSONArray("output")
                    val finalText = buildString {
                        for (i in 0 until output.length()) {
                            val item = output.getJSONObject(i)
                            if (item.optString("type") != "message") continue
                            val parts = item.getJSONArray("content")
                            for (j in 0 until parts.length()) {
                                val part = parts.getJSONObject(j)
                                if (part.optString("type") == "output_text") append(part.getString("text"))
                            }
                        }
                    }
                    if (finalText != text.toString()) throw PlanException("Incomplete stream. Your plan was kept; nothing was typed.")
                    if (!withinDepth(finalText, 8)) throw PlanException("Plan nesting exceeded its limit. Your plan was kept.")
                    val plan = PlanCodec.parseResponse(response.toString())
                    onDraft(PlanDraftPreview.read(finalText))
                    completed = plan
                }
                "response.failed", "response.incomplete", "error" ->
                    throw PlanException("OpenAI could not complete the streamed plan. Your current plan was kept; try again manually.")
                // Lifecycle and reasoning events contain no displayable plan content.
            }
        }
        try {
            while (completed == null) {
                currentCoroutineContext().ensureActive()
                val line = StringBuilder()
                var eof = false
                while (true) {
                    val c = reader.read()
                    if (c == -1) { eof = true; break }
                    if (++wire > MAX_WIRE) throw PlanException("API stream exceeded its limit.")
                    if (c == 10) break
                    if (line.length >= MAX_EVENT) throw PlanException("API stream exceeded its limit.")
                    line.append(c.toChar())
                    if (line.length % 4096 == 0) currentCoroutineContext().ensureActive()
                }
                val value = line.toString().removeSuffix("\r")
                if (value.isEmpty()) dispatch()
                else if (value.startsWith("data:")) {
                    val payload = value.substring(5).removePrefix(" ")
                    if (data.length + payload.length + 1 > MAX_EVENT) throw PlanException("API stream event too large.")
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(payload)
                }
                if (completed != null) break
                if (eof) { dispatch(); break }
            }
            currentCoroutineContext().ensureActive()
            return completed ?: throw PlanException("Stream ended before the plan was complete. Your current plan was kept.")
        } catch (e: PlanException) { throw e }
          catch (_: org.json.JSONException) { throw PlanException("Invalid API stream. Your plan was kept; nothing was typed.") }
        finally { text.setLength(0); data.setLength(0) }
    }

    /** Lexical depth bound before JSONObject recursion; braces inside strings do not count. */
    private fun withinDepth(value: String, maximum: Int): Boolean {
        var depth = 0; var quoted = false; var escaped = false
        for (c in value) {
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; if (depth > maximum) return false }
                '}', ']' -> depth--
            }
        }
        return true
    }
}

/** Tokenizes an unfinished JSON prefix for presentation only, without inventing missing commands. */
internal object PlanDraftPreview {
    private val limits = buildMap {
        put("explanation", 6000)
        repeat(16) { put("roadmap[$it].title", 160); put("roadmap[$it].purpose", 1500) }
        repeat(PlanCodec.MAX_COMMANDS) {
            put("decision.commands[$it].title", 160); put("decision.commands[$it].command", 512)
            put("decision.commands[$it].explanation", 2000)
        }
        repeat(8) { put("decision.questions[$it]", 1000) }
    }
    private data class Frame(val array: Boolean, val path: String, var key: String? = null, var index: Int = 0)
    fun read(prefix: String): PlanStreamDraft {
        if (prefix.length > PlanResponseStream.MAX_TEXT) return PlanStreamDraft()
        val stack = mutableListOf<Frame>()
        val values = mutableMapOf<String, String>()
        var i = 0
        fun path(): String {
            val parent = stack.lastOrNull() ?: return ""
            return if (parent.array) "${parent.path}[${parent.index}]" else
                if (parent.path.isEmpty()) parent.key.orEmpty() else "${parent.path}.${parent.key.orEmpty()}"
        }
        fun consumed() { stack.lastOrNull()?.let { if (it.array) it.index++ else it.key = null } }
        while (i < prefix.length) {
            when (val char = prefix[i++]) {
                '{', '[' -> {
                    if (stack.size >= 8) return PlanStreamDraft()
                    stack.add(Frame(char == '[', path()))
                }
                '}', ']' -> { if (stack.isEmpty()) return PlanStreamDraft(); stack.removeAt(stack.lastIndex); consumed() }
                '"' -> {
                    val value = StringBuilder()
                    while (i < prefix.length) {
                        val c = prefix[i++]
                        if (c == '"') break
                        if (c == '\\') {
                            if (i >= prefix.length) break
                            when (val escape = prefix[i++]) {
                                '"', '\\', '/' -> value.append(escape)
                                'n', 'r' -> value.append('\n')
                                't' -> value.append(' ')
                                'b', 'f' -> Unit
                                'u' -> {
                                    if (i + 4 > prefix.length) { i = prefix.length; break }
                                    prefix.substring(i, i + 4).toIntOrNull(16)?.let { value.append(it.toChar()) }; i += 4
                                }
                            }
                        } else if (!c.isISOControl()) value.append(c)
                    }
                    val parent = stack.lastOrNull() ?: continue
                    if (!parent.array && parent.key == null) {
                        if (value.length > 64) return PlanStreamDraft()
                        parent.key = value.toString()
                    } else {
                        val field = path()
                        limits[field]?.let { values[field] = value.toString().take(it) }
                        consumed()
                    }
                }
                ':', ',', ' ', '\r', '\n', '\t' -> Unit
                else -> { while (i < prefix.length && prefix[i] !in ",]} \n\r\t") i++; consumed() }
            }
        }
        val phases = (0 until 16).mapNotNull { n ->
            val title = values["roadmap[$n].title"] ?: return@mapNotNull null
            title.take(160) to values["roadmap[$n].purpose"].orEmpty().take(1500)
        }
        val commands = (0 until PlanCodec.MAX_COMMANDS).mapNotNull { n ->
            val base = "decision.commands[$n]"
            if (values.keys.none { it.startsWith("$base.") }) return@mapNotNull null
            DraftCommand(values["$base.title"].orEmpty().take(160), values["$base.command"].orEmpty().take(512), values["$base.explanation"].orEmpty().take(2000))
        }
        return PlanStreamDraft(values["explanation"].orEmpty().take(6000), phases, commands,
            (0 until 8).mapNotNull { values["decision.questions[$it]"]?.take(1000) })
    }
}
