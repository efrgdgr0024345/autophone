package com.blackcat.remote

import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class OpenAiPhotoAnalyzer {
    @Volatile private var connection: HttpsURLConnection? = null

    fun cancel() {
        connection?.disconnect()
    }

    suspend fun analyze(
        goal: String,
        target: String,
        model: String,
        key: String,
        jpeg: ByteArray
    ): PhotoAnalysis = withContext(Dispatchers.IO) {
        require(OpenAiPlanner.validKey(key)) { "Enter a valid API key." }
        val body = PhotoAnalysisCodec.request(goal, target, model, jpeg)
            .toString().toByteArray(Charsets.UTF_8)
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
            currentCoroutineContext().ensureActive()
            http.outputStream.use { it.write(body) }
            val code = http.responseCode
            if (code != 200) throw PlanException(when (code) {
                401 -> "OpenAI rejected the key. Check or replace it in Settings."
                403 -> "This API key or account is not permitted to use the selected model."
                429 -> "OpenAI quota or rate limit reached. No automatic retry."
                400, 404 -> "OpenAI rejected the photo request or selected model."
                in 300..399 -> "Unexpected redirect blocked to protect the API key."
                else -> "OpenAI photo request failed (HTTP $code)."
            })
            val response = http.inputStream.use { input ->
                val buffer = ByteArray(4096)
                val out = java.io.ByteArrayOutputStream()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 262144) throw PlanException("Photo response too large.")
                    out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }
            currentCoroutineContext().ensureActive()
            PhotoAnalysisCodec.parseResponse(response)
        } catch (_: SocketTimeoutException) {
            throw PlanException("Photo request timed out. Nothing was typed.")
        } catch (_: SSLException) {
            throw PlanException("Secure connection failed. TLS verification was not bypassed.")
        } finally {
            body.fill(0)
            http.disconnect()
            if (connection === http) connection = null
        }
    }
}
