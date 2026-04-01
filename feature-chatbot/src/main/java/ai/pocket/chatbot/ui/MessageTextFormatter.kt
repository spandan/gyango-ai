package ai.pocket.chatbot.ui

import ai.pocket.api.ResponseSanitizer

object MessageTextFormatter {

    /**
     * Full pipeline for on-screen assistant bubbles: LLM sanitization, dedupe noise, strip markdown-like
     * delimiters so the model can drift without showing raw `**` or `#` in the UI.
     */
    fun readableAssistantText(raw: String): String {
        var s = ResponseSanitizer.sanitize(raw)
        s = normalizeMarkdownUnicode(s)
        s = dedupeRepeatedParagraphs(s)
        s = dedupeConsecutiveDuplicateLines(s)
        s = normalizeWhitespaceForPlainText(s)
        // Note: we don't strip everything here yet because buildDisplayBlocks 
        // needs some markers (like # or 1.) to identify structure.
        return s.trim()
    }

    /**
     * Splits cleaned assistant text into [AssistantDisplayBlock]s for structured Compose layout
     * (lists, labels, paragraph breaks) without interpreting markdown.
     */
    fun assistantDisplayBlocks(raw: String): List<AssistantDisplayBlock> {
        val clean = readableAssistantText(raw)
        return buildDisplayBlocks(clean)
    }

    private val orderedLine = Regex("""^(\d+)\.\s+(.+)$""")
    private val bulletLine = Regex("""^[-*•]\s+(.+)$""")
    private val markdownHeader = Regex("""^#{1,6}\s+(.+)$""")

    private fun buildDisplayBlocks(clean: String): List<AssistantDisplayBlock> {
        val lines = clean.replace("\r\n", "\n").split('\n')
        val out = mutableListOf<AssistantDisplayBlock>()
        val paragraph = StringBuilder()
        var blankRun = 0
        var currentOrderedIndex = 0

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            val text = stripMarkdownLikeDelimiters(paragraph.toString().trim())
            if (text.isNotEmpty()) {
                out.add(AssistantDisplayBlock.Paragraph(text))
            }
            paragraph.clear()
        }

        fun isSectionLabel(t: String): Boolean {
            // Treat lines ending in colon as labels
            if (t.length in 2..80 && t.endsWith(':')) {
                if (t.startsWith('-') || t.startsWith('•') || t.startsWith('*')) return false
                if (orderedLine.matches(t)) return false
                if (t.contains("://")) return false
                return true
            }
            return false
        }

        for (line in lines) {
            if (line.isBlank()) {
                blankRun++
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
                    out.add(AssistantDisplayBlock.BulletItem(stripMarkdownLikeDelimiters(bulletMatch.groupValues[1].trim())))
                }
                orderedMatch != null -> {
                    flushParagraph()
                    val parsedIndex = orderedMatch.groupValues[1].toIntOrNull() ?: 1
                    // If model repeats "1." for every item, we auto-increment
                    if (parsedIndex == 1 && currentOrderedIndex >= 1) {
                        currentOrderedIndex++
                    } else {
                        currentOrderedIndex = parsedIndex
                    }
                    out.add(AssistantDisplayBlock.OrderedItem(currentOrderedIndex, stripMarkdownLikeDelimiters(orderedMatch.groupValues[2].trim())))
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
        }
        flushParagraph()
        
        // Final cleanup: remove trailing breaks
        if (out.isNotEmpty() && out.last() is AssistantDisplayBlock.ParagraphBreak) {
            out.removeAt(out.size - 1)
        }
        
        return out
    }

    private fun normalizeMarkdownUnicode(s: String): String =
        s.replace('\uFF0A', '*')
         .replace('\u2217', '*')
         .replace("\uFEFF", "")

    private fun dedupeRepeatedParagraphs(text: String): String {
        val paras = text.split(Regex("\n\n+"))
        if (paras.size < 2) return text
        fun norm(p: String) = p.trim().lowercase().replace(Regex("\\s+"), " ")
        val out = mutableListOf<String>()
        var prevKey: String? = null
        for (p in paras) {
            val key = norm(p)
            if (key.isNotEmpty() && key == prevKey) continue
            out.add(p)
            prevKey = if (key.isNotEmpty()) key else null
        }
        return out.joinToString("\n\n")
    }

    private fun dedupeConsecutiveDuplicateLines(text: String): String {
        val lines = text.lines()
        val out = mutableListOf<String>()
        var prevNonBlank: String? = null
        for (line in lines) {
            val t = line.trim()
            if (t.isNotEmpty() && t == prevNonBlank) continue
            out.add(line)
            prevNonBlank = if (t.isNotEmpty()) t else prevNonBlank
        }
        return out.joinToString("\n")
    }

    private fun normalizeWhitespaceForPlainText(input: String): String {
        var s = input.trim().replace("\r\n", "\n")
        s = s.replace(Regex("\n{4,}"), "\n\n\n")
        s = s.replace('\u00A0', ' ')
        return s
    }

    private fun stripMarkdownLikeDelimiters(input: String): String {
        var s = input
        // Remove bold/italic markers while keeping text
        s = s.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        s = s.replace(Regex("""\*([^*]+)\*"""), "$1")
        s = s.replace(Regex("""__([^_]+)__"""), "$1")
        s = s.replace(Regex("""_([^_]+)_"""), "$1")
        s = s.replace(Regex("""`([^`]+)`"""), "$1")
        // Remove leading # markers if any leaked through
        s = s.replace(Regex("""(?m)^#{1,6}\s*"""), "")
        return s.trim()
    }
}
