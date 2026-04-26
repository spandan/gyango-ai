package ai.gyango.assistant

/**
 * Assistant reply: markdown answer body, then:
 *
 * ```
 * ---
 * CONTEXT >> <one line for next-turn M:>
 * CURIOSITY >> <one line for analytics / interest>
 * SPARKS >> <easy>||<medium>||<hard>
 * ```
 */
object GyangoOutputEnvelope {

    const val METADATA_SEPARATOR = "\n---\n"
    const val CONTEXT_LINE_PREFIX = "CONTEXT >>"
    const val CURIOSITY_LINE_PREFIX = "CURIOSITY >>"
    const val SPARKS_LINE_PREFIX = "SPARKS >>"
    const val SPARK_CHIPS_DELIMITER = "||"

    /** Strips echoed `<…>` placeholders the model copies from the prompt. */
    private val sparkAngleBracketPlaceholder = Regex("""(?i)^\s*<[^>\n]{0,64}>\s*""")

    /** Strips echoed difficulty / follow-up labels (model often mirrors prompt placeholders). */
    private val sparkChipDifficultyPrefix = Regex(
        """(?i)^\s*(?:easy|easier|medium|mid|harder|hard|hardest)(?:\s*[-]?\s*follow[-\s]?up)?\s*[:.\-–]\s*""",
    )

    private val sparkFollowupOnlyPrefix = Regex("""(?i)^\s*follow\s*[-\s]?up\s*[:.\-–]\s*""")

    /** Strips prompt-echo tier labels (models mirror `Ext:` / `Deep:` / `Apply:` from metadata instructions). */
    private val sparkExtDeepApplyLabelPrefix = Regex("""(?i)^\s*(?:ext|deep|apply)\s*:\s*""")

    private val contextMarker = Regex("""(?mi)^\s*CONTEXT\s*(?:>>|>|:)\s*""")
    private val curiosityMarker = Regex("""(?mi)^\s*CURIOSITY\s*(?:>>|>|:)\s*""")
    private val sparksMarker = Regex("""(?mi)^\s*SPARKS\s*(?:>>|>|:)\s*""")
    private val leakedTailHeaderMarker = Regex("""(?mi)^\s*(?:[*_`#>\-]+\s*)*(?:#\s*)?TAIL\b.*$""")
    private val leakedFormatHeaderMarker = Regex("""(?mi)^\s*(?:#\s*)?FORMAT\s*\(STRICT\)\s*$""")
    private val leakedSessionHeaderMarker = Regex("""(?mi)^\s*(?:#\s*)?SESSION\s*$""")
    private val leakedMissionHeaderMarker = Regex("""(?mi)^\s*(?:#\s*)?MISSION\s*$""")
    private val leakedActivationMarker = Regex("""(?mi)^\s*\[(?:ACTIVATE|DOMAIN):[^\]]*]""")

    fun normalize(raw: String): String =
        AssistantTextPolisher.stripOuterMarkdownCodeFence(raw.trim()).trim()

    fun hasMetadataTail(raw: String): Boolean {
        val n = normalize(raw)
        return contextMarker.containsMatchIn(n) &&
            curiosityMarker.containsMatchIn(n) &&
            sparksMarker.containsMatchIn(n)
    }

    fun hasClosedMetadataTail(raw: String): Boolean {
        val n = normalize(raw)
        if (!hasMetadataTail(n)) return false
        return !parseContextLine(n).isNullOrBlank() &&
            !parseCuriosityLine(n).isNullOrBlank() &&
            !parseSparksLine(n).isNullOrBlank()
    }

    /** @deprecated alias */
    fun isLessonEnvelope(raw: String): Boolean = hasMetadataTail(raw)

    /** @deprecated alias */
    fun hasClosedLessonTail(raw: String): Boolean = hasClosedMetadataTail(raw)

    private fun answerEndIndex(n: String): Int {
        val sep = n.indexOf(METADATA_SEPARATOR)
        if (sep >= 0) return sep
        val firstMeta = listOfNotNull(
            contextMarker.find(n)?.range?.first,
            curiosityMarker.find(n)?.range?.first,
            sparksMarker.find(n)?.range?.first,
        ).minOrNull() ?: return n.length
        var cut = firstMeta
        while (cut > 0 && (n[cut - 1] == '\n' || n[cut - 1] == '\r')) cut--
        return cut.coerceAtLeast(0)
    }

