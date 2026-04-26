package ai.gyango.api

import ai.gyango.assistant.GyangoOutputEnvelope
import ai.gyango.core.SafetyProfile
import ai.gyango.core.SubjectMode
import ai.gyango.core.TutorUserPreference
import java.time.LocalDate
import java.util.Locale

/**
 * Short on-device prompt: `<|turn>system` policy + output tail, `<|turn>user` M/U, `<|turn>assistant`.
 * Next turn uses prior `CONTEXT >>` as `M:` plus exam question replay in exam mode.
 */
object PromptBuilder {
    const val DEFAULT_PROMPT_MODEL_FAMILY: String = "gemma4_e2b"
    private const val PROMPT_ASSET_DIR = "prompts"
    enum class PromptTemplateVersion(val token: String) {
        V1("v1"),
        V2("v2"),
    }

    val ACTIVE_PROMPT_TEMPLATE_VERSION: PromptTemplateVersion = PromptTemplateVersion.V2

    private fun estimatedAgeFromDob(month: Int?, year: Int?): Int? {
        val y = year ?: return null
        val now = LocalDate.now()
        if (y < 1900 || y > now.year) return null
        var age = now.year - y
        if (month != null) {
            if (month !in 1..12) return null
            if (now.monthValue < month) age -= 1
        }
        return age.coerceAtLeast(0)
    }

    private fun ageSafetyInstruction(month: Int?, year: Int?): String {
        val age = estimatedAgeFromDob(month, year)
        return when {
            age == null ->
                "Age unknown: keep content cautious and age-appropriate."
            age < 13 ->
                "Child (~$age): child-safe language; sensitive topics → trusted adult."
            age < 18 ->
                "Teen (~$age): age-appropriate; no dangerous how-to harm."
            else ->
                "Adult (~$age): normal safety."
        }
    }

    private fun strictKidsSafetyInstruction(safetyProfile: SafetyProfile): String =
        if (safetyProfile != SafetyProfile.KIDS_STRICT) {
            ""
        } else {
            "Kids-safe: no explicit/self-harm/drugs/weapons/cheating help; risky topics → short safe reply + adult."
        }

    fun topicLabelForPrompt(mode: SubjectMode?): String = compactTopicLabel(mode)

    private fun compactTopicLabel(mode: SubjectMode?): String = when (mode ?: SubjectMode.GENERAL) {
        SubjectMode.GENERAL -> "GENERAL"
        SubjectMode.MATH -> "MATH"
        SubjectMode.SCIENCE -> "SCIENCE"
        SubjectMode.CODING -> "CODING"
        SubjectMode.WRITING -> "WRITING"
        SubjectMode.EXAM_PREP -> "EXAM_PREP"
    }

    private fun languageNameForLocaleTag(tag: String): String = when {
        tag.startsWith("te", ignoreCase = true) -> "Telugu"
        tag.startsWith("hi", ignoreCase = true) -> "Hindi"
        tag.startsWith("mr", ignoreCase = true) -> "Marathi"
        tag.startsWith("ta", ignoreCase = true) -> "Tamil"
        tag.startsWith("bn", ignoreCase = true) -> "Bengali"
        tag.startsWith("gu", ignoreCase = true) -> "Gujarati"
        tag.startsWith("kn", ignoreCase = true) -> "Kannada"
        tag.startsWith("ml", ignoreCase = true) -> "Malayalam"
        tag.startsWith("pa", ignoreCase = true) -> "Punjabi"
        tag.startsWith("fr", ignoreCase = true) -> "French"
        tag.startsWith("es", ignoreCase = true) -> "Spanish"
        tag.startsWith("en", ignoreCase = true) -> "English"
        else -> "English"
    }

