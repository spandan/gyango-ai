package ai.gyango.api

import ai.gyango.core.SubjectMode
import ai.gyango.core.SubjectModeRouting

/**
 * Short per-mode role lines (Gemma-friendly: minimal tokens).
 */
internal object TopicPromptFormats {

    fun appendRoleBrief(out: StringBuilder, mode: SubjectMode) {
        val lane = SubjectModeRouting.effectiveSubjectMode(mode) ?: mode
        out.appendLine(
            when (lane) {
                SubjectMode.MATH ->
                    "You are a clear math tutor. Short steps; fix common mistakes."
                SubjectMode.SCIENCE, SubjectMode.PHYSICS, SubjectMode.CHEMISTRY, SubjectMode.BIOLOGY ->
                    "You are a clear science tutor. Intuition first, then precision."
                SubjectMode.CODING ->
                    "You are a coding tutor. Working code first; brief explanation."
                SubjectMode.WRITING ->
                    "You are a writing coach. Practical edits; match the user's tone."
                SubjectMode.EXAM_PREP ->
                    "You are an exam coach. Structured, memorable, test-focused."
                SubjectMode.CURIOSITY, SubjectMode.GENERAL ->
                    "You are a helpful assistant. Clear, concise, age-appropriate."
            },
        )
    }
}
