package ai.pocket.chatbot.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper around [SpeechRecognizer] for one-shot dictation. Must be used from the main thread
 * for callbacks; call [release] when the hosting Activity is destroyed.
 */
class SpeechTranscriber(context: Context) {

    private val appContext = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(
        localeTag: String,
        onStarted: () -> Unit,
        onFinalResult: (String) -> Unit,
        onFailed: (String) -> Unit,
        onFinished: () -> Unit
    ) {
        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        if (recognizer == null) {
            onFailed("Speech recognition is not available on this device")
            onFinished()
            return
        }
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onStarted()
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val message = speechErrorMessage(error)
                onFailed(message)
                cleanup()
                onFinished()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!text.isNullOrEmpty()) {
                    onFinalResult(text)
                } else {
                    onFailed("No speech was recognized. Try again.")
                }
                cleanup()
                onFinished()
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.let { r ->
            try {
                r.stopListening()
            } catch (_: Exception) { }
            try {
                r.destroy()
            } catch (_: Exception) { }
        }
        speechRecognizer = null
    }

    fun release() {
        stopListening()
    }

    private fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun speechErrorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Recognition cancelled"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
        SpeechRecognizer.ERROR_NETWORK -> "Network error (check connection for speech services)"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand speech. Try speaking more clearly."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy — try again"
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap the mic and try again."
        else -> "Speech recognition failed (code $code)"
    }
}