    fun buildChatPrompt(
        lastUserContent: String,
        memoryHint: String? = null,
        imageOcrContext: String? = null,
        preferredReplyLocaleTag: String = "en-US",
        birthMonth: Int? = null,
        birthYear: Int? = null,
        subjectMode: SubjectMode? = null,
        safetyProfile: SafetyProfile = SafetyProfile.KIDS_STRICT,
        userPreferenceModeLine: String = TutorUserPreference.GENERIC_MODE_LINE,
        difficultyLevel: Int = 1,
        requestThoughtHints: Boolean = false,
        promptModelFamily: String = DEFAULT_PROMPT_MODEL_FAMILY,
        promptTemplateVersion: PromptTemplateVersion = ACTIVE_PROMPT_TEMPLATE_VERSION,
        loadPromptTemplate: ((String) -> String?)? = null,
        /** When [subjectMode] is [SubjectMode.EXAM_PREP], overrides `{{TOPIC}}` (e.g. GENERAL, MATH, SCIENCE). */
        examPrepTopicLane: String? = null,
        /** Learner-chosen exam focus; injected into exam-prep template only. */
        examPrepSubtopic: String? = null,
        /** Target number of scored questions for this exam session. */
        examPrepQuestionTarget: Int? = null,
        /** Last asked exam question text (for grading U against the exact prompt). */
        examPrepPriorQuestion: String? = null,
    ): String {
        val lastUserText = lastUserContent.trim()
        val ageYears = estimatedAgeFromDob(birthMonth, birthYear)
        val age = ageYears?.toString() ?: "unknown"
        val memoryRaw = memoryHint?.trim()?.takeIf { it.isNotBlank() } ?: "None"
        val imageOcrRaw = imageOcrContext?.trim()?.takeIf { it.isNotBlank() }
        val language = languageNameForLocaleTag(preferredReplyLocaleTag)
        val lane = subjectMode ?: SubjectMode.GENERAL
        val topic = when {
            lane == SubjectMode.EXAM_PREP &&
                !examPrepTopicLane.isNullOrBlank() ->
                examPrepTopicLane.trim().uppercase(Locale.US)
            else -> compactTopicLabel(subjectMode)
        }
        val localeTag = preferredReplyLocaleTag.trim()
        val safetyLine = if (safetyProfile == SafetyProfile.KIDS_STRICT) {
            "Safety: kids-strict"
        } else {
            "Safety: standard"
        }
        val diffCoerced = difficultyLevel.coerceIn(1, 10)
        val templateSelection = selectTemplate(
            mode = lane,
            promptModelFamily = promptModelFamily,
            promptTemplateVersion = promptTemplateVersion,
            loadPromptTemplate = loadPromptTemplate,
        )
        val template = templateSelection.template
        val thoughtHintLine = if (requestThoughtHints) {
            "Keep any private reasoning minimal; do not dump chain-of-thought."
        } else {
            ""
        }
        val imageBlock = if (imageOcrRaw == null) {
            ""
        } else {
            buildString {
                appendLine("IMAGE_OCR:")
                appendLine(imageOcrRaw)
                appendLine(
                    "INSTRUCTIONS: Use IMAGE_OCR only as supporting context for U. " +
                        "If OCR is unclear or incomplete, say what is uncertain and ask a targeted follow-up.",
                )
            }.trimEnd()
        }
        val examSubtopicLine = examPrepSubtopic
            ?.trim()
            ?.replace(Regex("""\s+"""), " ")
            ?.take(400)
            ?.takeIf { it.isNotBlank() }
            ?: "—"
        val examTargetLine = examPrepQuestionTarget
            ?.takeIf { it in 1..999 }
            ?.toString()
            ?: "10"
        val examPriorQuestionLine = examPrepPriorQuestion
            ?.trim()
            ?.replace(Regex("""\s+"""), " ")
            ?.take(800)
            ?.takeIf { it.isNotBlank() }
            ?: "None"
        val rendered = template
            .replace("{{TOPIC}}", topic)
            .replace("{{AGE}}", age)
            .replace("{{LANGUAGE}}", language)
            .replace("{{LOCALE_TAG}}", localeTag)
            .replace("{{TONE_LINE}}", userPreferenceModeLine.trim())
            .replace("{{DIFFICULTY}}", diffCoerced.toString())
            .replace("{{SAFETY_LINE}}", safetyLine)
            .replace("{{AGE_SAFETY_INSTRUCTION}}", ageSafetyInstruction(birthMonth, birthYear))
            .replace("{{KIDS_EXTRA}}", strictKidsSafetyInstruction(safetyProfile))
            .replace("{{THOUGHT_HINT_LINE}}", thoughtHintLine)
            .replace("{{MEMORY}}", memoryRaw)
            .replace("{{USER_TEXT}}", lastUserText)
            .replace("{{IMAGE_OCR_BLOCK}}", imageBlock)
            .replace("{{CONTEXT_PREFIX}}", GyangoOutputEnvelope.CONTEXT_LINE_PREFIX)
            .replace("{{CURIOSITY_PREFIX}}", GyangoOutputEnvelope.CURIOSITY_LINE_PREFIX)
            .replace("{{SPARKS_PREFIX}}", GyangoOutputEnvelope.SPARKS_LINE_PREFIX)
            .replace("{{SPARK_DELIMITER}}", GyangoOutputEnvelope.SPARK_CHIPS_DELIMITER)
            .replace("{{EXAM_SUBTOPIC}}", examSubtopicLine)
            .replace("{{EXAM_QUESTION_TARGET}}", examTargetLine)
            .replace("{{EXAM_PRIOR_QUESTION}}", examPriorQuestionLine)
        return rendered
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trimEnd { it == ' ' || it == '\t' || it == '\n' }
    }

