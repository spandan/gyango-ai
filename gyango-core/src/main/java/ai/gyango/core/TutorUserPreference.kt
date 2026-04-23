package ai.gyango.core

/**
 * Maps starter check-in answer indices (0..3 per question) into a compact MODE line for lesson prompts.
 * When check-in was skipped or answers are missing, use [genericModeLine].
 */
object TutorUserPreference {

    const val GENERIC_MODE_LINE: String =
        "GENERIC — balanced, clear; answer first, depth only if useful."

    fun modeLineFromCheckIn(
        answerIndices: List<Int>,
        starterCheckInCompleted: Boolean,
    ): String {
        if (!starterCheckInCompleted || answerIndices.isEmpty()) return GENERIC_MODE_LINE
        val vals = answerIndices.filter { it in 0..3 }
        if (vals.isEmpty()) return GENERIC_MODE_LINE
        val avg = vals.average()
        val supportLean = vals.count { it <= 1 }
        val challengeLean = vals.count { it >= 3 }
        return when {
            challengeLean >= supportLean + 3 ->
                "CHALLENGE — add stretch connections, concise rigor, and one harder follow angle when it fits."
            supportLean >= challengeLean + 3 ->
                "SUPPORTIVE — shorter sentences, extra reassurance, smaller steps, and one gentle check."
            avg < 1.25 ->
                "EASY_PACE — keep cognitive load low; prioritize clarity and familiar examples."
            avg > 2.35 ->
                "DEEPER — include why/how links, careful reasoning, and one meaningful extension."
            else ->
                "BALANCED — clear core answer, one concrete example, then a compact deeper layer."
        }
    }
}
