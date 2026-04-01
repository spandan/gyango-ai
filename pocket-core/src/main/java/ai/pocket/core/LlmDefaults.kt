package ai.pocket.core

/**
 * Must match native `n_ctx` ([RwkvLlm], [NativeLlmBridge]) and [ai.pocket.api.PromptBuilder] budgeting.
 * Lower values reduce allocator pressure and peak RSS on phones.
 */
object LlmDefaults {
    const val CONTEXT_LENGTH_TOKENS: Int = 2048

    /**
     * Upper bound on llama.cpp worker threads for phones. Flagship SoCs like Tensor G4
     * can handle more, but we cap to 4 to balance speed and thermal throttling.
     */
    const val LLM_INFERENCE_THREADS_CAP: Int = 4

    /**
     * Default max new tokens per generation. Higher values reduce mid-sentence cutoffs at the cost of
     * latency and battery. Users can lower this in chat settings.
     */
    const val DEFAULT_MAX_NEW_TOKENS: Int = 512

    /** Hard cap for settings UI / headroom math — keep below context minus worst-case prompt. */
    const val MAX_NEW_TOKENS_CAP: Int = 1024
}
