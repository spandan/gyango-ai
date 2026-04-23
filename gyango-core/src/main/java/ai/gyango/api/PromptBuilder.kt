package ai.gyango.api

import ai.gyango.assistant.GyangoOutputEnvelope
import ai.gyango.core.SafetyProfile
import ai.gyango.core.SubjectMode
import ai.gyango.core.TutorUserPreference
import java.time.LocalDate

/**
 * Short on-device prompt: `<|turn>system` policy + output tail, `<|turn>user` M/U, `<|turn>assistant`.
 * Next turn uses prior `CONTEXT >>` as `M:` only (no full history).
 */
object PromptBuilder {

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
        SubjectMode.CURIOSITY -> "CURIOSITY"
        SubjectMode.GENERAL -> "GENERAL"
        SubjectMode.MATH -> "MATH"
        SubjectMode.SCIENCE -> "SCIENCE"
        SubjectMode.PHYSICS -> "PHYSICS"
        SubjectMode.CHEMISTRY -> "CHEMISTRY"
        SubjectMode.BIOLOGY -> "BIOLOGY"
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
    ): String {
        val lastUserText = lastUserContent.trim()
        val ageYears = estimatedAgeFromDob(birthMonth, birthYear)
        val age = ageYears?.toString() ?: "unknown"
        val memoryRaw = memoryHint?.trim()?.takeIf { it.isNotBlank() } ?: "None"
        val imageOcrRaw = imageOcrContext?.trim()?.takeIf { it.isNotBlank() }
        val language = languageNameForLocaleTag(preferredReplyLocaleTag)
        val topic = compactTopicLabel(subjectMode)
        val lane = subjectMode ?: SubjectMode.GENERAL
        val localeTag = preferredReplyLocaleTag.trim()
        val safetyLine = if (safetyProfile == SafetyProfile.KIDS_STRICT) {
            "Safety: kids-strict"
        } else {
            "Safety: standard"
        }
        val diffCoerced = difficultyLevel.coerceIn(1, 10)

        return buildString {
            appendLine("<|turn>system")
            TopicPromptFormats.appendRoleBrief(this, lane)
            appendLine("Topic: $topic | Age: $age | Lang: $language ($localeTag) | Tone: ${userPreferenceModeLine.trim()} | Difficulty: $diffCoerced")
            appendLine(safetyLine)
            appendLine(ageSafetyInstruction(birthMonth, birthYear))
            val kidsExtra = strictKidsSafetyInstruction(safetyProfile)
            if (kidsExtra.isNotBlank()) appendLine(kidsExtra)
            appendLine("Reply in $language unless the user asks otherwise.")
            if (requestThoughtHints) {
                appendLine("Keep any private reasoning minimal; do not dump chain-of-thought.")
            }
            appendLine(
                "Turn focus: the next user block is U (current question)—answer U directly; match depth to the question " +
                    "(greetings or small talk: one to three short sentences unless U asks for more). " +
                    "M is optional prior-topic background only; use it only to clarify U, never as a substitute for answering U.",
            )
            appendLine()
            appendLine(
                "Use markdown when it helps (headings, bullets; fenced code only for code). " +
                    "For trivial chat, plain sentences are fine—do not pad with extra sections.",
            )
            appendLine("After the answer, output exactly:")
            appendLine("---")
            appendLine("${GyangoOutputEnvelope.CONTEXT_LINE_PREFIX} <one line: reusable topic summary for next turn>")
            appendLine("${GyangoOutputEnvelope.CURIOSITY_LINE_PREFIX} <one line: interest hook to save>")
            appendLine(
                "${GyangoOutputEnvelope.SPARKS_LINE_PREFIX} <complete follow-up question 1>${GyangoOutputEnvelope.SPARK_CHIPS_DELIMITER}" +
                    "<complete follow-up question 2>${GyangoOutputEnvelope.SPARK_CHIPS_DELIMITER}<complete follow-up question 3>",
            )
            appendLine(
                "Replace each placeholder with one real follow-up question only—no difficulty words, no angle-bracket tags in the final line.",
            )
            appendLine("No other metadata lines. CONTEXT/CURIOSITY/SPARKS must be single lines each.")
            appendLine("<turn|>")
            appendLine()
            appendLine("<|turn>user")
            appendLine("M: $memoryRaw")
            appendLine("U: $lastUserText")
            if (imageOcrRaw != null) {
                appendLine("IMAGE_OCR:")
                appendLine(imageOcrRaw)
                appendLine(
                    "INSTRUCTIONS: Use IMAGE_OCR only as supporting context for U. " +
                        "If OCR is unclear or incomplete, say what is uncertain and ask a targeted follow-up.",
                )
            }
            appendLine("<turn|>")
            appendLine()
            appendLine("<|turn>assistant")
        }.trimEnd { it == ' ' || it == '\t' }
    }
}
