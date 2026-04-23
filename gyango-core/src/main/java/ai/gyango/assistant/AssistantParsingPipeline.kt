package ai.gyango.assistant

import ai.gyango.core.SubjectMode

/** Strip outer fences / trim, then answer body merge or plain polish for display. */
object AssistantParsingPipeline {

    fun normalizeEnvelopeInput(raw: String): String =
        AssistantTextPolisher.stripOuterMarkdownCodeFence(raw.trim()).trim()

    fun formatForDisplay(raw: String): String = parseForDisplay(raw).displayText

    fun parseForDisplay(raw: String, @Suppress("UNUSED_PARAMETER") subjectMode: SubjectMode? = null): AssistantParseResult {
        val trimmed = normalizeEnvelopeInput(raw)
        if (GyangoOutputEnvelope.hasMetadataTail(trimmed)) {
            val tailComplete = GyangoOutputEnvelope.hasClosedMetadataTail(trimmed)
            GyangoOutputEnvelope.mergeForDisplayPolished(trimmed)?.let {
                return AssistantParseResult(
                    displayText = it,
                    status = if (tailComplete) AssistantParseStatus.VALID else AssistantParseStatus.PARTIAL,
                    invalidReason = if (tailComplete) null else "metadata_incomplete",
                    topicContractValid = tailComplete,
                    tailComplete = tailComplete,
                )
            }
            GyangoOutputEnvelope.streamingLessonPreview(trimmed)?.let {
                return AssistantParseResult(
                    displayText = AssistantTextPolisher.polishDisplayTextForMarkdown(AssistantLlmSanitizer.sanitize(it)),
                    status = AssistantParseStatus.PARTIAL,
                    invalidReason = if (tailComplete) null else "metadata_incomplete",
                    topicContractValid = tailComplete,
                    tailComplete = tailComplete,
                )
            }
            return AssistantParseResult(
                displayText = "",
                status = AssistantParseStatus.INVALID,
                invalidReason = "empty_answer_block",
                topicContractValid = false,
                tailComplete = tailComplete,
            )
        }
        GyangoOutputEnvelope.streamingLessonPartial(trimmed)?.let {
            return AssistantParseResult(
                displayText = AssistantTextPolisher.polishDisplayTextForMarkdown(AssistantLlmSanitizer.sanitize(it)),
                status = AssistantParseStatus.PARTIAL,
                invalidReason = "tail_incomplete",
                topicContractValid = false,
                tailComplete = false,
            )
        }
        val s = AssistantLlmSanitizer.sanitize(trimmed)
        return AssistantParseResult(
            displayText = AssistantTextPolisher.polishDisplayTextForMarkdown(s),
            status = AssistantParseStatus.PARTIAL,
            invalidReason = "non_envelope_output",
            topicContractValid = false,
            tailComplete = false,
        )
    }

    fun sanitizeAggregateAfterEnvelopeNormalized(envelopeNormalized: String): String =
        AssistantLlmSanitizer.sanitize(envelopeNormalized)
}

enum class AssistantParseStatus {
    VALID,
    PARTIAL,
    INVALID,
}

data class AssistantParseResult(
    val displayText: String,
    val status: AssistantParseStatus,
    val invalidReason: String?,
    val topicContractValid: Boolean,
    val tailComplete: Boolean,
)
