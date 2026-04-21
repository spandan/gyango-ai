package ai.gyango.core

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
     * Parsed `[SAVE_CONTEXT]` body from the last assistant turn; passed as `M:` on the next prompt.
     * Not shown in the bubble.
     */
    val outputMemoryHint: String? = null,
    /** Parsed `---SPARKS---` line (`||`-delimited chips) for chip buttons on this assistant bubble. */
    val outputSparksCsv: String? = null,
    /**
     * Full assistant stream after sanitizer (still includes `**Definition:**` markers, `---SPARKS---`, etc.).
     * Used only for UI structure (section tints); [text] is the user-visible merged prose.
     */
    val rawAssistantEnvelope: String? = null,
)
