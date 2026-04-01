package ai.pocket.audio

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
object JsonPretty {
    private val lenientParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** Best-effort pretty-print for UI; returns [raw] unchanged if parsing fails. */
    fun formatForDisplay(raw: String): String {
        if (raw.isBlank()) return raw
        val trimmed = raw.trim()
        val candidate = AudioSentimentResult.extractFirstJsonObject(trimmed) ?: trimmed
        return try {
            val element = lenientParser.parseToJsonElement(candidate)
            pretty.encodeToString(JsonElement.serializer(), element)
        } catch (_: Exception) {
            raw
        }
    }
}