    fun promptTemplateAssetPath(mode: SubjectMode?, promptModelFamily: String = DEFAULT_PROMPT_MODEL_FAMILY): String {
        return promptTemplateAssetPath(
            mode = mode,
            promptModelFamily = promptModelFamily,
            promptTemplateVersion = ACTIVE_PROMPT_TEMPLATE_VERSION,
        )
    }

    fun promptTemplateAssetPath(
        mode: SubjectMode?,
        promptModelFamily: String = DEFAULT_PROMPT_MODEL_FAMILY,
        promptTemplateVersion: PromptTemplateVersion,
    ): String {
        val modelPrefix = promptModelFamily.trim().ifBlank { DEFAULT_PROMPT_MODEL_FAMILY }.lowercase()
        val topicToken = compactTopicLabel(mode).lowercase()
        val versionDir = promptTemplateVersion.token
        return "$PROMPT_ASSET_DIR/$versionDir/${modelPrefix}_${topicToken}.txt"
    }

    fun promptTemplateAssetPathCandidates(
        mode: SubjectMode?,
        promptModelFamily: String = DEFAULT_PROMPT_MODEL_FAMILY,
        promptTemplateVersion: PromptTemplateVersion = ACTIVE_PROMPT_TEMPLATE_VERSION,
    ): List<String> {
        val activePath = promptTemplateAssetPath(
            mode = mode,
            promptModelFamily = promptModelFamily,
            promptTemplateVersion = promptTemplateVersion,
        )
        val v1Path = promptTemplateAssetPath(
            mode = mode,
            promptModelFamily = promptModelFamily,
            promptTemplateVersion = PromptTemplateVersion.V1,
        )
        val legacyPath = legacyPromptTemplateAssetPath(mode = mode, promptModelFamily = promptModelFamily)
        return listOf(activePath, v1Path, legacyPath).distinct()
    }

    private fun legacyPromptTemplateAssetPath(
        mode: SubjectMode?,
        promptModelFamily: String = DEFAULT_PROMPT_MODEL_FAMILY,
    ): String {
        val modelPrefix = promptModelFamily.trim().ifBlank { DEFAULT_PROMPT_MODEL_FAMILY }.lowercase()
        val topicToken = compactTopicLabel(mode).lowercase()
        return "$PROMPT_ASSET_DIR/${modelPrefix}_${topicToken}.txt"
    }

    private data class TemplateSelection(val template: String)

