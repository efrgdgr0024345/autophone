package com.blackcat.remote

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import java.io.IOException
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import org.json.JSONObject

internal enum class NarrationVoice(val label: String) {
    TEXT("Text explanations only"), PHONE("Phone voice · offline voices only"), OPENAI("OpenAI voice · paid AI-generated speech")
}

internal object NarrationText {
    fun step(step: SuggestedCommand): String = buildString {
        append(step.explanation.ifBlank { "Let's work through ${step.title.lowercase()} together." })
        if (step.requiresAdmin) append(" This step needs administrator privileges. We'll check the result carefully.")
        if (step.expectedResult.isNotBlank()) append(" Here's what to look for: ${step.expectedResult}.")
        append(" You can stop me at any time.")
    }.take(1800)
}

internal object OpenAiSpeechCodec {
    const val ENDPOINT = "https://api.openai.com/v1/audio/speech"
    const val MODEL = "gpt-4o-mini-tts"
    fun request(text: String): JSONObject {
        require(text.isNotBlank() && text.length <= 1800)
        return JSONObject().put("model", MODEL).put("voice", "marin").put("input", text)
            .put("response_format", "pcm").put("instructions",
                "Speak warmly and naturally, like a calm helpful companion. Explain without hype, pressure or theatrical voices. Use gentle pauses. Do not add words or claim commands succeeded.")
    }
}

/** No persisted audio, no microphone and no command input. Cancelling also stops playback. */
internal class AssistantNarrator(private val activity: Activity, private val status: (String) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speech: TextToSpeech? = null
    private var job: Job? = null
    @Volatile private var http: HttpsURLConnection? = null
    @Volatile private var track: AudioTrack? = null
    private var generation = 0L
    private var closed = false
    private var openAiUnavailable = false

    fun explain(text: String, voice: NarrationVoice, key: String) {
        stop()
        if (closed || voice == NarrationVoice.TEXT || text.isBlank()) return
        if (voice == NarrationVoice.OPENAI && openAiUnavailable) return
        val token = generation
        if (voice == NarrationVoice.PHONE) {
            // Choose a voice that explicitly requires no network before passing it any text.
            speech = TextToSpeech(activity.applicationContext) { result ->
                activity.runOnUiThread {
                    if (token != generation || closed) return@runOnUiThread
                    val engine = speech ?: return@runOnUiThread
                    val voices = engine.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
                    val offline = voices.firstOrNull { it.locale.language == Locale.getDefault().language }
                        ?: voices.firstOrNull { it.locale.language == "en" }
                    if (result != TextToSpeech.SUCCESS || offline == null) {
                        status("Offline phone voice unavailable. The explanation remains on screen.")
                    } else {
                        engine.voice = offline
                        engine.speak(text.take(1800), TextToSpeech.QUEUE_FLUSH, null, "blackcat-explanation")
                    }
                }
            }
            return
        }
        job = scope.launch {
            var bytes: ByteArray? = null
            try {
                bytes = withTimeout(35000) { fetch(text.take(1800), key) }
                if (generation != token || closed) return@launch
                val pcm = bytes
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    if (generation != token || closed) return@withContext
                    val player = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(16384).build()
                    track = player
                    try {
                        if (generation != token || closed) return@withContext
                        player.play()
                        var offset = 0
                        while (offset < pcm.size) {
                            currentCoroutineContext().ensureActive()
                            if (generation != token || closed) break
                            val count = player.write(pcm, offset, minOf(4096, pcm.size - offset), AudioTrack.WRITE_BLOCKING)
                            if (count <= 0) break
                            offset += count
                        }
                        // Let the final queued samples play, with cancellable checks.
                        while (generation == token && !closed && player.playbackHeadPosition < offset / 2) delay(30)
                    } finally { if (track === player) track = null; player.release() }
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) {
                if (generation == token && !closed) {
                    openAiUnavailable = true
                    status("OpenAI voice unavailable. This session continues with text explanations; no audio retry.")
                }
            } finally { bytes?.fill(0) }
        }
    }

    private suspend fun fetch(text: String, key: String): ByteArray = withContext(Dispatchers.IO) {
        require(OpenAiPlanner.validKey(key))
        val body = OpenAiSpeechCodec.request(text).toString().toByteArray(Charsets.UTF_8)
        currentCoroutineContext().ensureActive()
        val connection = URL(OpenAiSpeechCodec.ENDPOINT).openConnection() as HttpsURLConnection
        http = connection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 25000
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != 200) throw IOException("Speech request failed")
            val contentType = connection.contentType.orEmpty().substringBefore(';')
            if (contentType !in setOf("audio/pcm", "audio/L16", "application/octet-stream")) throw IOException("Unexpected speech response")
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 2_400_000) throw IOException("Speech response too large")
                    out.write(buffer, 0, count)
                }
                val bytes = out.toByteArray()
                if (bytes.isEmpty() || bytes.size % 2 != 0) { bytes.fill(0); throw IOException("Invalid PCM audio") }
                bytes
            }
        } finally { body.fill(0); connection.disconnect(); if (http === connection) http = null }
    }

    fun stop() {
        generation++
        http?.disconnect()
        job?.cancel()
        job = null
        try { track?.pause(); track?.flush() } catch (_: IllegalStateException) {}
        speech?.stop()
        speech?.shutdown()
        speech = null
    }

    suspend fun awaitExplanation() {
        withTimeoutOrNull(60000) { job?.join() }
    }

    fun close() { closed = true; stop(); scope.cancel() }
    companion object { const val VOICE_KEY = "assistant_narration_voice" }
}
