package ai.gyango.core

import kotlinx.serialization.Serializable

/**
 * One locally stored “interest” row derived from an optional model `[USER_CURIOSITY]` block (see [ai.gyango.api.PromptBuilder]).
 * Never required for chat to work — if the tag is missing or malformed, we simply skip recording.
 */
@Serializable
data class InterestSignal(
    /** Short phrase from the model (already sanitized / length-capped). */
    val areaOfInterest: String,
    /** [SubjectMode.name] or `GENERAL` when no tile is selected. */
    val subjectKey: String,
    /** Trimmed start of the user’s question for context in the dashboard. */
    val userQuerySnippet: String,
    val recordedAtEpochMs: Long,
)

object InterestCapture {
    private val boilerplate = Regex(
        """(?is)\b(optional|omit|leave\s+blank|max\s*12|words?|tag)\b""",
    )

    /** Accept inner `[USER_CURIOSITY]` text or null if it should be ignored. */
    fun sanitizeInterestInner(raw: String): String? {
        val collapsed = raw.replace('\n', ' ').replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (collapsed.startsWith("[")) return null
        if (collapsed.length < 2) return null
        if (collapsed.length > 120) return collapsed.take(120).trimEnd()
        if (collapsed.length < 40 && boilerplate.containsMatchIn(collapsed)) return null
        return collapsed
    }
}
