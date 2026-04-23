package ai.gyango.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Sender {
    USER, ASSISTANT
}

@Serializable
data class ChatMessage(
    val id: Long,
    val sender: Sender,
    val text: String,
    val timestamp: Long,
    /**
     * Parsed `CONTEXT >>` line from the last assistant turn; passed as `M:` on the next prompt.
     * Not shown in the bubble. JSON key was `outputMemoryHint` in older app versions.
     */
    @SerialName("outputMemoryHint")
    val outputContext: String? = null,
    /** Parsed `CURIOSITY >>` line (saved / telemetry). */
    val outputCuriosity: String? = null,
    /** Parsed `SPARKS >>` body (`||`-delimited, up to 3 chips). */
    val outputSparksCsv: String? = null,
    /**
     * Full assistant stream after sanitizer (lesson + fenced mentor tail).
     * Used for spark parsing and lesson layout; [text] is the user-visible merged prose.
     */
    val rawAssistantEnvelope: String? = null,
)
