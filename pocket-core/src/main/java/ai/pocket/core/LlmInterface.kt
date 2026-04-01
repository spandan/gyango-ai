package ai.pocket.core

/**
 * Model-agnostic interface for LLM inference.
 */
interface LlmInterface {
    /** Unique identifier for this model, e.g. "rwkv7-g1e-1.5b-q4_k_m". */
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
data class InferenceSettings(
    val temperature: Float = 0.60f,
    /** Max new tokens per reply; must leave room for priming + history inside [LlmDefaults.CONTEXT_LENGTH_TOKENS]. */
    val maxTokens: Int = LlmDefaults.DEFAULT_MAX_NEW_TOKENS,
    /**
     * Reserved for future battery/thermal tuning. The native runtime currently always uses the normal
     * capped thread count ([LlmDefaults.LLM_INFERENCE_THREADS_CAP]) regardless of this flag.
     */
    val lowPowerMode: Boolean = false
)
