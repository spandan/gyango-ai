package ai.gyango.core

/**
 * On-device decode sampling (temperature / top-p / top-k) **per [SubjectMode]**.
 *
 * **Phase 1:** these values are **ship-curated only**—learners have no in-app control over
 * temperature / top-p / top-k; the app always applies the table below for the active topic.
 *
 * Each topic has its own `when` branch so maintainers can tune one mode without affecting others.
 * Shared numeric constants live on [LlmDefaults] where several topics intentionally match today.
 */
data class TopicSampling(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
)

fun topicSamplingForSubject(subjectMode: SubjectMode?): TopicSampling = when (subjectMode) {
    null,
    SubjectMode.GENERAL,
    ->
        TopicSampling(
            temperature = LlmDefaults.SAMPLING_DEFAULT_TEMPERATURE,
            topP = LlmDefaults.SAMPLING_DEFAULT_TOP_P,
            topK = LlmDefaults.SAMPLING_DEFAULT_TOP_K,
        )
    SubjectMode.MATH ->
        TopicSampling(
            temperature = LlmDefaults.TOPIC_MATH_SCIENCE_TEMPERATURE,
            topP = LlmDefaults.TOPIC_MATH_SCIENCE_TOP_P,
            topK = LlmDefaults.TOPIC_MATH_SCIENCE_TOP_K,
        )
    SubjectMode.SCIENCE ->
        TopicSampling(
            temperature = LlmDefaults.TOPIC_MATH_SCIENCE_TEMPERATURE,
            topP = LlmDefaults.TOPIC_MATH_SCIENCE_TOP_P,
            topK = LlmDefaults.TOPIC_MATH_SCIENCE_TOP_K,
        )
    SubjectMode.CODING ->
        TopicSampling(
            temperature = LlmDefaults.TOPIC_CODING_TEMPERATURE,
            topP = LlmDefaults.TOPIC_CODING_TOP_P,
            topK = LlmDefaults.TOPIC_CODING_TOP_K,
        )
    SubjectMode.WRITING ->
        TopicSampling(
            temperature = LlmDefaults.TOPIC_WRITING_TEMPERATURE,
            topP = LlmDefaults.TOPIC_WRITING_TOP_P,
            topK = LlmDefaults.TOPIC_WRITING_TOP_K,
        )
    SubjectMode.EXAM_PREP ->
        TopicSampling(
            temperature = LlmDefaults.TOPIC_EXAM_PREP_TEMPERATURE,
            topP = LlmDefaults.TOPIC_EXAM_PREP_TOP_P,
            topK = LlmDefaults.TOPIC_EXAM_PREP_TOP_K,
        )
}
