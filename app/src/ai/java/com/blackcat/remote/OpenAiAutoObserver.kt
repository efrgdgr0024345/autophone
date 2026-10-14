package com.blackcat.remote

import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal interface AutoObservationClient {
    suspend fun observe(goal: String, step: SuggestedCommand, target: String, model: String, key: String, jpeg: ByteArray, preflight: Boolean = false): AutoObservation
    fun cancel()
}

/** Vision-only client. It has no HID, approval, camera or persistent storage reference. */
internal class OpenAiAutoObserver : AutoObservationClient {
    @Volatile private var connection: HttpsURLConnection? = null
    override fun cancel() { connection?.disconnect() }

    override suspend fun observe(goal: String, step: SuggestedCommand, target: String, model: String,
                                 key: String, jpeg: ByteArray, preflight: Boolean): AutoObservation = withContext(Dispatchers.IO) {
        require(OpenAiPlanner.validKey(key))
        val body = AutoObservationCodec.request(goal, step, target, model, jpeg, preflight).toString().toByteArray(Charsets.UTF_8)
        currentCoroutineContext().ensureActive()
        val http = URL(OpenAiPlanner.ENDPOINT).openConnection() as HttpsURLConnection
        connection = http
        try {
            http.requestMethod = "POST"
            http.instanceFollowRedirects = false
            http.connectTimeout = 10000
            http.readTimeout = 60000
            http.useCaches = false
            http.doOutput = true
            http.setRequestProperty("Authorization", "Bearer $key")
            http.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            http.setFixedLengthStreamingMode(body.size)
            http.outputStream.use { it.write(body) }
            if (http.responseCode != 200) throw PlanException(OpenAiPlanner.failureMessage(http.responseCode, OpenAiPlanner.providerErrorCode(http)))
            val response = http.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 262144) throw PlanException("Photo check too large. Auto paused.")
                    out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }
            currentCoroutineContext().ensureActive()
            AutoObservationCodec.parseResponse(response)
        } catch (_: SSLException) {
            currentCoroutineContext().ensureActive()
            throw PlanException("Secure photo-check connection failed. Auto paused.")
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            throw PlanException("Photo check could not reach OpenAI. Auto paused; no retry or next command.")
        } finally {
            body.fill(0)
            http.disconnect()
            if (connection === http) connection = null
        }
    }
}
