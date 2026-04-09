package ai.pocket.core

import kotlinx.serialization.Serializable

/**
 * Model-agnostic interface for LLM inference.
 */
interface LlmInterface {
    /** Unique identifier for this model, e.g. "gemma-4-e2b-litertlm". */
    val modelId: String

    suspend fun generate(request: GenerateRequest, onToken: (String) -> Unit)
}

/**
 * Extended interface for models that support loading-phase feedback (e.g. "Loading model…").
 * Orchestrator uses this when available for better UX.
 */
interface PhasedLlm : LlmInterface {
    suspend fun generateWithPhase(
        request: GenerateRequest,
        onToken: (String) -> Unit,
        onPhaseChange: (LoadingPhase) -> Unit
    )
}

/**
 * Request for text generation. Model-agnostic.
 */
data class GenerateRequest(
    val prompt: String,
    val settings: InferenceSettings
)

/**
 * Inference parameters used across all models.
 */
@Serializable
data class InferenceSettings(
    val temperature: Float = 0.80f,
    /** Max new tokens per reply; must leave room for priming + history inside [LlmDefaults.CONTEXT_LENGTH_TOKENS]. */
    val maxTokens: Int = LlmDefaults.DEFAULT_MAX_NEW_TOKENS,
    /**
     * Reserved for future battery/thermal tuning. The native runtime currently always uses the normal
     * capped thread count ([LlmDefaults.LLM_INFERENCE_THREADS_CAP]) regardless of this flag.
     */
    val lowPowerMode: Boolean = false,
    /**
     * BCP 47 tag (e.g. `en-US`, `es-ES`) passed to the system speech recognizer for voice input.
     */
    val speechInputLocaleTag: String = "en-US",
    /**
     * When true, assistant replies are read aloud with on-device TTS (FastSpeech + vocoder via ONNX Runtime).
     */
    val assistantSpeechEnabled: Boolean = false,
    /**
     * Read-aloud (TTS) language code. UI currently offers Telugu only (`te`).
     */
    val ttsLanguage: String = "te",
    /**
     * Reserved read-aloud voice selector for future re-enable.
     */
    val ttsVoiceGender: String = "female",
    /**
     * Set to false until the user finishes the first-run language/voice sheet. Decodes as `true` when
     * missing in stored JSON so existing installs are not blocked.
     */
    val voiceOnboardingComplete: Boolean = false,
)
