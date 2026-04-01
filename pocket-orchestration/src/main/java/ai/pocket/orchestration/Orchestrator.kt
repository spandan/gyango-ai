package ai.pocket.orchestration

import ai.pocket.core.LoadingPhase
import ai.pocket.core.ModelInfo

/**
 * Single entry point for all tools (ChatBot, Dashboard, InferenceService).
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
