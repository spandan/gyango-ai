package ai.gyango.core

/**
 * Maps user-facing chat preferences (plain language) to [InferenceSettings] numeric fields.
 */
object ChatPreferenceMappings {
    /** Chat setting when “long answers” is off — same decode cap as [MAX_TOKENS_LONG_ANSWERS] here. */
    const val MAX_TOKENS_SHORT_ANSWERS: Int = 1024

    /** “Long answers” upper band (still capped by [LlmDefaults.MAX_NEW_TOKENS_CAP] in repository). */
    const val MAX_TOKENS_LONG_ANSWERS: Int = 1024

    fun isLongAnswersEnabled(maxTokens: Int): Boolean = maxTokens >= 512

    fun maxTokensForLongAnswersToggle(longAnswers: Boolean): Int =
        if (longAnswers) MAX_TOKENS_LONG_ANSWERS else MAX_TOKENS_SHORT_ANSWERS
}
