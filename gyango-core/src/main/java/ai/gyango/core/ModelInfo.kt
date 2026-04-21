package ai.gyango.core

/**
 * Metadata about a registered model.
 */
data class ModelInfo(
    val modelId: String,
    val displayName: String,
    val capabilities: List<String> = emptyList()
)
