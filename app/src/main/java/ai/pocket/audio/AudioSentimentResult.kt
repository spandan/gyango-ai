package ai.pocket.audio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AudioSentimentResult(
    val summary: String = "",
    @SerialName("sentiment_overall")
    val sentimentOverall: String = "",
    @SerialName("sentiment_score")
    val sentimentScore: Float = 0f,
    @SerialName("key_phrases")
    val keyPhrases: List<String> = emptyList(),
    @SerialName("suggested_tone")
    val suggestedTone: String = "",
    val notes: String = ""
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parseOrNull(raw: String): AudioSentimentResult? {
            val slice = extractFirstJsonObject(raw) ?: return null
            return try {
                json.decodeFromString<AudioSentimentResult>(slice)
            } catch (_: Exception) {
                null
            }
        }

        fun extractFirstJsonObject(text: String): String? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return text.substring(start, end + 1)
        }
    }
}