    fun parseAnswerBody(raw: String): String? {
        val n = normalize(raw)
        if (!hasMetadataTail(n)) return null
        val end = answerEndIndex(n)
        return n.substring(0, end).trim().takeIf { it.isNotBlank() }
    }

    /** @deprecated use [parseAnswerBody] */
    fun parseLessonBlock(raw: String): String? = parseAnswerBody(raw)

    fun parseContextLine(raw: String): String? = parseTailLineBody(normalize(raw), contextMarker)
    fun parseCuriosityLine(raw: String): String? = parseTailLineBody(normalize(raw), curiosityMarker)
    fun parseSparksLine(raw: String): String? = parseTailLineBody(normalize(raw), sparksMarker)

    fun parseContextForNextTurn(raw: String): String? =
        parseContextLine(raw)?.trim()?.takeIf { it.isNotBlank() }

    /** @deprecated */
    fun parseHintInner(raw: String): String? = parseContextLine(raw)

    fun parseSparksSection(raw: String): String? = parseSparksLine(raw)

    /** @deprecated */
    fun parseInterestInner(raw: String): String? = parseCuriosityLine(raw)

    fun parseSparkChips(sparksLine: String?): List<String> {
        val s = sparksLine?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
        fun stripChipLabels(chip: String): String {
            var c = chip.trim()
            var prev: String
            do {
                prev = c
                c = sparkAngleBracketPlaceholder.replace(c, "").trim()
                c = sparkChipDifficultyPrefix.replace(c, "").trim()
                c = sparkFollowupOnlyPrefix.replace(c, "").trim()
                c = sparkExtDeepApplyLabelPrefix.replace(c, "").trim()
            } while (c != prev && c.isNotEmpty())
            return c
        }
        return s.split(SPARK_CHIPS_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { stripChipLabels(it) }
            .filter { it.isNotEmpty() }
            .take(3)
    }

    fun streamingLessonPreview(raw: String): String? {
        val n = normalize(raw)
        if (!hasMetadataTail(n)) return null
        return parseAnswerBody(raw)
    }

    fun streamingLessonPartial(raw: String): String? {
        val n = normalize(raw)
        if (hasMetadataTail(n)) return parseAnswerBody(raw)
        val cut = firstPrematureMetadataOrLeakIndex(n)
        if (cut >= 0) return n.substring(0, cut).trimEnd()
        return null
    }

    private fun firstPrematureMetadataOrLeakIndex(n: String): Int {
        var cut = n.length
        val sep = n.indexOf(METADATA_SEPARATOR)
        if (sep >= 0) cut = minOf(cut, sep)
        markerStart(n, contextMarker)?.let { cut = minOf(cut, it) }
        markerStart(n, curiosityMarker)?.let { cut = minOf(cut, it) }
        markerStart(n, sparksMarker)?.let { cut = minOf(cut, it) }
        markerStart(n, leakedTailHeaderMarker)?.let { cut = minOf(cut, it) }
        markerStart(n, leakedFormatHeaderMarker)?.let { cut = minOf(cut, it) }
        markerStart(n, leakedSessionHeaderMarker)?.let { cut = minOf(cut, it) }
        markerStart(n, leakedMissionHeaderMarker)?.let { cut = minOf(cut, it) }
        markerStart(n, leakedActivationMarker)?.let { cut = minOf(cut, it) }
        return if (cut < n.length) cut else -1
    }

    fun mergeForDisplayPolished(raw: String): String? {
        val body = parseAnswerBody(raw) ?: return null
        val san = AssistantLlmSanitizer.sanitize(body)
        return AssistantTextPolisher.polishDisplayTextForMarkdown(san).takeIf { it.isNotBlank() }
    }

    private fun markerStart(text: String, marker: Regex): Int? =
        marker.find(text)?.range?.first

    private fun parseTailLineBody(text: String, marker: Regex): String? {
        val m = marker.find(text) ?: return null
        val start = m.range.last + 1
        val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        return text.substring(start, lineEnd).trim().takeIf { it.isNotBlank() }
    }
}
