package ai.gyango.assistant

/**
 * Orchestrates `---OUTPUT---` / `---SPARKS---` envelope handling and plain-text polish.
 */
object AssistantParsingPipeline {

    /**
     * Normalize raw model output before display extraction (outer markdown fences, trim).
     */
    fun normalizeEnvelopeInput(raw: String): String {
        var s = raw.trim()
        s = AssistantTextPolisher.stripOuterMarkdownCodeFence(s).trim()
        s = GyangoOutputEnvelope.coerceMissingOutputMarkerIfNeeded(s)
        return s
    }

    /**
     * Raw assistant string → user-visible prose (bubble, copy, TTS): OUTPUT merge / stream preview,
     * otherwise sanitized plain text + [AssistantTextPolisher.polishDisplayText].
     */
    fun formatForDisplay(raw: String): String {
        val trimmed = normalizeEnvelopeInput(raw)
        if (GyangoOutputEnvelope.isOutputFormat(trimmed)) {
            GyangoOutputEnvelope.mergeForDisplayPolished(trimmed)?.let { return it }
            GyangoOutputEnvelope.streamingLessonPreview(trimmed)?.let {
                return AssistantTextPolisher.polishDisplayText(AssistantLlmSanitizer.sanitize(it))
            }
            return ""
        }
        val s = AssistantLlmSanitizer.sanitize(trimmed)
        return AssistantTextPolisher.polishDisplayText(s)
    }

    /** Sanitize streaming aggregate text (no structured envelope assumptions). */
    fun sanitizeAggregateAfterEnvelopeNormalized(envelopeNormalized: String): String =
        AssistantLlmSanitizer.sanitize(envelopeNormalized)
}
