package ai.gyango.core

/**
 * Prompt / settings budgeting for on-device LLM ([ai.gyango.api.PromptBuilder]).
 */
object LlmDefaults {
    /**
     * LiteRT [EngineConfig.maxNumTokens] (KV / sequence budget). Must comfortably exceed the longest
     * prompt plus [MAX_NEW_TOKENS_CAP] so decode does not thrash; align exported `kv_cache_max_len`
     * with this value when converting models (see `gyango_pad_model_source/README.txt`).
     */
    const val CONTEXT_LENGTH_TOKENS: Int = 4096

    /**
     * Low-RAM mode context when [InferenceSettings.lowPowerMode] is enabled — still large enough
     * for multi-turn prompts plus a long decode at reduced [LOW_RAM_MAX_NEW_TOKENS_CAP].
     */
    const val LOW_RAM_CONTEXT_LENGTH_TOKENS: Int = 3072

    /**
     * Default max new tokens per generation — below [MAX_NEW_TOKENS_CAP]; adjust with device/RAM in mind.
     */
    const val DEFAULT_MAX_NEW_TOKENS: Int = 1024

    /** Hard cap for settings UI / decode stop — keep below [CONTEXT_LENGTH_TOKENS] minus prompt headroom. */
    const val MAX_NEW_TOKENS_CAP: Int = 1024

    /** Runtime cap while low-power mode is active (smaller decode to protect RAM/thermals). */
    const val LOW_RAM_MAX_NEW_TOKENS_CAP: Int = 350

    /** LiteRT sampling defaults tuned for coherent professional responses. */
    const val LITERT_DEFAULT_TOP_P: Double = 0.92
    const val LITERT_DEFAULT_TOP_K: Int = 48
    const val LITERT_LOW_POWER_TOP_P: Double = 0.90
    const val LITERT_LOW_POWER_TOP_K: Int = 32
    const val LITERT_MIN_TEMPERATURE: Float = 0.2f
    const val LITERT_MAX_TEMPERATURE: Float = 1.2f
}
