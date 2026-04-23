package ai.gyango.assistant

import ai.gyango.core.SubjectMode

/**
 * Assistant message hygiene: sanitize, stream sanitizer, display formatting, lesson bubble layout.
 */
object AssistantOutput {
    fun sanitizeLlm(input: String): String = AssistantLlmSanitizer.sanitize(input)

    fun newLlmStreamSanitizer(): AssistantLlmStreamSanitizer = AssistantLlmStreamSanitizer()

    fun formatForDisplay(raw: String): String = AssistantParsingPipeline.formatForDisplay(raw)

    fun parseForDisplay(raw: String, subjectMode: SubjectMode? = null): AssistantParseResult =
        AssistantParsingPipeline.parseForDisplay(raw, subjectMode)

    fun stripInlineMarkdown(line: String): String = AssistantTextPolisher.stripInlineMarkdown(line)

    fun lessonBubblePresentation(raw: String): AssistantLessonBubblePresentation {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(raw)
        val lesson = GyangoOutputEnvelope.parseAnswerBody(trimmed)
            ?: GyangoOutputEnvelope.streamingLessonPartial(trimmed)
        if (lesson.isNullOrBlank()) {
            val plain = formatForDisplay(trimmed)
            return AssistantLessonBubblePresentation(
                sections = if (plain.isNotBlank()) listOf(plain) else emptyList(),
            )
        }
        val polished = AssistantTextPolisher.polishDisplayTextForMarkdown(AssistantLlmSanitizer.sanitize(lesson))
        return AssistantLessonBubblePresentation(sections = listOf(polished))
    }

    fun extractSparksCsv(text: String): String? {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(text)
        return GyangoOutputEnvelope.parseSparksSection(trimmed)
    }

    fun extractOutputContext(text: String): String? {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(text)
        return GyangoOutputEnvelope.parseContextForNextTurn(trimmed)
    }

    fun extractCuriosity(text: String): String? {
        val trimmed = AssistantParsingPipeline.normalizeEnvelopeInput(text)
        return GyangoOutputEnvelope.parseCuriosityLine(trimmed)
    }
}

data class AssistantLessonBubblePresentation(
    val sections: List<String>,
) {
    fun joinedForPlainDisplay(): String = sections.joinToString("\n\n") { it }
}