    private fun selectTemplate(
        mode: SubjectMode?,
        promptModelFamily: String,
        promptTemplateVersion: PromptTemplateVersion,
        loadPromptTemplate: ((String) -> String?)?,
    ): TemplateSelection {
        val candidateAssets = promptTemplateAssetPathCandidates(
            mode = mode,
            promptModelFamily = promptModelFamily,
            promptTemplateVersion = promptTemplateVersion,
        )
        if (loadPromptTemplate != null) {
            candidateAssets.forEach { assetPath ->
                val loaded = loadPromptTemplate(assetPath)?.takeIf { it.isNotBlank() }
                if (loaded != null) {
                    return TemplateSelection(template = loaded)
                }
            }
        }
        return TemplateSelection(template = fallbackTemplateFor(mode))
    }

    private fun fallbackTemplateFor(mode: SubjectMode?): String = when (mode ?: SubjectMode.GENERAL) {
        SubjectMode.MATH -> MATH_TEMPLATE
        SubjectMode.SCIENCE -> SCIENCE_TEMPLATE
        SubjectMode.CODING -> CODING_TEMPLATE
        SubjectMode.WRITING -> WRITING_TEMPLATE
        SubjectMode.EXAM_PREP -> EXAM_PREP_TEMPLATE
        SubjectMode.GENERAL -> GENERAL_TEMPLATE
    }

    private const val COMMON_TEMPLATE_PREFIX = """
<|turn>system
"""

    private const val COMMON_TEMPLATE_SUFFIX = """
Topic: {{TOPIC}} | Age: {{AGE}} | Lang: {{LANGUAGE}} ({{LOCALE_TAG}}) | Tone: {{TONE_LINE}} | Difficulty: {{DIFFICULTY}}
{{SAFETY_LINE}}
{{AGE_SAFETY_INSTRUCTION}}
{{KIDS_EXTRA}}
Reply in {{LANGUAGE}} unless the user asks otherwise.
{{THOUGHT_HINT_LINE}}
Turn focus: the next user block is U (current question)—answer U directly; match depth to the question (greetings or small talk: one to three short sentences unless U asks for more). M is optional prior-topic background only; use it only to clarify U, never as a substitute for answering U.

Use markdown when it helps (headings, bullets; fenced code only for code). For trivial chat, plain sentences are fine—do not pad with extra sections.
After the answer, output exactly:
---
{{CONTEXT_PREFIX}} <one line: reusable topic summary for next turn>
{{CURIOSITY_PREFIX}} <one line: interest hook to save>
{{SPARKS_PREFIX}} <complete follow-up question 1>{{SPARK_DELIMITER}}<complete follow-up question 2>{{SPARK_DELIMITER}}<complete follow-up question 3>
Replace each placeholder with one real follow-up question only—no difficulty words, no angle-bracket tags in the final line.
No other metadata lines. CONTEXT/CURIOSITY/SPARKS must be single lines each.
<turn|>

<|turn>user
M: {{MEMORY}}
U: {{USER_TEXT}}
{{IMAGE_OCR_BLOCK}}
<turn|>

<|turn>assistant
"""

    private const val COMMON_TEMPLATE_MATH_SUFFIX = """
Topic: {{TOPIC}} | Age: {{AGE}} | Lang: {{LANGUAGE}} ({{LOCALE_TAG}}) | Tone: {{TONE_LINE}} | Difficulty: {{DIFFICULTY}}
{{SAFETY_LINE}}
{{AGE_SAFETY_INSTRUCTION}}
{{KIDS_EXTRA}}
Reply in {{LANGUAGE}} unless the user asks otherwise.
{{THOUGHT_HINT_LINE}}
Turn focus: the next user block is U (current question)—answer U directly; match depth to the question (greetings or small talk: one to three short sentences unless U asks for more). M is optional prior-topic background only; use it only to clarify U, never as a substitute for answering U.

Use markdown when it helps (headings, bullets; fenced code only for programming code—never fence equations). For math, put TeX inside $$...$$ (same delimiter at both ends); one main relation per span, or use \\ between lines inside that span. For trivial chat, plain sentences are fine—do not pad with extra sections.
After the answer, output exactly:
---
{{CONTEXT_PREFIX}} <one line: reusable topic summary for next turn>
{{CURIOSITY_PREFIX}} <one line: interest hook to save>
{{SPARKS_PREFIX}} <complete follow-up question 1>{{SPARK_DELIMITER}}<complete follow-up question 2>{{SPARK_DELIMITER}}<complete follow-up question 3>
Replace each placeholder with one real follow-up question only—no difficulty words, no angle-bracket tags in the final line.
No other metadata lines. CONTEXT/CURIOSITY/SPARKS must be single lines each.
<turn|>

<|turn>user
M: {{MEMORY}}
U: {{USER_TEXT}}
{{IMAGE_OCR_BLOCK}}
<turn|>

<|turn>assistant
"""

