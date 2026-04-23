package ai.gyango.core

/**
 * Zero-latency heuristic: pick a [SubjectMode] for the prompt from the user utterance.
 * When [InferenceSettings.autoRouteSubject] is false, the UI-selected tile is used instead.
 */
object SubjectModeAutoRouter {

    private val codingSignals = Regex(
        """(?i)\b(code|coding|python|kotlin|java|javascript|typescript|function|class\s+\w+|debug|compiler|api|json|sql|html|css|git|algorithm|leetcode|import\s+\w+|def\s+\w+|val\s+\w+|var\s+\w+)\b""",
    )
    private val mathSignals = Regex(
        """(?i)\b(equation|solve|integral|derivative|algebra|geometry|calculus|matrix|fraction|percent|graph|theorem|proof|sin\(|cos\(|log\s*\(|sqrt)\b""",
    )
    private val examSignals = Regex(
        """(?i)\b(exam|test prep|sat|act|gre|quiz|revision|study guide|past paper|marks|grade|board exam|entrance)\b""",
    )
    private val writingSignals = Regex(
        """(?i)\b(essay|paragraph|rewrite|proofread|story|poem|creative writing|grammar|thesis|draft|tone|voice|narrative)\b""",
    )
    private val scienceSignals = Regex(
        """(?i)\b(photosynthesis|cell|molecule|atom|reaction|physics|chemistry|biology|experiment|hypothesis|dna|rna|gravity|energy|ecosystem|element)\b""",
    )

    /** Light social openers: keep on GENERAL/CURIOSITY role, not the broad “any ? ⇒ CURIOSITY” path. */
    private val lightweightSocial = Regex(
        """(?i)^\s*(hi+|hello|hey|how\s+are\s+you|what'?s\s+up|good\s+(morning|afternoon|evening)|namaste)\b""",
    )

    fun route(userText: String): SubjectMode {
        val t = userText.trim()
        if (t.isEmpty()) return SubjectMode.GENERAL
        if (lightweightSocial.containsMatchIn(t)) return SubjectMode.GENERAL
        if (codingSignals.containsMatchIn(t)) return SubjectMode.CODING
        if (mathSignals.containsMatchIn(t)) return SubjectMode.MATH
        if (examSignals.containsMatchIn(t)) return SubjectMode.EXAM_PREP
        if (writingSignals.containsMatchIn(t)) return SubjectMode.WRITING
        if (scienceSignals.containsMatchIn(t)) return SubjectMode.SCIENCE
        if (t.contains('?')) return SubjectMode.CURIOSITY
        return SubjectMode.GENERAL
    }
}
