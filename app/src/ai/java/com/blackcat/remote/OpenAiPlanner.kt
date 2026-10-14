package com.blackcat.remote

import java.net.SocketTimeoutException
import java.net.URL
import java.io.IOException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
            http.readTimeout = 60000
            http.useCaches = false
            http.doOutput = true
            http.setRequestProperty("Authorization", "Bearer $key")
            http.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            http.setFixedLengthStreamingMode(body.size)
            currentCoroutineContext().ensureActive()
            http.outputStream.use { it.write(body) }
            val code = http.responseCode
            if (code != 200) throw PlanException(failureMessage(code, providerErrorCode(http)))
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
        } catch (_: SocketTimeoutException) {
            currentCoroutineContext().ensureActive()
            throw PlanException("API request timed out. No automatic retry; nothing was sent to the computer.")
        } catch (_: SSLException) {
            currentCoroutineContext().ensureActive()
            throw PlanException("Secure connection failed. TLS verification was not bypassed.")
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            throw PlanException("Could not reach OpenAI. Check your internet connection; your current plan was kept and nothing was typed.")
        }
        finally {
            body.fill(0)
            http.disconnect()
            if (connection === http) connection = null
        }
    }

    companion object {
        const val ENDPOINT = "https://api.openai.com/v1/responses"
        fun validKey(key: String) = key.startsWith("sk-") && key.length in 20..512 && key.all { it.code in 33..126 }

        internal fun failureMessage(code: Int, errorCode: String): String = when (code) {
            401 -> "OpenAI rejected the key. Check or replace it in Settings."
            403 -> "This key is not permitted to generate a response with the selected model. Check key permissions and model access."
            429 -> if (errorCode == "insufficient_quota")
                "OpenAI API credit or billing quota is unavailable. Check API billing; no automatic retry."
                else "OpenAI rate limit reached. Try again manually later; no automatic retry."
            404 -> "The selected model is unavailable to this key. Check model access or choose the other model."
            400 -> "OpenAI rejected the request format. Your current plan was kept; nothing was typed."
            in 300..399 -> "Unexpected redirect blocked to protect the API key."
            else -> "OpenAI request failed (HTTP $code). Your current plan was kept; nothing was typed."
        }

        // Read only the provider's bounded error code; never expose its message/body to UI or logs.
        internal suspend fun providerErrorCode(http: HttpsURLConnection): String {
            return try {
                val body = http.errorStream?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 16384) return ""
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                } ?: return ""
                JSONObject(body).optJSONObject("error")?.optString("code").orEmpty()
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                ""
            } catch (_: org.json.JSONException) { "" }
        }
    }
}