    private const val GENERAL_TEMPLATE = COMMON_TEMPLATE_PREFIX + """
You are a helpful assistant. Clear, concise, age-appropriate.
""" + COMMON_TEMPLATE_SUFFIX
    private const val SCIENCE_TEMPLATE = COMMON_TEMPLATE_PREFIX + """
You are a clear science tutor. Intuition first, then precision.
""" + COMMON_TEMPLATE_SUFFIX
    private const val CODING_TEMPLATE = COMMON_TEMPLATE_PREFIX + """
You are a coding tutor. Working code first; brief explanation.
""" + COMMON_TEMPLATE_SUFFIX
    private const val WRITING_TEMPLATE = COMMON_TEMPLATE_PREFIX + """
You are a writing coach. Practical edits; match the user's tone.
""" + COMMON_TEMPLATE_SUFFIX
    private const val EXAM_PREP_TEMPLATE = COMMON_TEMPLATE_PREFIX + """
Role: GyanGo Coach. Supportive, adaptive, and direct.
Config: Topic: {{TOPIC}} | Age: {{AGE}} | Lang: {{LANGUAGE}} ({{LOCALE_TAG}}) | Tone: {{TONE_LINE}} | Start_Diff: {{DIFFICULTY}}
{{SAFETY_LINE}}
{{AGE_SAFETY_INSTRUCTION}}
{{KIDS_EXTRA}}
Reply in {{LANGUAGE}} unless the user asks otherwise.
{{THOUGHT_HINT_LINE}}
MISSION: Deliver a mock exam. M holds Question #, Level, and Status. Q is the exact previous question text; U is the learner's latest answer.
LEARNER_FOCUS: The learner chose this test focus: {{EXAM_SUBTOPIC}}. Every question must align with that focus within topic lane {{TOPIC}}.
ADAPTIVE LOGIC:
- Correct: Level +0.5. Introduce a small, logical step up in complexity.
- Incorrect: Level stays same. Pivot to a fresh question (never repeat the same question).
- Tone: Empathetic mentor. Use supportive validation even when the answer is wrong.
- Termination: After Q {{EXAM_QUESTION_TARGET}}, provide "### Final Review" and stop. Do not output "### Next Question".

FORMAT:
- Use Markdown with "### Feedback" and "### Next Question" headers.
- If the topic lane is MATH or SCIENCE, use $$...$$ for equations. If CODING, use fenced code blocks for code.
- No preambles.

METADATA FORMAT: Output exactly:
---
CONTEXT: [Question: <num>] [Level: <current_diff>] [Status: <Correct/Incorrect/Complete>]
CURIOSITY: <Specific logic gap or pattern identified>
SPARKS: <Ext>{{SPARK_DELIMITER}}<Deep>{{SPARK_DELIMITER}}<Tip>
No other metadata lines. CONTEXT/CURIOSITY/SPARKS must be single lines each.
<turn|>

<|turn>user
M: {{MEMORY}}
Q: {{EXAM_PRIOR_QUESTION}}
U: {{USER_TEXT}}
{{IMAGE_OCR_BLOCK}}
<turn|>

<|turn>assistant
"""
    private const val MATH_TEMPLATE = COMMON_TEMPLATE_PREFIX + """
You are a clear math tutor. Keep steps short and fix common mistakes.
""" + COMMON_TEMPLATE_MATH_SUFFIX
}
