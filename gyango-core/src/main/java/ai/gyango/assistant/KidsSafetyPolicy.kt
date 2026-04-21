package ai.gyango.assistant

import ai.gyango.core.SafetyProfile

enum class SafetyDecision {
    ALLOW,
    REDIRECT,
    BLOCK,
}

data class SafetyCheckResult(
    val decision: SafetyDecision,
    val category: String? = null,
    val safeReply: String? = null,
)

object KidsSafetyPolicy {
    private data class Rule(
        val category: String,
        val decision: SafetyDecision,
        val patterns: List<Regex>,
    )

    private val rules = listOf(
        Rule(
            category = "sexual_content",
            decision = SafetyDecision.BLOCK,
            patterns = listOf(
                Regex("""\bsex\b|\bsexy\b|\bkiss\b|\bmake out\b|\bromantic roleplay\b|\bporn\b|\bnudes?\b""", RegexOption.IGNORE_CASE),
            ),
        ),
        Rule(
            category = "self_harm",
            decision = SafetyDecision.BLOCK,
            patterns = listOf(
                Regex("""\bkill myself\b|\bsuicide\b|\bself harm\b|\bcut myself\b|\bhow to die\b""", RegexOption.IGNORE_CASE),
            ),
        ),
        Rule(
            category = "violence_weapons",
            decision = SafetyDecision.BLOCK,
            patterns = listOf(
                Regex("""\bgun\b|\bknife attack\b|\bbomb\b|\bexplosive\b|\bhurt someone\b|\bpoison\b""", RegexOption.IGNORE_CASE),
            ),
        ),
        Rule(
            category = "drugs_alcohol",
            decision = SafetyDecision.BLOCK,
            patterns = listOf(
                Regex("""\bweed\b|\bdrugs?\b|\bvape\b|\balcohol\b|\bdrunk\b|\bsmoke\b""", RegexOption.IGNORE_CASE),
            ),
        ),
        Rule(
            category = "grooming_or_secrecy",
            decision = SafetyDecision.REDIRECT,
            patterns = listOf(
                Regex("""\bsecret from (mom|dad|parent|parents|guardian|teacher)\b|\bdon't tell my (mom|dad|parents|guardian)\b""", RegexOption.IGNORE_CASE),
            ),
        ),
        Rule(
            category = "crime_cheating",
            decision = SafetyDecision.REDIRECT,
            patterns = listOf(
                Regex("""\bsteal\b|\bshoplift\b|\bcheat on (a )?test\b|\bhack\b|\bskip school\b|\blie to my teacher\b""", RegexOption.IGNORE_CASE),
            ),
        ),
    )

    fun screenInput(
        text: String,
        safetyProfile: SafetyProfile,
    ): SafetyCheckResult {
        if (safetyProfile != SafetyProfile.KIDS_STRICT) {
            return SafetyCheckResult(SafetyDecision.ALLOW)
        }
        val hit = rules.firstOrNull { rule -> rule.patterns.any { it.containsMatchIn(text) } }
            ?: return SafetyCheckResult(SafetyDecision.ALLOW)
        return SafetyCheckResult(
            decision = hit.decision,
            category = hit.category,
            safeReply = safeReplyForCategory(hit.category),
        )
    }

    fun moderateOutput(
        text: String,
        safetyProfile: SafetyProfile,
    ): SafetyCheckResult {
        if (safetyProfile != SafetyProfile.KIDS_STRICT) {
            return SafetyCheckResult(SafetyDecision.ALLOW)
        }
        return screenInput(text, safetyProfile)
    }

    private fun safeReplyForCategory(category: String): String = when (category) {
        "sexual_content" ->
            "I can't help with grown-up or explicit topics. If you have a question about your body or feelings, please ask a parent, guardian, teacher, or another trusted adult."
        "self_harm" ->
            "I'm really glad you asked. I can't help with hurting yourself. Please tell a parent, guardian, teacher, counselor, or another trusted adult right now so they can help you."
        "violence_weapons" ->
            "I can't help with hurting people, weapons, or dangerous instructions. If this is about safety, please get help from a trusted adult right away."
        "drugs_alcohol" ->
            "I can't help with drugs, vaping, or alcohol instructions. If you're worried about something you saw or heard, a trusted adult can help you talk it through safely."
        "grooming_or_secrecy" ->
            "I can't help keep risky secrets from trusted adults. If something feels confusing or unsafe, please talk to a parent, guardian, teacher, or another trusted adult."
        "crime_cheating" ->
            "I can't help with cheating, stealing, or tricking adults. I can help you solve the problem in a safe and honest way instead."
        else ->
            "I want to keep this safe for kids. Let's switch to a safer question, or ask a trusted adult if this is important."
    }
}
