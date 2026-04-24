package ai.gyango.core

/**
 * Prompt / settings budgeting for on-device LLM ([ai.gyango.api.PromptBuilder]).
 */
object LlmDefaults {

    /**
     * Decode sampling for a topic. **Phase 1:** preset-only ([topicSamplingForSubject]); no user-facing
     * settings to override temperature / top-p / top-k. Numeric knobs for several topics are the
     * `TOPIC_*` constants below.
     */
    fun samplingForSubject(subjectMode: SubjectMode?): TopicSampling =
        topicSamplingForSubject(subjectMode)

    const val TOPIC_MATH_SCIENCE_TEMPERATURE: Float = 0.2f
    const val TOPIC_MATH_SCIENCE_TOP_P: Float = 0.9f
    const val TOPIC_MATH_SCIENCE_TOP_K: Int = 40

    /** Midpoint of requested 1.0–1.2 range for coding topic. */
    const val TOPIC_CODING_TEMPERATURE: Float = 1.1f
    const val TOPIC_CODING_TOP_P: Float = 0.95f
    const val TOPIC_CODING_TOP_K: Int = 64

    /** Midpoint of requested 1.2–1.5 range for writing / creative topic. */
    const val TOPIC_WRITING_TEMPERATURE: Float = 1.35f
    const val TOPIC_WRITING_TOP_P: Float = 0.99f
    const val TOPIC_WRITING_TOP_K: Int = 100

    /** Midpoint of requested 0.7–0.8 range for exam preparation topic. */
    const val TOPIC_EXAM_PREP_TEMPERATURE: Float = 0.75f
    const val TOPIC_EXAM_PREP_TOP_P: Float = 0.95f
    const val TOPIC_EXAM_PREP_TOP_K: Int = 50

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

    /**
     * Standard LiteRT sampling preset for normal (non–low-power) generation.
     * Use these when resetting model sampling in UI or as serialized defaults.
     */
    const val SAMPLING_DEFAULT_TEMPERATURE: Float = 1.0f
    const val SAMPLING_DEFAULT_TOP_P: Float = 0.95f
    const val SAMPLING_DEFAULT_TOP_K: Int = 64

    /**
     * **Repetition penalty (educational policy):** keep at **[EDUCATIONAL_REPETITION_PENALTY_OFF]** (1.0,
     * disabled) or at most **[EDUCATIONAL_REPETITION_PENALTY_SLIGHT]** (1.1, slight). Higher penalties
     * often break the model’s ability to repeat technical definitions or math formulas that students
     * need for a mandatory level of understanding.
     *
     * `litertlm-android` 0.10.0 [com.google.ai.edge.litertlm.SamplerConfig] does not expose repetition
     * penalty (only top-k, top-p, temperature, seed)—the stack does not apply one from the app, which
     * matches **off**. If a future LiteRT-LM release adds it, wire **off** or **slight** here, not aggressive values.
     */
    const val EDUCATIONAL_REPETITION_PENALTY_OFF: Float = 1.0f
    const val EDUCATIONAL_REPETITION_PENALTY_SLIGHT: Float = 1.1f

    /** LiteRT sampling defaults when [InferenceSettings] does not override (legacy paths). */
    const val LITERT_DEFAULT_TOP_P: Double = SAMPLING_DEFAULT_TOP_P.toDouble()
    const val LITERT_DEFAULT_TOP_K: Int = SAMPLING_DEFAULT_TOP_K

    /** Allowed top-p range when applying topic presets or low-power overrides. */
    const val LITERT_MIN_TOP_P: Float = 0.5f
    const val LITERT_MAX_TOP_P: Float = 1.0f

    /** Allowed top-k range when applying topic presets or low-power overrides. */
    const val LITERT_MIN_TOP_K: Int = 8
    const val LITERT_MAX_TOP_K: Int = 128
    const val LITERT_LOW_POWER_TOP_P: Double = 0.90
    const val LITERT_LOW_POWER_TOP_K: Int = 32
    /** Floor low enough for math/science topic presets (0.1–0.3 range). */
    const val LITERT_MIN_TEMPERATURE: Float = 0.1f
    /** High enough for writing/creative topic presets (1.2–1.5) and coding (≤1.2). */
    const val LITERT_MAX_TEMPERATURE: Float = 1.5f
}
