package com.blackcat.remote

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.cert.Certificate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/** Actual production HTTP clients/serializers/parsers with a JVM-only HTTPS connection double.
 * No socket, real credential, provider charge or production endpoint override is used.
 */
class OpenAiTransportTest {
    companion object {
        private val replies = ConcurrentLinkedQueue<Reply>()
        private const val KEY = "sk-transport-fixture-not-a-real-key"
        @BeforeClass @JvmStatic fun interceptTestHttpsOnly() {
            URL.setURLStreamHandlerFactory { protocol -> if (protocol != "https") null else object : URLStreamHandler() {
                override fun openConnection(url: URL): URLConnection {
                    check(url.host == "api.openai.com")
                    val reply = replies.poll() ?: error("Unscripted HTTP request; real networking is forbidden in these tests")
                    return FakeConnection(url, reply).also { reply.connection = it }
                }
            } }
        }
    }
    private class Reply(val code: Int = 200, val body: String = "", val error: String = "", val fault: IOException? = null,
                        val blocked: Boolean = false) {
        lateinit var connection: FakeConnection
        val reading = CountDownLatch(1)
    }
    private class FakeConnection(url: URL, val reply: Reply) : HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var originalBuffer: ByteArray? = null
        var disconnected = false
        private val released = CountDownLatch(1)
        override fun connect() { connected = true }
        override fun disconnect() { disconnected = true; released.countDown() }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TEST_ONLY"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getOutputStream() = object : java.io.OutputStream() {
            override fun write(value: Int) { sent.write(value) }
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                originalBuffer = buffer; sent.write(buffer, offset, length)
            }
        }
        override fun getResponseCode(): Int { reply.fault?.let { throw it }; return reply.code }
        override fun getErrorStream() = ByteArrayInputStream(reply.error.toByteArray())
        override fun getContentType() = "text/event-stream; charset=utf-8"
        override fun getInputStream(): InputStream = if (!reply.blocked) ByteArrayInputStream(reply.body.toByteArray())
            else object : InputStream() {
                override fun read(): Int {
                    reply.reading.countDown()
                    check(released.await(5, TimeUnit.SECONDS)) { "Cancellation did not disconnect the pending request" }
                    throw IOException("Synthetic cancelled stream")
                }
            }
    }
    private fun queue(value: Reply): Reply { replies.add(value); return value }
    private fun response(value: JSONObject) = JSONObject().put("status", "completed").put("output", JSONArray().put(
        JSONObject().put("type", "message").put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", value.toString()))))).toString()
    private fun command() = JSONObject().put("title", "Check identity").put("command", "id").put("explanation", "Inspect the current account.")
        .put("risk", "low").put("requires_admin", false).put("expected_result", "Account information.").put("warnings", JSONArray())
    private fun plan() = JSONObject().put("explanation", "Inspect first.").put("roadmap", JSONArray()).put("assumptions", JSONArray())
        .put("decision", JSONObject().put("mode", "commands").put("questions", JSONArray()).put("commands", JSONArray().put(command())))
    private fun step() = SuggestedCommand("Check", "id", "Inspect.", "low", false, "Account information.", emptyList())
    private fun verifiedTransport(reply: Reply, path: String = "/v1/responses", post: Boolean = true, stream: Boolean = false) {
        val connection = reply.connection
        assertEquals(path, connection.url.path)
        assertEquals(if (post) "POST" else "GET", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertEquals("Bearer $KEY", connection.getRequestProperty("Authorization"))
        assertEquals(10000, connection.connectTimeout)
        assertTrue(connection.disconnected)
        if (post) {
            assertTrue(connection.originalBuffer!!.all { it == 0.toByte() })
            val body = JSONObject(connection.sent.toString("UTF-8"))
            val isPlanner = body.getJSONObject("text").getJSONObject("format").getString("name") == "command_proposal"
            assertEquals(if (isPlanner) 180000 else 60000, connection.readTimeout)
            if (isPlanner) assertTrue(OpenAiPlanner.REQUEST_TIMEOUT_MS >= connection.connectTimeout + connection.readTimeout)
            assertFalse(body.getBoolean("store"))
            assertEquals(stream, body.getBoolean("stream"))
            assertTrue(body.getJSONObject("text").getJSONObject("format").getBoolean("strict"))
            assertEquals("none", body.getString("tool_choice"))
        }
    }
    @Test fun plannerUsesTheCompleteProductionWireAndParsesProviderOutput() = runBlocking {
        val reply = queue(Reply(body = response(plan())))
        val result = OpenAiPlanner().propose("Inspect this synthetic host", "Linux", PlanCodec.DEFAULT_MODEL, KEY)
        assertEquals("id", result.commands.single().command)
        verifiedTransport(reply)
    }
    @Test fun streamedPlannerUsesTheSameSchemaAndDeliversDisplayOnlyDrafts() = runBlocking {
        val text = plan().toString()
        val delta = JSONObject().put("type", "response.output_text.delta").put("output_index", 0).put("content_index", 0).put("delta", text)
        val end = JSONObject().put("type", "response.completed").put("response", JSONObject(response(plan())))
        val reply = queue(Reply(body = "data: $delta\n\ndata: $end\n\n"))
        val drafts = mutableListOf<PlanStreamDraft>()
        val result = OpenAiPlanner().propose("Inspect", "Linux", PlanCodec.DEFAULT_MODEL, KEY) { drafts += it }
        assertEquals("id", result.commands.single().command)
        assertEquals("id", drafts.last().commands.single().command)
        assertEquals("text/event-stream", reply.connection.getRequestProperty("Accept"))
        verifiedTransport(reply, stream = true)
    }
    @Test fun observerSendsActualImageBytesAndParsesAllCollectionFields() = runBlocking {
        val value = JSONObject().put("observed_command", "id").put("terminal_state", "shell_prompt").put("outcome", "appears_complete")
            .put("explanation", "Account output and a fresh prompt are visible.").put("visible_evidence", JSONArray(listOf("uid=1000")))
            .put("revision_needed", false).put("page_marker", "").put("page_state", "none").put("page_text", "").put("exit_code", "")
        val reply = queue(Reply(body = response(value)))
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 0xff.toByte(), 0xd9.toByte())
        val result = OpenAiAutoObserver().observe("Inspect host", step(), "Linux", PlanCodec.DEFAULT_MODEL, KEY, jpeg)
        assertTrue(result.allowsNext("id"))
        val sent = reply.connection.sent.toString("UTF-8")
        assertTrue(sent.contains(java.util.Base64.getEncoder().encodeToString(jpeg)))
        verifiedTransport(reply)
    }
    @Test fun photoClientUsesTheSameProtectedWireAndParsesAnalysis() = runBlocking {
        val analysis = JSONObject().put("summary", "A terminal is visible.").put("observations", JSONArray(listOf("An idle prompt.")))
            .put("decision", JSONObject().put("mode", "commands").put("questions", JSONArray()).put("commands", JSONArray().put(command())))
        val reply = queue(Reply(body = response(analysis)))
        assertEquals("id", OpenAiPhotoAnalyzer().analyze("Inspect host", "Linux", PlanCodec.DEFAULT_MODEL, KEY, byteArrayOf(1)).commands.single().command)
        verifiedTransport(reply)
    }
    @Test fun providerFailuresAreBoundedAndNeverExposeTheRawMessageOrRetry() = runBlocking {
        for ((status, code, expected) in listOf(Triple(401, "invalid_api_key", "rejected"), Triple(403, "forbidden", "not permitted"),
            Triple(404, "model_not_found", "unavailable"), Triple(429, "insufficient_quota", "billing"), Triple(429, "rate_limit", "rate limit"),
            Triple(400, "invalid_json_schema", "format"), Triple(302, "redirect", "redirect"), Triple(500, "server_error", "HTTP 500"))) {
            val reply = queue(Reply(status, error = """{"error":{"code":"$code","message":"PRIVATE_PROVIDER_PAYLOAD"}}"""))
            try { OpenAiPlanner().propose("Inspect", "Linux", PlanCodec.DEFAULT_MODEL, KEY); fail("Expected HTTP $status to stop") }
            catch (error: PlanException) {
                assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(expected, ignoreCase = true))
                assertFalse(error.message.orEmpty().contains("PRIVATE_PROVIDER_PAYLOAD"))
            }
            verifiedTransport(reply)
            assertTrue(replies.isEmpty())
        }
    }
    @Test fun malformedMixedAndOversizedProviderOutputNeverBecomesAPlan() = runBlocking {
        val mixed = plan().apply { getJSONObject("decision").put("questions", JSONArray(listOf("Unexpected question"))) }
        for (body in listOf("<html>PRIVATE_PROVIDER_PAYLOAD</html>", response(mixed), "x".repeat(262145))) {
            val reply = queue(Reply(body = body))
            try { OpenAiPlanner().propose("Inspect", "Linux", PlanCodec.DEFAULT_MODEL, KEY); fail("Invalid response accepted") }
            catch (error: PlanException) { assertFalse(error.message.orEmpty().contains("PRIVATE_PROVIDER_PAYLOAD")) }
            verifiedTransport(reply)
        }
    }
    @Test fun timeoutAndTlsFailuresDisconnectAndKeepTheirUsefulDiagnosis() = runBlocking {
        for ((fault, expected) in listOf(SocketTimeoutException() to "timed out", SSLException("synthetic") to "Secure connection", IOException() to "Could not reach")) {
            val reply = queue(Reply(fault = fault))
            try { OpenAiPlanner().propose("Inspect", "Linux", PlanCodec.DEFAULT_MODEL, KEY); fail("Fault accepted") }
            catch (error: PlanException) { assertTrue(error.message.orEmpty().contains(expected)) }
            verifiedTransport(reply)
        }
    }
    @Test fun cancellationDisconnectsAnInFlightClientAndClearsItsBody() = runBlocking {
        val reply = queue(Reply(blocked = true))
        val client = OpenAiPlanner()
        val job = async { client.propose("Inspect", "Linux", PlanCodec.DEFAULT_MODEL, KEY) }
        withContext(Dispatchers.IO) { assertTrue(reply.reading.await(3, TimeUnit.SECONDS)) }
        job.cancel(); client.cancel()
        try { job.await(); fail("Cancelled request returned") } catch (_: CancellationException) { }
        job.join()
        verifiedTransport(reply)
    }
    @Test fun streamingCancellationClosesTheReaderWithoutPublishingADraftOrAPlan() = runBlocking {
        val reply = queue(Reply(blocked = true))
        val client = OpenAiPlanner()
        var drafts = 0
        val job = async { client.propose("Inspect", "Linux", PlanCodec.DEFAULT_MODEL, KEY) { drafts++ } }
        withContext(Dispatchers.IO) { assertTrue(reply.reading.await(3, TimeUnit.SECONDS)) }
        job.cancel(); client.cancel()
        try { job.await(); fail("Cancelled stream returned") } catch (_: CancellationException) { }
        job.join()
        assertEquals(0, drafts)
        verifiedTransport(reply, stream = true)
    }
    @Test fun explicitKeyCheckUsesMetadataOnlyAndValidatesTheModelIdentity() = runBlocking {
        val reply = queue(Reply(body = JSONObject().put("object", "model").put("id", PlanCodec.DEFAULT_MODEL).toString()))
        assertTrue(OpenAiKeyChecker().check(KEY, PlanCodec.DEFAULT_MODEL).verified)
        assertEquals(0, reply.connection.sent.size())
        verifiedTransport(reply, "/v1/models/${PlanCodec.DEFAULT_MODEL}", post = false)
        val wrong = queue(Reply(body = """{"object":"model","id":"wrong-model"}"""))
        assertFalse(OpenAiKeyChecker().check(KEY, PlanCodec.DEFAULT_MODEL).verified)
        verifiedTransport(wrong, "/v1/models/${PlanCodec.DEFAULT_MODEL}", post = false)
    }
}
