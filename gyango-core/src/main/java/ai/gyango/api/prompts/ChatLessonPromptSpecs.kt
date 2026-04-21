package ai.gyango.api.prompts

import ai.gyango.core.SubjectMode

/**
 * Per–subject-mode instructions for [ai.gyango.api.PromptBuilder.buildChatPrompt].
 *
 * Headers stay **Definition / Analogy / Application** everywhere so
 * [ai.gyango.assistant.AssistantLessonStructureParser] does not need changes — only the *meaning* of
 * each slot changes by mode. Edit copy here; keep lines concise for on-device context limits.
 */
object ChatLessonPromptSpecs {

    /** Map legacy science splits to the single Science tile; leave other modes unchanged. */
    fun effectiveSubjectMode(mode: SubjectMode?): SubjectMode? = when (mode) {
        SubjectMode.PHYSICS, SubjectMode.CHEMISTRY, SubjectMode.BIOLOGY -> SubjectMode.SCIENCE
        else -> mode
    }

    /**
     * GENERAL and CURIOSITY share one “general learner” profile in Phase 1 (single tile in UI).
     */
    private fun normalizedMode(mode: SubjectMode?): SubjectMode = when (effectiveSubjectMode(mode)) {
        null, SubjectMode.GENERAL, SubjectMode.CURIOSITY -> SubjectMode.GENERAL
        else -> effectiveSubjectMode(mode)!!
    }

    data class LessonBodySpec(
        /** Replaces the single `# Goal:` line in the chat system header. */
        val goalLine: String,
        /** `#` comment: what belongs under **Definition:** */
        val definitionGuide: String,
        /** `#` comment: what belongs under **Analogy:** */
        val analogyGuide: String,
        /** `#` comment: what belongs under **Application:** */
        val applicationGuide: String,
        /**
         * Bracket hints after each `**…:**` line under `# OUTPUT FORMAT (STRICT)` — must stay on one line
         * each; mirrors this template’s section guides so the model does not follow a stale generic skeleton.
         */
        val strictOutputDefinitionHint: String,
        val strictOutputAnalogyHint: String,
        val strictOutputApplicationHint: String,
    )

    private enum class LessonTemplateId {
        GENERAL_DEFAULT,
        GENERAL_COMPARE,
        MATH_SOLVE,
        MATH_CONCEPT,
        SCIENCE_CONCEPT,
        SCIENCE_COMPARE,
        CODING_BUILD,
        CODING_DEBUG,
        WRITING_COMPOSE,
        WRITING_EDIT,
        EXAM_STRATEGY,
        EXAM_RECALL,
    }

    private val solveKeywords = setOf("solve", "calculate", "find", "evaluate", "derive", "prove", "simplify")
    private val explainKeywords = setOf("what is", "explain", "why", "concept", "meaning", "define")
    private val compareKeywords = setOf("difference", "differentiate", "compare", "vs", "versus", "distinguish")
    private val codingDebugKeywords = setOf("debug", "fix", "error", "exception", "crash", "not working", "issue")
    private val writingEditKeywords = setOf("improve", "rewrite", "edit", "grammar", "correct", "refine")
    private val examStrategyKeywords = setOf("strategy", "plan", "revision", "time management", "tips", "approach")
    private val examRecallKeywords = setOf("important questions", "pyq", "mcq", "5 marks", "2 marks", "short note")

    fun lessonBodySpec(mode: SubjectMode?, userQuestion: String?): LessonBodySpec {
        val normalizedMode = normalizedMode(mode)
        val template = selectTemplate(normalizedMode, userQuestion)
        return lessonSpecForTemplate(template)
    }

    fun lessonBodySpec(mode: SubjectMode?): LessonBodySpec = lessonBodySpec(mode, userQuestion = null)

    private fun selectTemplate(mode: SubjectMode, userQuestion: String?): LessonTemplateId {
        val q = normalizeQuestion(userQuestion)
        return when (mode) {
            SubjectMode.MATH -> {
                when {
                    q.hasAny(solveKeywords) -> LessonTemplateId.MATH_SOLVE
                    q.hasAny(explainKeywords) -> LessonTemplateId.MATH_CONCEPT
                    else -> LessonTemplateId.MATH_SOLVE
                }
            }
            SubjectMode.SCIENCE -> {
                when {
                    q.hasAny(compareKeywords) -> LessonTemplateId.SCIENCE_COMPARE
                    else -> LessonTemplateId.SCIENCE_CONCEPT
                }
            }
            SubjectMode.CODING -> {
                when {
                    q.hasAny(codingDebugKeywords) -> LessonTemplateId.CODING_DEBUG
                    else -> LessonTemplateId.CODING_BUILD
                }
            }
            SubjectMode.WRITING -> {
                when {
                    q.hasAny(writingEditKeywords) -> LessonTemplateId.WRITING_EDIT
                    else -> LessonTemplateId.WRITING_COMPOSE
                }
            }
            SubjectMode.EXAM_PREP -> {
                when {
                    q.hasAny(examRecallKeywords) -> LessonTemplateId.EXAM_RECALL
                    q.hasAny(examStrategyKeywords) -> LessonTemplateId.EXAM_STRATEGY
                    else -> LessonTemplateId.EXAM_STRATEGY
                }
            }
            else -> {
                when {
                    q.hasAny(compareKeywords) -> LessonTemplateId.GENERAL_COMPARE
                    else -> LessonTemplateId.GENERAL_DEFAULT
                }
            }
        }
    }

