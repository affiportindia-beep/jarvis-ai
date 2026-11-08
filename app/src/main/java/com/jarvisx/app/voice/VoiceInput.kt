package com.jarvisx.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the microphone is doing; shown in the chat so the user always knows. */
sealed class ListenState {
    object Idle : ListenState()
    object Listening : ListenState()
    data class Partial(val text: String) : ListenState()
    data class Error(val message: String, val canDownloadModel: Boolean = false) : ListenState()
}

/**
 * Offline speech-to-text using Android's ON-DEVICE recogniser only
 * ([SpeechRecognizer.createOnDeviceSpeechRecognizer], Android 12+).
 *
 * The ordinary Android recogniser is deliberately not used: it may send audio to an online
 * service. If the on-device recogniser or its language pack is missing, we say so and offer to
 * download the pack instead of silently going online.
 *
 * Must be used from the main thread (Android requirement).
 */
class VoiceInput(private val context: Context) {

    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Starts one utterance. [onFinal] receives the recognised text. */
    fun start(languageTag: String, onFinal: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            _state.value = ListenState.Error(
                "This phone has no offline speech recogniser. Voice input needs Android 12+ with " +
                    "Google's on-device speech service; typing still works."
            )
            return
        }
        stop()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _state.value = ListenState.Listening }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { _state.value = ListenState.Partial(it) }
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _state.value = ListenState.Idle
                release()
                if (text.isNotBlank()) onFinal(text)
            }

            override fun onError(error: Int) {
                release()
                _state.value = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        ListenState.Error("Didn't catch that. Tap the mic and try again.")
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                        ListenState.Error(
                            "The offline speech pack for this language is not on the phone yet.",
                            canDownloadModel = Build.VERSION.SDK_INT >= 33,
                        )
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        ListenState.Error("Microphone permission is needed for voice input.")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        ListenState.Error("The speech recogniser is busy. Try again in a moment.")
                    else -> ListenState.Error("Voice input stopped (code $error). Typing still works.")
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        _state.value = ListenState.Listening
        r.startListening(intent)
    }

    /** Asks Android to download the offline speech pack for [languageTag] (Android 13+). */
    fun downloadLanguagePack(languageTag: String) {
        if (Build.VERSION.SDK_INT < 33) return
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        _state.value = ListenState.Error("Downloading the offline speech pack… you can keep using the app.")
        if (Build.VERSION.SDK_INT >= 34) {
            r.triggerModelDownload(intent, context.mainExecutor, object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) {
                    _state.value = ListenState.Error("Downloading the offline speech pack: $completedPercent%")
                }
                override fun onSuccess() { _state.value = ListenState.Error("Speech pack ready. Tap the mic."); r.destroy() }
                override fun onScheduled() { _state.value = ListenState.Error("Download scheduled by Android; it finishes on Wi-Fi."); r.destroy() }
                override fun onError(error: Int) { _state.value = ListenState.Error("Speech pack download failed (code $error)."); r.destroy() }
            })
        } else {
            // Android 13: Android shows its own download dialog/notification.
            r.triggerModelDownload(intent)
            r.destroy()
        }
    }

    fun stop() {
        recognizer?.let { runCatching { it.stopListening() } }
        release()
        if (_state.value is ListenState.Listening || _state.value is ListenState.Partial) _state.value = ListenState.Idle
    }

    fun clearMessage() {
        if (_state.value is ListenState.Error) _state.value = ListenState.Idle
    }

    private fun release() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }
}
