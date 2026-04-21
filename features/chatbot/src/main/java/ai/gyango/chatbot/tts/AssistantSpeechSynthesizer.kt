package ai.gyango.chatbot.tts

import android.content.Context
import ai.gyango.core.InferenceSettings

/** On-device assistant speech. Implemented in the app module (ONNX TTS). */
interface AssistantSpeechSynthesizer {
    fun speak(text: String)
    fun stopPlayback()
    fun release()

    /** Load ONNX sessions into RAM so the first [speak] is fast. No-op by default. */
    fun warmUpOnDeviceModels() {}
}

fun interface AssistantSpeechSynthesizerFactory {
    /**
     * @param settings Current inference settings; use [InferenceSettings.ttsLanguage] and
     * [InferenceSettings.ttsVoiceGender] to load the ONNX voice pack. The chat ViewModel releases
     * the previous synthesizer when those fields (or speech locale) change.
     */
    fun create(context: Context, settings: InferenceSettings): AssistantSpeechSynthesizer
}
