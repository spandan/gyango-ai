package ai.pocket.core

/**
 * Prompt / settings budgeting for on-device LLM ([ai.pocket.api.PromptBuilder]). LiteRT-LM applies
 * its own context limits from the bundled model.
 */
object LlmDefaults {
    const val CONTEXT_LENGTH_TOKENS: Int = 8192

    /** Kept for API compatibility with settings UI. */
    const val LLM_INFERENCE_THREADS_CAP: Int = 4

    /**
     * Default max new tokens per generation. Higher values reduce mid-sentence cutoffs at the cost of
     * latency and battery. Users can lower this in chat settings.
     */
    const val DEFAULT_MAX_NEW_TOKENS: Int = 512

    /** Hard cap for settings UI / headroom math — keep below context minus worst-case prompt. */
    const val MAX_NEW_TOKENS_CAP: Int = 1024
}
