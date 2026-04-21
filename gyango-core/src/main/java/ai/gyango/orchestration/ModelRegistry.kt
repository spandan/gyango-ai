package ai.gyango.orchestration

import ai.gyango.core.LlmInterface
import ai.gyango.core.ModelInfo

/**
 * Registry of available LLM implementations.
 * Models register themselves; orchestrator selects based on routing policy.
 */
interface ModelRegistry {
    fun register(model: LlmInterface)
    fun get(modelId: String): LlmInterface?
    fun listAll(): List<LlmInterface>
    fun getModelInfo(modelId: String): ModelInfo?
}

/**
 * Simple in-memory implementation.
 */
class DefaultModelRegistry : ModelRegistry {
    private val models = mutableMapOf<String, LlmInterface>()

    override fun register(model: LlmInterface) {
        models[model.modelId] = model
    }

    override fun get(modelId: String): LlmInterface? = models[modelId]

    override fun listAll(): List<LlmInterface> = models.values.toList()

    override fun getModelInfo(modelId: String): ModelInfo? {
        val model = models[modelId] ?: return null
        return ModelInfo(
            modelId = model.modelId,
            displayName = model.modelId.replace("-", " ").replaceFirstChar { it.uppercase() },
            capabilities = listOf("chat", "summarize", "analyze", "draft")
        )
    }
}
