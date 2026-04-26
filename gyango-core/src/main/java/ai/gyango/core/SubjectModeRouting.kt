package ai.gyango.core

/** Subject routing is now identity; science sub-modes were retired. */
object SubjectModeRouting {
    fun effectiveSubjectMode(mode: SubjectMode?): SubjectMode? = mode
}
