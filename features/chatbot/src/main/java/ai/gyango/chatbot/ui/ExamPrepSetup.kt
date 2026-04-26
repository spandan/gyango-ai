package ai.gyango.chatbot.ui

/**
 * Content lane for an Exam Prep session (maps to prompt `{{TOPIC}}` / CONTEXT Topic bracket).
 */
enum class ExamPrepContentLane {
    GENERAL,
    MATH,
    SCIENCE,
    ;

    val promptLabel: String get() = name
}

/** In-progress choices before the first LLM turn. */
data class ExamPrepWizardDraft(
    val lane: ExamPrepContentLane? = null,
    /** Set after the learner confirms the sub-topic step. */
    val subtopic: String? = null,
)

/** Frozen after the wizard completes; used for every exam-prep prompt in the thread. */
data class ExamPrepSessionConfig(
    val lane: ExamPrepContentLane,
    val subtopic: String,
    val questionCount: Int,
)
