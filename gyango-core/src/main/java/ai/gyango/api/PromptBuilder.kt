package ai.gyango.api

import ai.gyango.api.prompts.ChatLessonPromptSpecs
import ai.gyango.assistant.GyangoOutputEnvelope
import ai.gyango.assistant.AssistantLlmSanitizer
import ai.gyango.core.LearnerProfile
import ai.gyango.core.LearningProfile
import ai.gyango.core.SafetyProfile
import ai.gyango.core.SkillBand
import ai.gyango.core.SubjectMode
import java.time.LocalDate

/**
 * Prompts for on-device LiteRT models: K-12 chat is **stateless** (only the latest user turn is sent), with
 * Socratic mentor framing, `# TOPIC:` / `# Subject:` (tile pedagogy), strict/standard safety line, and required `---OUTPUT---` / `STATE >>` /
 * (`Var` / `Goal` / `Skill`) / `---SPARKS---` / `[SAVE_CONTEXT]` (`Topic|Sub|Var|Last|Next`) /
 * `[USER_CURIOSITY]` layout.
 *
 * Chat targets **Gemma 4 E2B IT** (see LiteRT bundle `gemma-4-E2B-it.litertlm`). [buildChatPrompt] is **single-turn only**:
 * [lastUserContent] is the only user string in the prompt (no transcript / prior turns).
 * Tool and share flows use [formatGyangoSingleTurn] (plain text).
 */
object PromptBuilder {

    /** Avoid `..` when appending `. Bridge [U] and [M].` after a guide that already ends with a period. */
    private fun definitionSectionGuideLine(lessonSpec: ChatLessonPromptSpecs.LessonBodySpec): String {
        val g = lessonSpec.definitionGuide.trimEnd(' ', '\t', '.')
        return "- Definition: $g. Bridge [U] and [M]."
    }

    /** Still used by [formatGyangoSingleTurn] (tool / plain path); K-12 chat uses a minimal header without it. */
    private val gyangoAssistantFingerprint = AssistantLlmSanitizer.gyangoPrimingFingerprint

    /** Plain-text priming (after fingerprint); ends before [AssistantLlmSanitizer.gyangoPrimingUserChatPlainTail]. */
    private const val gyangoPrimingPlainMid =
        "(on-device LiteRT, Gemma 4 E2B IT). " +
            "Write like a careful professional: precise wording, complete sentences, correct terminology. " +
            "Use normal English spacing around short function words (never merge a word with \"a\", \"an\", or \"the\" — write \"uses a\", not \"usesa\"). " +
            "Use plain text for normal answers: no markdown headings and no decorative markup. " +
            "Structure answers for scanning: one short intro line, then a blank line before the rest; " +
            "use lines that start with \"- \" or \"1. \" for lists; " +
            "for short section labels write them as their own line ending with a colon (Example:). " +
            "For math, algebra, chemistry or physics equations, or program snippets, put only that formal material in fenced code blocks (triple backticks; you may add a language tag after the opening backticks for code), and keep intro/outro in plain sentences outside the fence. " +
            "Keep paragraphs small. " +
            "Match depth to the question — everyday questions get a tight answer; " +
            "only go long if they explicitly ask for detail, steps, or code. " +
            "Do not pad with filler or repeat yourself. " +
            "Do not ask legal/professional intake questions (for example jurisdiction, where they practice, or bar details). " +
            "If profile setup is needed, ask for first name, last name, preferred language, and optionally birth month/year. " +
            "End on a natural boundary (full sentence, finished bullet, or closed paragraph)—do not stop mid-clause. " +
            "If you are near your length limit, add one short closing line instead of trailing off. "

    private const val gyangoPrimingJsonHint =
        " If they ask for JSON, output only a single valid JSON object, no markdown fences or explanation."

    private const val gyangoPrimingAssistant = "Understood."

    private data class TutorPromptContext(
        val role: String,
        val age: Int?,
        val iqLevel: Int?,
        val preferredReplyLocaleTag: String,
        val safetyProfile: SafetyProfile,
        val subjectMode: SubjectMode? = null,
    )

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

    /** Short label for the K-12 system header (used with birth year/month when known). */
    private fun studentAudienceLabel(age: Int?): String = when {
        age == null -> "Secondary-level"
        age <= 8 -> "Primary"
        age <= 11 -> "Upper-primary"
        age <= 14 -> "Middle-secondary"
        age <= 16 -> "Secondary"
        age <= 18 -> "Senior-secondary"
        else -> "Adult"
    }

