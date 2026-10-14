package com.blackcat.remote

import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** No Bluetooth reference, command target, execution tool or logging in this client. */
class OpenAiPlanner {
    @Volatile private var connection: HttpsURLConnection? = null
    fun cancel() { connection?.disconnect() }

    suspend fun propose(goal: String, target: String, model: String, key: String): CommandPlan = withContext(Dispatchers.IO) {
        require(validKey(key)) { "Enter a valid API key." }
        val body = PlanCodec.request(goal, target, model).toString().toByteArray(Charsets.UTF_8)
        currentCoroutineContext().ensureActive()
        val http = URL(ENDPOINT).openConnection() as HttpsURLConnection
        connection = http
        try {
            http.requestMethod = "POST"
            http.instanceFollowRedirects = false
            http.connectTimeout = 10000
            http.readTimeout = 45000
            http.useCaches = false
            http.doOutput = true
            http.setRequestProperty("Authorization", "Bearer $key")
            http.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            http.setFixedLengthStreamingMode(body.size)
            currentCoroutineContext().ensureActive()
            http.outputStream.use { it.write(body) }
            val code = http.responseCode
            if (code != 200) throw PlanException(when (code) {
                401 -> "OpenAI rejected the key. Check or replace it in the app."
                403 -> "This API key or account is not permitted to use this model."
                429 -> "OpenAI quota or rate limit reached. Check API billing/limits; no automatic retry."
                400, 404 -> "OpenAI rejected the model/request. Check model access."
                in 300..399 -> "Unexpected redirect blocked to protect the API key."
                else -> "OpenAI request failed (HTTP $code). Nothing was sent to the computer."
            })
            val response = http.inputStream.use { input ->
                val buffer = ByteArray(4096)
                val out = java.io.ByteArrayOutputStream()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 262144) throw PlanException("API response too large.")
                    out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }
            currentCoroutineContext().ensureActive()
            PlanCodec.parseResponse(response)
        } catch (_: SocketTimeoutException) { throw PlanException("API request timed out. No automatic retry; nothing was sent to the computer.") }
          catch (_: SSLException) { throw PlanException("Secure connection failed. TLS verification was not bypassed.") }
        finally {
            body.fill(0)
            http.disconnect()
            if (connection === http) connection = null
        }
    }

    companion object {
        const val ENDPOINT = "https://api.openai.com/v1/responses"
        fun validKey(key: String) = key.startsWith("sk-") && key.length in 20..512 && key.all { it.code in 33..126 }
    }
}
