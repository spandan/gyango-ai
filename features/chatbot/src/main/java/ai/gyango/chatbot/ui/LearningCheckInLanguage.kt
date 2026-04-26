package ai.gyango.chatbot.ui

import java.util.Locale

/**
 * Maps [InferenceSettings.speechInputLocaleTag] (BCP 47) to a primary ISO 639-1 language code so
 * learning check-in prompts and preference questions match the learner’s chosen UI/speech locale.
 */
fun learningCheckInContentLanguage(localeTag: String): String {
    val normalized = localeTag.trim().replace('_', '-')
    if (normalized.isEmpty()) return "en"
    val language = try {
        Locale.forLanguageTag(normalized).language.lowercase(Locale.ROOT)
    } catch (_: Throwable) {
        ""
    }.ifBlank { "en" }
    return when (language) {
        "te", "hi", "en", "mr", "ta", "bn", "gu", "kn", "ml", "pa", "fr", "es" -> language
        else -> "en"
    }
}