    private fun gradeBandInstruction(month: Int?, year: Int?): String {
        val age = estimatedAgeFromDob(month, year)
        return when {
            age == null ->
                "Use a professional, encouraging tone suited to Indian secondary students (about Grade 8–10) " +
                    "unless the question clearly targets a different level; when unsure, stay cautious and clear."
            age <= 8 ->
                "Use vocabulary, pace, and encouragement suited to primary grades (roughly Grades 1–3 by age)."
            age <= 11 ->
                "Use tone suited to upper primary (roughly Grades 4–6)."
            age <= 14 ->
                "Use tone suited to middle secondary (roughly Grades 7–9)."
            age <= 16 ->
                "Use tone suited to Grade 10 secondary level."
            age <= 18 ->
                "Use tone suited to senior secondary (roughly Grades 11–12)."
            else ->
                "Use clear, professional tone suited to an adult learner; still map ideas to NCERT-style framing when helpful."
        }
    }

    private fun ageSafetyInstruction(month: Int?, year: Int?): String {
        val age = estimatedAgeFromDob(month, year)
        return when {
            age == null ->
                "Age is unknown. Keep responses age-appropriate and cautious by default, and avoid unsafe or explicit guidance."
            age < 13 ->
                "The user appears to be a child (about $age years old). Use child-safe language, avoid mature or risky details, and direct harmful/safety-sensitive topics to a trusted adult."
            age < 18 ->
                "The user appears to be a minor teenager (about $age years old). Keep responses age-appropriate, avoid dangerous step-by-step harmful guidance, and include safety framing when needed."
            else ->
                "The user appears to be an adult (about $age years old). Keep normal safety practices."
        }
    }

    private fun strictKidsSafetyInstruction(safetyProfile: SafetyProfile): String =
        if (safetyProfile != SafetyProfile.KIDS_STRICT) {
            ""
        } else {
            "Kids-safe mode is ON. Never provide explicit sexual content, self-harm guidance, dangerous instructions, cheating help, weapon help, drug or alcohol guidance, or advice that encourages secrecy from trusted adults. When a topic is risky, give a short safe response and guide the child to a parent, guardian, teacher, or another trusted adult."
        }

    private fun tutorRole(subjectMode: SubjectMode?): String = when (subjectMode) {
        SubjectMode.MATH -> "Math"
        SubjectMode.SCIENCE -> "Science"
        SubjectMode.PHYSICS -> "Physics"
        SubjectMode.CHEMISTRY -> "Chemistry"
        SubjectMode.BIOLOGY -> "Biology"
        SubjectMode.CODING -> "Coding"
        SubjectMode.WRITING -> "Writing"
        SubjectMode.EXAM_PREP -> "Exam Prep"
        SubjectMode.CURIOSITY -> "Curiosity"
        SubjectMode.GENERAL, null -> "Curiosity"
    }

    /** Topic token for `# TOPIC:` / verbose logs; matches [buildChatPrompt]. */
    fun topicLabelForPrompt(mode: SubjectMode?): String = compactTopicLabel(mode)

    /** Short topic token for prompts (`# TOPIC:`) and logs (tile / subject mode). */
    private fun compactTopicLabel(mode: SubjectMode?): String = when (ChatLessonPromptSpecs.effectiveSubjectMode(mode)) {
        null, SubjectMode.GENERAL, SubjectMode.CURIOSITY -> "GENERAL"
        SubjectMode.MATH -> "MATH"
        SubjectMode.SCIENCE -> "SCIENCE"
        SubjectMode.PHYSICS -> "PHYSICS"
        SubjectMode.CHEMISTRY -> "CHEMISTRY"
        SubjectMode.BIOLOGY -> "BIOLOGY"
        SubjectMode.CODING -> "CODING"
        SubjectMode.WRITING -> "WRITING"
        SubjectMode.EXAM_PREP -> "EXAM_PREP"
    }

    private fun gradeLabelFromAge(age: Int?): String = when {
        age == null -> "Unknown"
        age <= 8 -> "Grade 1-3"
        age <= 11 -> "Grade 4-6"
        age <= 14 -> "Grade 7-9"
        age <= 16 -> "Grade 10"
        age <= 18 -> "Grade 11-12"
        else -> "Adult"
    }

