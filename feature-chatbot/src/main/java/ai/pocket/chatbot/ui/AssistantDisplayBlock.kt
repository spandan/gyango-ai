package ai.pocket.chatbot.ui

/**
 * Structured chunks for assistant bubbles after sanitization — typography only, no markdown from the model.
 */
sealed class AssistantDisplayBlock {
    /** Blank-line gap between paragraphs (extra vertical space). */
    data object ParagraphBreak : AssistantDisplayBlock()

    data class Paragraph(val text: String) : AssistantDisplayBlock()
    data class SectionLabel(val text: String) : AssistantDisplayBlock()
    data class BulletItem(val text: String) : AssistantDisplayBlock()
    data class OrderedItem(val index: Int, val text: String) : AssistantDisplayBlock()
}
