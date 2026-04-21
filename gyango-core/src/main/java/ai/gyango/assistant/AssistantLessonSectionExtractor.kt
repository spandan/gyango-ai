package ai.gyango.assistant

/**
 * Reusable extractor for explicit sectioned lesson bodies.
 *
 * Given ordered or unordered section headers, returns each section body by key when all required
 * headers are present and non-overlapping. Caller controls regexes and output labels.
 */
object AssistantLessonSectionExtractor {
    data class SectionPattern(
        val key: String,
        val caption: String,
        val headerRegex: Regex,
    )

    data class ExtractedSection(
        val key: String,
        val caption: String,
        val body: String,
    )

    private data class HeaderHit(
        val key: String,
        val caption: String,
        val start: Int,
        val end: Int,
    )

    /**
     * Returns extracted sections when all [patterns] are found exactly once (first match per key),
     * non-overlapping, and each resolved body is non-blank. The result preserves [patterns] order.
     */
    fun extractAllOrNull(text: String, patterns: List<SectionPattern>): List<ExtractedSection>? {
        if (text.isBlank() || patterns.isEmpty()) return null
        val hits = patterns.map { pattern ->
            firstHit(text, pattern) ?: return null
        }
        val sorted = hits.sortedBy { it.start }
        for (i in 0 until sorted.lastIndex) {
            if (sorted[i].end > sorted[i + 1].start) return null
        }

        val bodiesByKey = mutableMapOf<String, String>()
        for (i in sorted.indices) {
            val current = sorted[i]
            val bodyEnd = if (i + 1 < sorted.size) sorted[i + 1].start else text.length
            bodiesByKey[current.key] = text.substring(current.end, bodyEnd).trim()
        }

        val result = patterns.map { pattern ->
            val body = bodiesByKey[pattern.key] ?: return null
            if (body.isBlank()) return null
            ExtractedSection(
                key = pattern.key,
                caption = pattern.caption,
                body = body,
            )
        }
        return result
    }

    private fun firstHit(text: String, pattern: SectionPattern): HeaderHit? {
        val match = pattern.headerRegex.find(text) ?: return null
        return HeaderHit(
            key = pattern.key,
            caption = pattern.caption,
            start = match.range.first,
            end = match.range.last + 1,
        )
    }
}
