package com.blackcat.remote

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class KeyCheckStatus {
    VERIFIED, INVALID_FORMAT, REJECTED_KEY, MODEL_UNAVAILABLE, FORBIDDEN, RATE_LIMITED,
    NETWORK_FAILED, SECURE_CONNECTION_FAILED, INVALID_RESPONSE, SERVICE_FAILED
}

/** Contains only a bounded status and fixed UI message, never the key or raw provider response. */
class KeyCheckResult(val status: KeyCheckStatus, val message: String) {
    val verified: Boolean get() = status == KeyCheckStatus.VERIFIED
}

interface ApiKeyChecker {
    fun cancel()
    suspend fun check(key: String, model: String): KeyCheckResult
}

/** Explicit foreground authentication/model-visibility check; no generation, prompts or HID. */
class OpenAiKeyChecker : ApiKeyChecker {
    @Volatile private var connection: HttpsURLConnection? = null

    override fun cancel() { connection?.disconnect() }

    override suspend fun check(key: String, model: String): KeyCheckResult = withContext(Dispatchers.IO) {
        if (!OpenAiPlanner.validKey(key)) return@withContext KeyCheckResult(KeyCheckStatus.INVALID_FORMAT,
            "Enter an OpenAI API key without spaces or line breaks. Its format alone does not verify it.")
        require(model in PlanCodec.MODELS)
        currentCoroutineContext().ensureActive()
        val http = URL(MODELS_ENDPOINT + model).openConnection() as HttpsURLConnection
        connection = http
        try {
            http.requestMethod = "GET"
            http.instanceFollowRedirects = false
            http.connectTimeout = 10000
            http.readTimeout = 15000
            http.useCaches = false
            http.setRequestProperty("Authorization", "Bearer $key")
            http.setRequestProperty("Accept", "application/json")
            currentCoroutineContext().ensureActive()
            val code = http.responseCode
            val response = if (code == 200) http.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 65536) return@withContext invalidResponse()
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            } else ""
            currentCoroutineContext().ensureActive()
            interpret(code, model, response)
        } catch (_: SocketTimeoutException) {
            currentCoroutineContext().ensureActive()
            KeyCheckResult(KeyCheckStatus.NETWORK_FAILED, "Key check timed out. The key was not verified; try again when connected.")
        } catch (_: SSLException) {
            currentCoroutineContext().ensureActive()
            KeyCheckResult(KeyCheckStatus.SECURE_CONNECTION_FAILED, "Secure connection failed. The key was not verified; TLS verification was not bypassed.")
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            KeyCheckResult(KeyCheckStatus.NETWORK_FAILED, "Could not reach OpenAI. The key was not verified; check your internet connection.")
        } finally {
            http.disconnect()
            if (connection === http) connection = null
        }
    }

    companion object {
        const val MODELS_ENDPOINT = "https://api.openai.com/v1/models/"

        private fun invalidResponse() = KeyCheckResult(KeyCheckStatus.INVALID_RESPONSE,
            "OpenAI returned unexpected model information. The key was not verified.")

        internal fun interpret(code: Int, model: String, response: String): KeyCheckResult {
            require(model in PlanCodec.MODELS)
            return when (code) {
                200 -> {
                    val recognized = try {
                        val value = JSONObject(response)
                        value.optString("id") == model && value.optString("object") == "model"
                    } catch (_: Exception) { false }
                    if (recognized) KeyCheckResult(KeyCheckStatus.VERIFIED,
                        "OpenAI accepted the key and lists this model. Billing and permission to generate responses were not tested.")
                    else invalidResponse()
                }
                401 -> KeyCheckResult(KeyCheckStatus.REJECTED_KEY, "OpenAI rejected this API key. Check or replace it.")
                403 -> KeyCheckResult(KeyCheckStatus.FORBIDDEN,
                    "This key cannot read model information. Check its permissions or organization access; this does not prove the key is incorrect.")
                404 -> KeyCheckResult(KeyCheckStatus.MODEL_UNAVAILABLE,
                    "The selected model is unavailable to this key. Try the other model or check model access.")
                429 -> KeyCheckResult(KeyCheckStatus.RATE_LIMITED,
                    "OpenAI limited this check. The key was not verified; retry manually later.")
                in 300..399 -> KeyCheckResult(KeyCheckStatus.SERVICE_FAILED,
                    "Unexpected redirect blocked to protect the API key. The key was not verified.")
                else -> KeyCheckResult(KeyCheckStatus.SERVICE_FAILED,
                    "OpenAI could not complete the key check (HTTP $code). The key was not verified.")
            }
        }
    }
}
