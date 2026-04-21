package ai.gyango.assistant

/**
 * **Single entry point** for assistant message hygiene in GyanGo.
 *
 * - **[sanitizeLlm]**: raw model output (including saved history replay) — tags, leaks, list/math glue.
 * - **[newLlmStreamSanitizer]**: token/chunk stream before aggregation; call [AssistantLlmStreamSanitizer.finish] at end.
 * - **[formatForDisplay]**: full bubble / clipboard / TTS string — see [AssistantParsingPipeline] for stage order.
 * - **[stripInlineMarkdown]**: per-line cleanup when the UI splits text into blocks.
 *
 * Prefer this object (or [AssistantParsingPipeline]) from app code; lower-level helpers stay
 * package-private implementation details.
 */
object AssistantOutput {
    fun sanitizeLlm(input: String): String = AssistantLlmSanitizer.sanitize(input)

    fun newLlmStreamSanitizer(): AssistantLlmStreamSanitizer = AssistantLlmStreamSanitizer()

    fun formatForDisplay(raw: String): String = AssistantParsingPipeline.formatForDisplay(raw)

    fun stripInlineMarkdown(line: String): String = AssistantTextPolisher.stripInlineMarkdown(line)

    /** Lesson body for the assistant card (after `---OUTPUT---`, before `STATE >>` / sparks / save-context markers). */
    fun extractLessonBodyForAssistantCard(text: String): String? {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(text)
        return GyangoOutputEnvelope.parseLessonBlock(trimmed)
    }

    /**
     * Structured sections for chat bubbles: explicit Definition / Analogy / Application when all three
     * headers exist; otherwise up to three paragraphs with optional analogy callout only when the
     * paragraph opens with an explicit Analogy header.
     */
    fun lessonBubblePresentation(raw: String): AssistantLessonBubblePresentation {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(raw)
        if (!GyangoOutputEnvelope.isOutputFormat(trimmed)) {
            val tripleProbe = GyangoOutputEnvelope.textBeforeSparksOrHint(trimmed)
            val sanitizedProbe = AssistantLlmSanitizer.sanitize(tripleProbe)
            AssistantLessonStructureParser.explicitLabeledTripleOrNull(sanitizedProbe)?.let { return it }
            val plain = formatForDisplay(trimmed)
            return AssistantLessonBubblePresentation(
                explicitLabeledTriple = false,
                applyFallbackTintSurfaces = false,
                sections = listOf(
                    AssistantLessonBubbleSection(
                        polishedBody = plain,
                        caption = null,
                        useAnalogyCallout = false,
                        fallbackTintIndex = 0,
                    ),
                ),
            )
        }
        val lesson = GyangoOutputEnvelope.parseLessonBlock(trimmed)
            ?: GyangoOutputEnvelope.streamingLessonPreview(trimmed)
        if (lesson.isNullOrBlank()) {
            return AssistantLessonBubblePresentation(
                explicitLabeledTriple = false,
                applyFallbackTintSurfaces = false,
                sections = emptyList(),
            )
        }
        val san = AssistantLlmSanitizer.sanitize(lesson)
        return AssistantLessonStructureParser.fromSanitizedLesson(san)
    }

    /** Raw sparks line after `---SPARKS---` (chips split with `||` for UI). */
    fun extractSparksCsv(text: String): String? {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(text)
        return GyangoOutputEnvelope.parseSparksSection(trimmed)
    }

    /** `[SAVE_CONTEXT]` body for `M:` on the next turn (not shown in UI). */
    fun extractOutputMemoryHint(text: String): String? {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(text)
        return GyangoOutputEnvelope.parseHintInner(trimmed)
    }

    /** `[USER_CURIOSITY]` body for local interest logging (not shown in the chat bubble). */
    fun extractInterestInner(text: String): String? {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(text)
        return GyangoOutputEnvelope.parseInterestInner(trimmed)
    }

    /** `STATE >>` line payload (not shown in the chat bubble). */
    fun extractInternalInner(text: String): String? {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(text)
        return GyangoOutputEnvelope.parseInternalInner(trimmed)
    }
}
