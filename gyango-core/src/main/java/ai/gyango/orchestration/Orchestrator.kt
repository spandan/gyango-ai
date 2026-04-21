package ai.gyango.orchestration

import ai.gyango.core.LoadingPhase
import ai.gyango.core.ModelInfo

/**
 * Single app entry point for inference requests from chat and related in-app flows.
 * Routes requests to appropriate models via [ModelRegistry] and [RoutingPolicy].
 */
interface Orchestrator {
    suspend fun generate(
        request: OrchestrationRequest,
        onToken: (String) -> Unit,
        onPhaseChange: ((LoadingPhase) -> Unit)? = null
    )

    fun getAvailableModels(): List<ModelInfo>
    fun getDefaultModelId(): String?

    /** Load the default model weights into RAM if supported. */
    suspend fun warmUpDefaultModel()

    /** Release the default model from RAM if supported. */
    suspend fun unloadDefaultModelFromMemory()
}
