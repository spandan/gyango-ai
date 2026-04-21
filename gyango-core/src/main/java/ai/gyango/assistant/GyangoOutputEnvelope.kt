package ai.gyango.assistant

/**
 * Structured assistant replies using `---OUTPUT---`, `---SPARKS---`, `STATE >>`, `[SAVE_CONTEXT]`,
 * and `[USER_CURIOSITY]` (see [ai.gyango.api.PromptBuilder]).
 */
object GyangoOutputEnvelope {
    const val OUTPUT_MARKER = "---OUTPUT---"
    const val SPARKS_MARKER = "---SPARKS---"
    /** Between the two short follow-up chips on the line after [SPARKS_MARKER]. */
    const val SPARK_CHIPS_DELIMITER = "||"
    /** Mentor-only state line prefix; rest of the line is Var/Goal/Skill payload (not shown in the chat bubble). */
    const val STATE_LINE_PREFIX = "STATE >>"
    /** Saved mentor context for the next turn’s `M:` line (not shown in UI). */
    const val SAVE_CONTEXT_MARKER = "[SAVE_CONTEXT]"
    /** Optional learner-curiosity block; omitted by the model when unsure (lenient capture). */
    const val USER_CURIOSITY_MARKER = "[USER_CURIOSITY]"

    /**
     * Memory line after [SAVE_CONTEXT] is structurally complete (used to end decode early).
     * Matches the current `Topic:|Sub:|Var:|Last:|Next:` contract; still accepts legacy `Subtopic:|Var:|Status:`.
     */
    private val closedSaveContextLine = Regex(
        """(?m)^(?:Topic:[^\n|]+\|Sub:[^\n|]+\|Var:[^\n|]+\|Last:[^\n|]+\|Next:[^\n]+|Subtopic:[^\n|]+\|Var:[^\n|]+\|Status:[^\n]+)$""",
    )

    fun isOutputFormat(raw: String): Boolean = normalize(raw).contains(OUTPUT_MARKER)

    fun normalize(raw: String): String =
        AssistantTextPolisher.stripOuterMarkdownCodeFence(raw.trim()).trim()

