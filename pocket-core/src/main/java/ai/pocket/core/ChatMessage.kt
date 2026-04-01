package ai.pocket.core

import kotlinx.serialization.Serializable

enum class Sender {
    USER, ASSISTANT
}

@Serializable
data class ChatMessage(
    val id: Long,
    val sender: Sender,
    val text: String,
    val timestamp: Long
)
