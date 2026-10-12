package com.blackcat.remote

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Explicit foreground dictation only. No audio files, AI requests, command targets or logging. */
internal class VoiceFeedbackController(private val activity: Activity) {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0L
    val listening: Boolean get() = recognizer != null

    fun onDeviceAvailable() = Build.VERSION.SDK_INT >= 31 &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)

    fun start(onText: (String) -> Unit, onStatus: (String) -> Unit) {
        cancel()
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST)
            onStatus("Grant microphone access, then tap Speak again. Listening does not start automatically.")
            return
        }
        if (!onDeviceAvailable() && !SpeechRecognizer.isRecognitionAvailable(activity)) {
            onStatus("No speech recognition service is installed. Type feedback instead.")
            return
        }
        val token = generation
        try {
            val speech = if (onDeviceAvailable()) SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
                else SpeechRecognizer.createSpeechRecognizer(activity)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (token == generation) onStatus("Listening… speak your feedback. Nothing is being typed or sent to OpenAI.")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { if (token == generation) onStatus("Finishing transcription…") }
                override fun onError(error: Int) {
                    if (token != generation) return
                    cancel()
                    onStatus(when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access was denied. Type feedback or enable it in Android settings."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No clear speech was recognized. Try again or type feedback."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech service needs a working connection. Type feedback instead."
                        else -> "Speech recognition is unavailable right now. Try again or type feedback."
                    })
                }
                override fun onResults(results: Bundle?) {
                    if (token != generation) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    cancel()
                    if (text.isNullOrBlank() || text.length > 2000) onStatus("No usable transcript. Try again or type feedback.")
                    else onText(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            speech.startListening(intent)
        } catch (_: Exception) {
            cancel()
            onStatus("Could not start speech recognition. Type feedback instead.")
        }
    }

    fun cancel() {
        generation++
        val speech = recognizer
        recognizer = null
        speech?.cancel()
        speech?.destroy()
    }

    companion object { private const val MICROPHONE_REQUEST = 4110 }
}