    /**
     * Decode often omits the repeated `---OUTPUT---` line after prefill; treat **Definition:** opens
     * or long prose as output-shaped.
     */
    fun coerceMissingOutputMarkerIfNeeded(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty() || s.contains(OUTPUT_MARKER)) return s
        if (Regex("""(?is)\A\s*\*\*Definition:\*\*""").containsMatchIn(s)) {
            return "$OUTPUT_MARKER\n\n$s"
        }
        if (s.length >= 25 &&
            !s.startsWith("---") &&
            !s.startsWith("Error", ignoreCase = true)
        ) {
            return "$OUTPUT_MARKER\n\n$s"
        }
        return s
    }

    /**
     * Lesson body: after first `---OUTPUT---`, through the triple (**Definition** / **Analogy** / **Application**).
     * Ends before the earliest of [STATE_LINE_PREFIX], [SPARKS_MARKER], [SAVE_CONTEXT_MARKER], [USER_CURIOSITY_MARKER].
     */
    fun parseLessonBlock(raw: String): String? {
        val n = normalize(raw)
        if (!n.contains(OUTPUT_MARKER)) return null
        val lesson = lessonTextAfterOutput(n)
        return lesson.takeIf { it.isNotBlank() }
    }

    /**
     * Text after [OUTPUT_MARKER]: trimmed at the earliest envelope boundary, else full remainder (streaming).
     */
    private fun lessonTextAfterOutput(normalized: String): String {
        val afterOutput = normalized.substringAfter(OUTPUT_MARKER)
        val cut = firstEnvelopeBoundaryIndex(afterOutput) ?: return afterOutput.trim()
        return afterOutput.substring(0, cut).trim()
    }

    /** Smallest non-negative index among known tail markers in [s], or null if none. */
    private fun firstEnvelopeBoundaryIndex(s: String): Int? {
        val candidates = listOf(
            s.indexOf(STATE_LINE_PREFIX),
            s.indexOf(SPARKS_MARKER),
            s.indexOf(SAVE_CONTEXT_MARKER),
            s.indexOf(USER_CURIOSITY_MARKER),
        ).filter { it >= 0 }
        return candidates.minOrNull()
    }

    /** After [SPARKS_MARKER], first boundary for the sparks CSV line. */
    private fun firstBoundaryAfterSparks(tail: String): Int {
        val iSave = tail.indexOf(SAVE_CONTEXT_MARKER)
        val iCur = tail.indexOf(USER_CURIOSITY_MARKER)
        return listOf(iSave, iCur).filter { it >= 0 }.minOrNull() ?: -1
    }

    /**
     * Raw sparks segment after [SPARKS_MARKER], up to [SAVE_CONTEXT_MARKER] or [USER_CURIOSITY_MARKER] (trimmed).
     * Chips split with [SPARK_CHIPS_DELIMITER].
     */
    fun parseSparksSection(raw: String): String? {
        val n = normalize(raw)
        val sparksIdx = n.indexOf(SPARKS_MARKER)
        if (sparksIdx < 0) return null
        val tail = n.substring(sparksIdx + SPARKS_MARKER.length)
        val endIdx = firstBoundaryAfterSparks(tail)
        val sparks = if (endIdx >= 0) {
            tail.substring(0, endIdx).trim()
        } else {
            tail.trim()
        }
        return sparks.takeIf { it.isNotBlank() }
    }

    /** Text on the `STATE >>` line after the prefix (mentor-only; not shown in UI). */
    fun parseInternalInner(raw: String): String? {
        val n = normalize(raw)
        val idx = n.indexOf(STATE_LINE_PREFIX)
        if (idx < 0) return null
        var start = idx + STATE_LINE_PREFIX.length
        while (start < n.length && (n[start] == ' ' || n[start] == '\t')) start++
        val rest = n.substring(start)
        val lineEnd = rest.indexOf('\n')
        val line = if (lineEnd >= 0) rest.substring(0, lineEnd) else rest
        return line.trim().takeIf { it.isNotBlank() }
    }

    /** Body after [SAVE_CONTEXT_MARKER] until [USER_CURIOSITY_MARKER] or end — used as `M:` on the next turn. */
    fun parseHintInner(raw: String): String? {
        val n = normalize(raw)
        val openIdx = n.lastIndexOf(SAVE_CONTEXT_MARKER)
        if (openIdx < 0) return null
        var body = n.substring(openIdx + SAVE_CONTEXT_MARKER.length).trimStart()
        val curIdx = body.indexOf(USER_CURIOSITY_MARKER)
        if (curIdx >= 0) body = body.substring(0, curIdx)
        return body.trim().takeIf { it.isNotBlank() }
    }

    /** Text after [USER_CURIOSITY_MARKER] when the model chose to emit it. */
    fun parseInterestInner(raw: String): String? {
        val n = normalize(raw)
        val idx = n.indexOf(USER_CURIOSITY_MARKER)
        if (idx < 0) return null
        return n.substring(idx + USER_CURIOSITY_MARKER.length).trim().takeIf { it.isNotBlank() }
    }

    fun parseSparkChips(sparksLine: String?): List<String> {
        val s = sparksLine?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return s.split(SPARK_CHIPS_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(2)
    }

    /**
     * For structure detection when `---OUTPUT---` is missing: keep only the part before the earliest
     * marker among [STATE_LINE_PREFIX], [SPARKS_MARKER], [SAVE_CONTEXT_MARKER], [USER_CURIOSITY_MARKER].
     */
    fun textBeforeSparksOrHint(raw: String): String {
        val s = raw.trim()
        val cut = firstEnvelopeBoundaryIndex(s) ?: -1
        return if (cut >= 0) s.substring(0, cut).trim() else s
    }

    /** Live stream: lesson slice only; same boundaries as [parseLessonBlock]. */
    fun streamingLessonPreview(raw: String): String? {
        val n = normalize(raw)
        if (!isOutputFormat(n)) return null
        return lessonTextAfterOutput(n).takeIf { it.isNotBlank() }
    }

    fun mergeForDisplayPolished(raw: String): String? {
        val lesson = parseLessonBlock(raw) ?: return null
        val san = AssistantLlmSanitizer.sanitize(lesson)
        val joined = AssistantLessonStructureParser.fromSanitizedLesson(san).joinedForPlainDisplay()
        return joined.takeIf { it.isNotBlank() }
    }

    /**
     * Output-shaped reply has a completed save-context line after `[SAVE_CONTEXT]` (new
     * `Topic:…|Sub:…|Var:…|Last:…|Next:…`, or legacy `Subtopic:…|Var:…|Status:…`) — decode may stop early on this.
     */
    fun hasClosedMemoryTag(raw: String): Boolean {
        val n = normalize(raw)
        if (!n.contains(SAVE_CONTEXT_MARKER)) return false
        val after = n.substringAfter(SAVE_CONTEXT_MARKER).trimStart()
        return closedSaveContextLine.containsMatchIn(after)
    }
}
