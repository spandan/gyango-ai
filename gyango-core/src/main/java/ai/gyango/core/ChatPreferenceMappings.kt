package ai.gyango.core

/**
 * Maps user-facing chat preferences (plain language) to [InferenceSettings] numeric fields.
 */
object ChatPreferenceMappings {
    /** Default max new tokens for new installs (matches [LlmDefaults.MAX_NEW_TOKENS_CAP]). */
    const val MAX_TOKENS_SHORT_ANSWERS: Int = 1024

    /** “Long answers” upper band (same as engine cap until higher decode budgets are supported). */
    const val MAX_TOKENS_LONG_ANSWERS: Int = 1024

    fun isLongAnswersEnabled(maxTokens: Int): Boolean =
        maxTokens >= MAX_TOKENS_LONG_ANSWERS

    fun maxTokensForLongAnswersToggle(longAnswers: Boolean): Int =
        if (longAnswers) MAX_TOKENS_LONG_ANSWERS else MAX_TOKENS_SHORT_ANSWERS
}
