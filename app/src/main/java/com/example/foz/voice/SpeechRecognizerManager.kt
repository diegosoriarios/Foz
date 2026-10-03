package com.example.foz.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.foz.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps the system SpeechRecognizer. All calls must originate from the main
 * thread; callbacks are marshalled onto a StateFlow so Compose can observe them.
 */
class SpeechRecognizerManager(private val context: Context) {

    data class VoiceState(
        val isListening: Boolean = false,
        val partialText: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var onFinalResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(onFinalResult: (String) -> Unit, onError: (String) -> Unit) {
        this.onFinalResult = onFinalResult
        this.onError = onError
        mainHandler.post { reallyStart() }
    }

    fun stopListening() {
        onFinalResult = null
        onError = null
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (_: Throwable) {
            }
            recognizer = null
            _state.value = VoiceState()
        }
    }

    private fun reallyStart() {
        if (!isAvailable()) {
            _state.value = VoiceState(error = context.getString(R.string.assistant_voice_error_unavailable))
            onError?.invoke(_state.value.error ?: "Speech recognition unavailable")
            return
        }
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _state.value = VoiceState(isListening = true)
            startListening(intent)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceState(isListening = true)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val message = errorMessageFor(error)
            Log.w(TAG, "SpeechRecognizer error $error: $message")
            _state.value = VoiceState(error = message)
            onError?.invoke(message)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            _state.value = VoiceState()
            if (!text.isNullOrBlank()) {
                onFinalResult?.invoke(text)
            } else {
                val message = context.getString(R.string.assistant_voice_error_no_match)
                onError?.invoke(message)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrBlank()) {
                _state.value = _state.value.copy(partialText = text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun errorMessageFor(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                context.getString(R.string.assistant_voice_error_no_match)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                context.getString(R.string.assistant_voice_error_busy)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                context.getString(R.string.assistant_voice_error_permission)
            else ->
                context.getString(R.string.assistant_voice_error_generic)
        }
    }

    companion object {
        private const val TAG = "SpeechRecognizerMgr"
    }
}
