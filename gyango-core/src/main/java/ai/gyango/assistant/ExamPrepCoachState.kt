package ai.gyango.assistant

/**
 * Exam-prep “proctor” state: single-line [CONTEXT] for `M:` on the next turn, plus UI helpers.
 *
 * Model contract (see `gemma4_e2b_exam_prep.txt`): after `---`, a line like
 * `CONTEXT: [Question: 2] [Level: 3] [Topic: MATH] [Status: Correct]`
 * is parsed by [GyangoOutputEnvelope]; this object adds bracket-field extraction and display splits.
 */
object ExamPrepCoachState {

    /** Injected as `M:` on the first exam turn when there is no prior assistant CONTEXT. */
    const val BOOTSTRAP_MEMORY: String = "[Question: 0] [Level: 1] [Status: Start]"

    private val questionBracket = Regex("""\[Question:\s*(\d+)\]""", RegexOption.IGNORE_CASE)
    private val levelBracket = Regex("""\[Level:\s*(\d+(?:\.\d+)?)\]""", RegexOption.IGNORE_CASE)
    private val statusBracket = Regex("""\[Status:\s*([^\]]+?)\s*]""", RegexOption.IGNORE_CASE)
    private val questionTagInline = Regex("""\[\s*Question:\s*(?:\d+|k)\s*]""", RegexOption.IGNORE_CASE)

    fun parseQuestionNumber(contextOrMemoryLine: String?): Int? {
        val s = contextOrMemoryLine?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return questionBracket.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun parseLevel(contextOrMemoryLine: String?): Double? {
        val s = contextOrMemoryLine?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return levelBracket.find(s)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    fun parseStatus(contextOrMemoryLine: String?): String? {
        val s = contextOrMemoryLine?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return statusBracket.find(s)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * First user line for the hidden bootstrap (readable label; token `EXAM_PREP` is avoided in UI).
     */
    fun bootstrapUserText(): String = "Let's start the Exam Prep exam."

    private val nextQuestionHeading = Regex("""(?mi)^\s*###\s+Next\s+Question\s*$""")

    /**
     * Splits assistant markdown for UI: review (evaluation + solution) vs active next question.
     * @return pair of (review markdown, next question markdown or null if heading absent)
     */
    fun splitDisplayAtNextQuestion(displayMarkdown: String): Pair<String, String?> {
        val trimmed = displayMarkdown.trimEnd()
        if (trimmed.isEmpty()) return "" to null
        val m = nextQuestionHeading.find(trimmed) ?: return trimmed to null
        val review = normalizeExamSectionSpacing(trimmed.substring(0, m.range.first).trimEnd())
        val next = normalizeExamSectionSpacing(trimmed.substring(m.range.last + 1).trim())
        return review to next.takeIf { it.isNotBlank() }
    }

    /** Pulls the latest visible "next question" block from an assistant display markdown body. */
    fun extractLatestNextQuestion(displayMarkdown: String): String? {
        val (_, next) = splitDisplayAtNextQuestion(displayMarkdown)
        if (next.isNullOrBlank()) return null
        val cleaned = questionTagInline.replace(next, "").trim()
        return normalizeExamSectionSpacing(cleaned).takeIf { it.isNotBlank() }
    }

    /**
     * Enforces monotonic question progression (`current > previous`) by rewriting the question bracket
     * when the model repeats or decrements the question number.
     */
    fun coerceQuestionProgression(
        parsedContextLine: String?,
        priorMemoryLine: String?,
    ): String? {
        val context = parsedContextLine?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val prev = parseQuestionNumber(priorMemoryLine) ?: return context
        val cur = parseQuestionNumber(context) ?: return context
        if (cur > prev) return context
        val expected = prev + 1
        return questionBracket.replace(context, "[Question: $expected]")
    }

    private fun normalizeExamSectionSpacing(markdown: String): String {
        var s = markdown.trim()
        s = Regex("""(?m)^(###\s+[^\n]+)\n{2,}""").replace(s, "$1\n")
        s = Regex("""\n{3,}""").replace(s, "\n\n")
        return s
    }
}