    private fun learnerPreferenceInstruction(learnerProfile: LearnerProfile): String = buildList {
        if (learnerProfile.supportSignals >= learnerProfile.challengeSignals + 2) {
            add("The learner often prefers extra support: reassure them, keep the pace calm, and reduce cognitive load.")
        }
        if (learnerProfile.challengeSignals >= learnerProfile.supportSignals + 2) {
            add("The learner often enjoys challenge: after the core explanation, add one small stretch question or deeper connection.")
        }
        if (learnerProfile.curiositySignals >= 10) {
            add("The learner shows strong curiosity: include one interesting why/how link or real-world connection when helpful.")
        }
        if (isEmpty()) {
            add("Personalize mainly for taste: keep the explanation clear, warm, and easy to follow.")
        }
    }.joinToString(" ")

    private fun buildTutorSystemTemplate(
        context: TutorPromptContext,
        birthMonth: Int?,
        birthYear: Int?,
        learnerProfile: LearnerProfile,
        subjectSkillBands: Map<SubjectMode, SkillBand>,
        requestModelThoughtInJson: Boolean,
    ): String {
        val languageName = languageNameForLocaleTag(context.preferredReplyLocaleTag)
        val mentorAgeSegment = context.age?.let { "${it}yo" } ?: "age unknown"
        val topic = compactTopicLabel(context.subjectMode)
        return buildString {
            appendLine("# Mentor | $mentorAgeSegment | $languageName | $topic")
            appendLine("# Step: 1.Think(EN) 2.Output($languageName)")
            appendLine("# Format: Three clear paragraphs; full sentences; no abbreviated section headers.")
            appendLine("# Rule: No repetition.")
            if (requestModelThoughtInJson) {
                appendLine("# Note: Keep private English reasoning short; all user-visible section text must be in $languageName.")
            }
            appendLine(gradeBandInstruction(birthMonth, birthYear))
            appendLine(ageSafetyInstruction(birthMonth, birthYear))
            appendLine(strictKidsSafetyInstruction(context.safetyProfile))
            val pedagogy = learnerProfileInstruction(learnerProfile, context.subjectMode, subjectSkillBands)
            if (pedagogy.isNotBlank()) {
                appendLine("# Learner: $pedagogy")
            }
            appendLine("# Subject: ${subjectTeachingHint(context.subjectMode)}")
            appendLine("# Markers: Begin the assistant reply with this line exactly:")
            appendLine(GyangoOutputEnvelope.OUTPUT_MARKER)
            appendLine(
                "# Then write **Definition:**, **Analogy:**, and **Application:** sections (blank line between). " +
                    "Immediately after **Application:**, on its own line write `${GyangoOutputEnvelope.STATE_LINE_PREFIX} Var:[KeyVar]; Goal:[CurrentObjective]; Skill:[MasteredStep]` " +
                    "(mentor-only). Then ${GyangoOutputEnvelope.SPARKS_MARKER} and one line: two 5–8 word contextual bridges as follow-up angles, separated by " +
                    "${GyangoOutputEnvelope.SPARK_CHIPS_DELIMITER} only. " +
                    "Then ${GyangoOutputEnvelope.SAVE_CONTEXT_MARKER} on its own line and one line " +
                    "`Topic:$topic|Sub:[SubTopic]|Var:[Var]|Last:[LogicStep]|Next:[NextPrerequisite]` (use the same coarse topic token as # TOPIC:). " +
                    "Optionally ${GyangoOutputEnvelope.USER_CURIOSITY_MARKER} then ≤12 words in $languageName describing what the student showed interest in. " +
                    "Write the lesson in $languageName.",
            )
            appendLine()
        }.trimEnd()
    }

