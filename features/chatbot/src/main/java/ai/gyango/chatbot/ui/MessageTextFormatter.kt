package ai.gyango.chatbot.ui

import ai.gyango.assistant.AssistantOutput

/**
 * Compose-oriented splitting of assistant text into [AssistantDisplayBlock]s.
 *
 * Envelope normalization + extraction + polish: [ai.gyango.assistant.AssistantParsingPipeline]
 * and [ai.gyango.assistant.AssistantOutput].
 * This object only handles line structure for the UI.
 */
object MessageTextFormatter {

    fun readableAssistantText(raw: String): String = AssistantOutput.formatForDisplay(raw)

    fun assistantDisplayBlocks(raw: String): List<AssistantDisplayBlock> {
        val clean = readableAssistantText(raw)
        return buildDisplayBlocks(clean)
    }

    /** When text was already passed through [AssistantOutput.formatForDisplay]. */
    fun assistantDisplayBlocksFromPolished(polished: String): List<AssistantDisplayBlock> =
        buildDisplayBlocks(polished.replace("\r\n", "\n").trim())

    private val orderedLine = Regex("""^(\d+)\.\s*(.+)$""")
    private val bulletLine = Regex("""^[-*•]\s+(.+)$""")
    private val markdownHeader = Regex("""^#{1,6}\s+(.+)$""")
    private val fencedCodeStart = Regex("""^```(?:\w+)?\s*$""")
    private val fencedCodeEnd = Regex("""^```\s*$""")

    private fun buildDisplayBlocks(clean: String): List<AssistantDisplayBlock> {
        val lines = clean.replace("\r\n", "\n").split('\n')
        val out = mutableListOf<AssistantDisplayBlock>()
        val paragraph = StringBuilder()
        var blankRun = 0
        var currentOrderedIndex = 0

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            val text = paragraph.toString().trim()
            if (text.isNotEmpty()) {
                out.add(AssistantDisplayBlock.Paragraph(text))
            }
            paragraph.clear()
        }

        fun isSectionLabel(t: String): Boolean {
            if (t.length in 2..80 && t.endsWith(':')) {
                if (t.startsWith('-') || t.startsWith('•') || t.startsWith('*')) return false
                if (orderedLine.matches(t)) return false
                if (t.contains("://")) return false
                return true
            }
            return false
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                blankRun++
                i++
                continue
            }

            val t = line.trim()

            if (blankRun > 0) {
                flushParagraph()
                if (out.isNotEmpty() && out.last() !is AssistantDisplayBlock.ParagraphBreak) {
                    out.add(AssistantDisplayBlock.ParagraphBreak)
                }
                blankRun = 0
            }

            if (fencedCodeStart.matches(t)) {
                flushParagraph()
                currentOrderedIndex = 0
                i++
                val codeLines = mutableListOf<String>()
                while (i < lines.size && !fencedCodeEnd.matches(lines[i].trim())) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size && fencedCodeEnd.matches(lines[i].trim())) {
                    i++
                }
                val code = codeLines.joinToString("\n").trimEnd()
                if (code.isNotBlank()) {
                    out.add(AssistantDisplayBlock.CodeBlock(code))
                }
                continue
            }

            val headerMatch = markdownHeader.matchEntire(t)
            val bulletMatch = bulletLine.matchEntire(t)
            val orderedMatch = orderedLine.matchEntire(t)

            when {
                headerMatch != null -> {
                    flushParagraph()
                    currentOrderedIndex = 0
                    out.add(AssistantDisplayBlock.SectionLabel(headerMatch.groupValues[1].trim().removeSuffix(":")))
                }
                bulletMatch != null -> {
                    flushParagraph()
                    currentOrderedIndex = 0
                    out.add(AssistantDisplayBlock.BulletItem(bulletMatch.groupValues[1].trim()))
                }
                orderedMatch != null -> {
                    flushParagraph()
                    val parsedIndex = orderedMatch.groupValues[1].toIntOrNull() ?: 1
                    if (parsedIndex == 1 && currentOrderedIndex >= 1) {
                        currentOrderedIndex++
                    } else {
                        currentOrderedIndex = parsedIndex
                    }
                    out.add(AssistantDisplayBlock.OrderedItem(currentOrderedIndex, orderedMatch.groupValues[2].trim()))
                }
                isSectionLabel(t) -> {
                    flushParagraph()
                    currentOrderedIndex = 0
                    out.add(AssistantDisplayBlock.SectionLabel(t.removeSuffix(":").trim()))
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(t)
                }
            }
            i++
        }
        flushParagraph()

        if (out.isNotEmpty() && out.last() is AssistantDisplayBlock.ParagraphBreak) {
            out.removeAt(out.size - 1)
        }

        return out
    }
}
