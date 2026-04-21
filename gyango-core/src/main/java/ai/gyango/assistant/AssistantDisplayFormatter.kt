package ai.gyango.assistant

/**
 * Thin compatibility shim: delegates to [AssistantParsingPipeline] / [AssistantTextPolisher].
 */
object AssistantDisplayFormatter {

    fun formatForDisplay(raw: String): String = AssistantParsingPipeline.formatForDisplay(raw)

    fun stripInlineMarkdown(input: String): String = AssistantTextPolisher.stripInlineMarkdown(input)
}
