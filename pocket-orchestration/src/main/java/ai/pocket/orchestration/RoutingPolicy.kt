package ai.pocket.orchestration

import ai.pocket.core.GenerateRequest
import ai.pocket.core.LlmInterface

/**
 * Decides which model to use for a given request.
 * Extensible for future: device capability, load, user preference, tool hints.
 */
interface RoutingPolicy {
    fun selectModel(request: OrchestrationRequest, available: List<LlmInterface>): LlmInterface?
}

/**
 * Simple policy: prefer requested model, else first available.
 */
class DefaultRoutingPolicy(
    private val defaultModelId: String? = null
) : RoutingPolicy {
    override fun selectModel(request: OrchestrationRequest, available: List<LlmInterface>): LlmInterface? {
        if (available.isEmpty()) return null
        val preferred = request.preferredModel ?: defaultModelId
        return available.find { it.modelId == preferred } ?: available.first()
    }
}