    private fun normalizeQuestion(userQuestion: String?): String {
        return userQuestion
            ?.lowercase()
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            .orEmpty()
    }

    private fun String.hasAny(keywords: Set<String>): Boolean {
        if (isBlank()) return false
        return keywords.any { keyword -> contains(keyword) }
    }

    private fun lessonSpecForTemplate(template: LessonTemplateId): LessonBodySpec = when (template) {
        LessonTemplateId.MATH_SOLVE -> LessonBodySpec(
            goalLine = "Answer with clear problem-solving: restate the task, show algebra or steps line-by-line, give the final result, and verify when possible.",
            definitionGuide = "Restate what is given and what to find (no solving here). Plain sentences; key expressions on their own lines.",
            analogyGuide = "Optional one-sentence intuition for the steps. Skip a forced home-object analogy if it does not fit.",
            applicationGuide = "Final answer with units if any; one-line check (substitute, sanity, or alternate path) when reasonable.",
            strictOutputDefinitionHint = "[Given + unknowns; equations from U on separate plain lines — no solving yet]",
            strictOutputAnalogyHint = "[One optional intuition line, or exactly: Skip:]",
            strictOutputApplicationHint = "[Algebra step-by-step → final X,Y (with units if any) → one-line verify]",
        )
        LessonTemplateId.MATH_CONCEPT -> LessonBodySpec(
            goalLine = "Teach the math concept with clarity first, then connect to one quick mini-problem.",
            definitionGuide = "Define the idea in plain terms and mention key symbols once.",
            analogyGuide = "Use one short intuition bridge only if it genuinely helps understanding.",
            applicationGuide = "Give one tiny worked use-case that shows when the concept is used.",
            strictOutputDefinitionHint = "[Name the idea + plain definition; key symbols once]",
            strictOutputAnalogyHint = "[One short bridge or exactly: Skip:]",
            strictOutputApplicationHint = "[One micro numeric example tying concept to use]",
        )
        LessonTemplateId.SCIENCE_COMPARE -> LessonBodySpec(
            goalLine = "Explain the science comparison clearly and concisely, then show why the difference matters.",
            definitionGuide = "State each item in one plain sentence before contrasting.",
            analogyGuide = "Use one concrete comparison to make the distinction memorable.",
            applicationGuide = "Give one practical context where choosing the right distinction matters.",
            strictOutputDefinitionHint = "[Item A + Item B in plain words before contrasting]",
            strictOutputAnalogyHint = "[One memorable contrast image]",
            strictOutputApplicationHint = "[Where the distinction matters in lab or daily life]",
        )
        LessonTemplateId.SCIENCE_CONCEPT -> LessonBodySpec(
            goalLine = "Explain the science clearly: concept, evidence or reasoning, then why it matters. Formulas or data on separate lines.",
            definitionGuide = "Core idea in plain words (what is going on).",
            analogyGuide = "One concrete comparison or picture-in-words that fits the topic.",
            applicationGuide = "One real-world or lab-life tie-in.",
            strictOutputDefinitionHint = "[Core mechanism in plain words]",
            strictOutputAnalogyHint = "[One concrete comparison]",
            strictOutputApplicationHint = "[One real-world or lab tie-in]",
        )
        LessonTemplateId.CODING_DEBUG -> LessonBodySpec(
            goalLine = "Help debug code: identify likely cause, propose focused fix, then suggest a quick validation step.",
            definitionGuide = "Restate the observed behavior and likely root cause in plain language.",
            analogyGuide = "Optional mental model for why this bug pattern happens; skip if forced.",
            applicationGuide = "Show minimal corrected code or checks, plus one test to confirm the fix.",
            strictOutputDefinitionHint = "[Symptom + suspected cause in prose]",
            strictOutputAnalogyHint = "[Optional mental model or exactly: Skip:]",
            strictOutputApplicationHint = "[Minimal fix + how to verify]",
        )
        LessonTemplateId.CODING_BUILD -> LessonBodySpec(
            goalLine = "Help with code: goal, short approach, minimal correct snippet, one common pitfall.",
            definitionGuide = "What the program should do - inputs/outputs in plain language.",
            analogyGuide = "Optional everyday process analogy; skip if unnatural.",
            applicationGuide = "Short code sample if needed (fenced block allowed); one pitfall or test idea.",
            strictOutputDefinitionHint = "[Goal + I/O in plain language]",
            strictOutputAnalogyHint = "[Optional process analogy or exactly: Skip:]",
            strictOutputApplicationHint = "[Short snippet + pitfall or test]",
        )
        LessonTemplateId.WRITING_EDIT -> LessonBodySpec(
            goalLine = "Support revision: preserve intent, improve clarity, and give one concrete improvement principle.",
            definitionGuide = "State the current draft goal, audience, and what needs improvement.",
            analogyGuide = "Use one optional clarity analogy if it helps explain the revision choice.",
            applicationGuide = "Provide a focused before/after rewrite or a brief revision checklist.",
            strictOutputDefinitionHint = "[Goal, audience, what to improve]",
            strictOutputAnalogyHint = "[Optional clarity analogy or exactly: Skip:]",
            strictOutputApplicationHint = "[Before/after snippet or revision checklist]",
        )
        LessonTemplateId.WRITING_COMPOSE -> LessonBodySpec(
            goalLine = "Support writing: audience, structure, one concrete craft tip.",
            definitionGuide = "Topic, audience, and tone in one or two short sentences.",
            analogyGuide = "One comparison that clarifies structure or voice (optional if awkward).",
            applicationGuide = "Mini-outline (hook -> body -> close) or one before/after wording tweak.",
            strictOutputDefinitionHint = "[Topic + audience + tone]",
            strictOutputAnalogyHint = "[Structure/voice comparison or exactly: Skip:]",
            strictOutputApplicationHint = "[Mini-outline hook→body→close OR one wording tweak]",
        )
        LessonTemplateId.EXAM_RECALL -> LessonBodySpec(
            goalLine = "Exam-style help for recall-heavy questions: concise must-know points and quick memory anchors.",
            definitionGuide = "State what this question expects in scoring language.",
            analogyGuide = "Optional short memory hook only if it improves recall speed.",
            applicationGuide = "Give a compact answer frame (points/order) for fast exam writing.",
            strictOutputDefinitionHint = "[What markers reward in one line]",
            strictOutputAnalogyHint = "[Optional recall hook or exactly: Skip:]",
            strictOutputApplicationHint = "[Compact points/order answer frame]",
        )
        LessonTemplateId.EXAM_STRATEGY -> LessonBodySpec(
            goalLine = "Exam-style help: calm tone, must-know points briefly, practical strategy.",
            definitionGuide = "What this question type rewards (recall, steps, or method) in one tight sentence.",
            analogyGuide = "Optional memory hook; skip if it weakens clarity.",
            applicationGuide = "Short checklist or order-of-attack the student can reuse.",
            strictOutputDefinitionHint = "[Recall vs steps vs method — one tight line]",
            strictOutputAnalogyHint = "[Optional memory hook or exactly: Skip:]",
            strictOutputApplicationHint = "[Reusable checklist or order-of-attack]",
        )
        LessonTemplateId.GENERAL_COMPARE -> LessonBodySpec(
            goalLine = "Give a clear side-by-side explanation, then help the learner decide based on context.",
            definitionGuide = "Define both items briefly before comparing.",
            analogyGuide = "Use one relatable contrast that makes the difference intuitive.",
            applicationGuide = "Suggest when to choose each option in real use.",
            strictOutputDefinitionHint = "[A and B defined briefly]",
            strictOutputAnalogyHint = "[One intuitive contrast]",
            strictOutputApplicationHint = "[When to pick A vs B in practice]",
        )
        LessonTemplateId.GENERAL_DEFAULT -> LessonBodySpec(
            goalLine = "Warm, clear help for homework, life questions, or curiosity - match U: (practical vs wonder).",
            definitionGuide = "Clear explanation of the idea or direct answer to their question.",
            analogyGuide = "Relatable comparison when it helps; otherwise a second angle without forcing a household object.",
            applicationGuide = "Concrete use, next step, or gentle follow-up angle.",
            strictOutputDefinitionHint = "[Direct answer or core idea from U]",
            strictOutputAnalogyHint = "[Relatable angle or second explanation — or exactly: Skip:]",
            strictOutputApplicationHint = "[Concrete next step or use case]",
        )
    }
}