    private fun learnerProfileInstruction(
        learnerProfile: LearnerProfile,
        subjectMode: SubjectMode?,
        subjectSkillBands: Map<SubjectMode, SkillBand>,
    ): String {
        val style = when (learnerProfile.learningProfile) {
            LearningProfile.EXPLORER ->
                "Explain with gentle curiosity, short steps, and concrete examples."
            LearningProfile.THINKER ->
                "Explain with extra why/how connections, comparisons, and cause-effect reasoning."
            LearningProfile.BUILDER ->
                "Explain with slightly more challenge, simple problem-solving prompts, and one stretch idea."
        }
        val skillBand = subjectMode?.let { mode -> subjectSkillBands[mode] }
        val confidence = when (skillBand) {
            SkillBand.NEW ->
                "Assume the child is new to this topic. Keep the explanation especially simple and welcoming."
            SkillBand.GROWING ->
                "Assume the child is building confidence. Keep the explanation clear and supportive, then add one small challenge."
            SkillBand.CONFIDENT ->
                "Assume the child is ready for a bit more depth while staying clear, respectful, and concise."
            null -> ""
        }
        val preferences = learnerPreferenceInstruction(learnerProfile)
        return listOf(style, confidence, preferences)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /** Subject tile tuning for delimiter lessons (no JSON). */
    private fun subjectTeachingHint(mode: SubjectMode?): String = when (mode) {
        null ->
            "general — curiosity-first; equations and code as plain lines (no fenced blocks unless tiny snippets); follow-up invites wonder."
        SubjectMode.GENERAL ->
            "general — clear, warm, useful; math, code, and equations as short plain lines."
        SubjectMode.CURIOSITY ->
            "curiosity — intuitive hook first; calculations as plain lines; follow-up invites wonder."
        SubjectMode.MATH ->
            "math — algebra in small plain lines, one step per line when possible; avoid dumping the final answer immediately."
        SubjectMode.SCIENCE ->
            "science — one familiar example in prose; formulas or data as separate plain lines."
        SubjectMode.PHYSICS ->
            "physics — one concrete situation in words; formulas on their own lines."
        SubjectMode.CHEMISTRY ->
            "chemistry — mechanism in prose; equations on their own lines."
        SubjectMode.BIOLOGY ->
            "biology — plain language and one concrete example; sequences on their own lines when needed."
        SubjectMode.CODING ->
            "coding — describe behavior in prose; sample lines indented or plain (no big fenced dumps)."
        SubjectMode.WRITING ->
            "writing — clarity and structure; outlines as short plain lines when helpful."
        SubjectMode.EXAM_PREP ->
            "exam prep — calm and short; key facts on their own lines; strategy in plain sentences."
    }

    private fun languageNameForLocaleTag(tag: String): String = when {
        tag.startsWith("te", ignoreCase = true) -> "Telugu"
        tag.startsWith("hi", ignoreCase = true) -> "Hindi"
        tag.startsWith("en", ignoreCase = true) -> "English"
        else -> "English"
    }

    /**
     * Binds assistant reply language to the user's app language (e.g. [InferenceSettings.speechInputLocaleTag]).
     */
    fun replyLanguageInstruction(localeTag: String): String {
        val name = languageNameForLocaleTag(localeTag)
        return "The user's preferred language for your replies is $name (locale: $localeTag). " +
            "Write every assistant message in $name unless they explicitly ask for a different language, " +
            "or another language is clearly required for code, quotes, or proper names."
    }

    @Suppress("UNUSED_PARAMETER")
    fun buildChatPrompt(
        /** Only this user string is placed in the prompt as `U: …` — no transcript of past turns. */
        lastUserContent: String,
        /** Parsed `[SAVE_CONTEXT]` body from the previous assistant reply; passed as `M:` mentor context. */
        memoryHint: String? = null,
        maxTokensHeadroom: Int = 256,
        promptBudget: Int? = null,
        preferredReplyLocaleTag: String = "en-US",
        userFirstName: String = "",
        userLastName: String = "",
        birthMonth: Int? = null,
        birthYear: Int? = null,
        subjectMode: SubjectMode? = null,
        safetyProfile: SafetyProfile = SafetyProfile.KIDS_STRICT,
        learnerProfile: LearnerProfile = LearnerProfile(),
        subjectSkillBands: Map<SubjectMode, SkillBand> = emptyMap(),
        /** When true, adds a short note that private English reasoning should stay minimal vs output language. */
        requestModelThoughtInJson: Boolean = false,
    ): String {
        val lastUserText = lastUserContent.trim()
        val ageYears = estimatedAgeFromDob(birthMonth, birthYear)
        val age = ageYears?.toString() ?: "10"
        val memory = memoryHint?.trim()?.takeIf { it.isNotBlank() } ?: "None"
        val language = languageNameForLocaleTag(preferredReplyLocaleTag)
        val topic = compactTopicLabel(subjectMode)
        val effectivePedagogyMode = ChatLessonPromptSpecs.effectiveSubjectMode(subjectMode)
        val localeTag = preferredReplyLocaleTag.trim()
        val lessonSpec = ChatLessonPromptSpecs.lessonBodySpec(subjectMode, lastUserText)
        val safetyLine =
            if (safetyProfile == SafetyProfile.KIDS_STRICT) {
                "# Safety: Strict Kids-Safe zone"
            } else {
                "# Safety: Standard kids-safe zone"
            }

        return buildString {
            appendLine("<start_of_turn>user")
            appendLine("# ROLE: ${age}yo Socratic Mentor (GyanGo). !NoIntro | !NoThought | !KIDS_SAFE")
            appendLine("# TOPIC: $topic")
            appendLine("# Subject: ${subjectTeachingHint(effectivePedagogyMode)}")
            appendLine(
                "# LOGIC: U=Task; M=Data. U>M. !InventData. !SafeTone. Anchor [U] to [M].",
            )
            appendLine("# LANG: $language ($localeTag).")
            appendLine(safetyLine)
            appendLine("# GOAL: ${lessonSpec.goalLine}")
            appendLine()
            appendLine("# SECTION GUIDES:")
            appendLine(definitionSectionGuideLine(lessonSpec))
            appendLine("- Analogy: ${lessonSpec.analogyGuide}")
            appendLine("- Application: ${lessonSpec.applicationGuide}")
            appendLine()
            appendLine("# OUTPUT FORMAT (STRICT):")
            appendLine(GyangoOutputEnvelope.OUTPUT_MARKER)
            appendLine("**Definition:** ${lessonSpec.strictOutputDefinitionHint}")
            appendLine("**Analogy:** ${lessonSpec.strictOutputAnalogyHint}")
            appendLine("**Application:** ${lessonSpec.strictOutputApplicationHint}")
            appendLine("# Rule: No model control tokens or broken turn fragments; only readable lesson text in those three sections.")
            appendLine()
            appendLine(
                "${GyangoOutputEnvelope.STATE_LINE_PREFIX} Var:[KeyVar]; Goal:[CurrentObjective]; Skill:[MasteredStep]",
            )
            appendLine()
            appendLine(GyangoOutputEnvelope.SPARKS_MARKER)
            appendLine(
                "[5-8 word contextual bridge]${GyangoOutputEnvelope.SPARK_CHIPS_DELIMITER}[5-8 word contextual bridge]",
            )
            appendLine()
            appendLine(GyangoOutputEnvelope.SAVE_CONTEXT_MARKER)
            appendLine("Topic:$topic|Sub:[SubTopic]|Var:[Var]|Last:[LogicStep]|Next:[NextPrerequisite]")
            appendLine()
            appendLine(GyangoOutputEnvelope.USER_CURIOSITY_MARKER)
            appendLine("$language: [12-word area student showed interest in]")
            appendLine()
            appendLine("M: $memory")
            append("U: ")
            append(lastUserText)
            append("<end_of_turn>")
            appendLine()
            appendLine("<start_of_turn>model")
            appendLine(GyangoOutputEnvelope.OUTPUT_MARKER)
            appendLine("**Definition:**")
        }.trimEnd { it == ' ' || it == '\t' }
    }

    fun formatGyangoSingleTurn(
        userTask: String,
        preferredReplyLocaleTag: String = "en-US",
        safetyProfile: SafetyProfile = SafetyProfile.KIDS_STRICT,
        birthMonth: Int? = null,
        birthYear: Int? = null,
        role: String = "General",
        iqLevel: Int? = null,
        requestModelThoughtInJson: Boolean = false,
    ): String {
        val body = userTask.trim()
        val sb = StringBuilder()
        sb.append("User: ")
        sb.append(gyangoAssistantFingerprint)
        sb.append(" ")
        sb.append(gyangoPrimingPlainMid)
        sb.append(AssistantLlmSanitizer.gyangoPrimingUserChatPlainTail)
        sb.append(" ")
        sb.append(ageSafetyInstruction(birthMonth, birthYear))
        sb.append(" ")
        sb.append(
            buildTutorSystemTemplate(
                TutorPromptContext(
                    role = role,
                    age = estimatedAgeFromDob(birthMonth, birthYear),
                    iqLevel = iqLevel,
                    preferredReplyLocaleTag = preferredReplyLocaleTag,
                    safetyProfile = safetyProfile,
                    subjectMode = null,
                ),
                birthMonth = birthMonth,
                birthYear = birthYear,
                learnerProfile = LearnerProfile(),
                subjectSkillBands = emptyMap(),
                requestModelThoughtInJson = requestModelThoughtInJson,
            )
        )
        sb.append(gyangoPrimingJsonHint)
        sb.append("\n\n")
        sb.append("Assistant: ")
        sb.append(gyangoPrimingAssistant)
        sb.append("\n\n")
        sb.append("User: ")
        sb.append(body)
        sb.append("\n\n")
        sb.append("Assistant:\n")
        return sb.toString()
    }

    fun buildRawPrompt(systemPrompt: String?, userText: String): String {
        return if (systemPrompt != null && systemPrompt.isNotBlank()) {
            "$systemPrompt\n\n$userText"
        } else {
            userText
        }
    }
}
