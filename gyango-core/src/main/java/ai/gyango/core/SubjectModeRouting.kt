package ai.gyango.core

/** Maps legacy science splits to the single Science tile; other modes unchanged. */
object SubjectModeRouting {
    fun effectiveSubjectMode(mode: SubjectMode?): SubjectMode? = when (mode) {
        SubjectMode.PHYSICS, SubjectMode.CHEMISTRY, SubjectMode.BIOLOGY -> SubjectMode.SCIENCE
        else -> mode
    }
}
