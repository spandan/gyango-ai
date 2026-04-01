package ai.pocket.orchestration

import ai.pocket.core.LoadingPhase
import ai.pocket.core.MemoryManagedLlm
import ai.pocket.core.ModelInfo
import ai.pocket.core.PhasedLlm

/**
 * Default orchestrator implementation.
 */
class DefaultOrchestrator(
    private val registry: ModelRegistry,
    private val routingPolicy: RoutingPolicy,
    private val defaultModelId: String? = null,
    private val inferenceTracker: InferenceActivityTracker? = null
) : Orchestrator {

    override suspend fun warmUpDefaultModel() {
        val id = defaultModelId ?: return
        val model = registry.get(id) as? MemoryManagedLlm ?: return
        model.loadIntoMemory()
    }

    override suspend fun unloadDefaultModelFromMemory() {
        val id = defaultModelId ?: return
        val model = registry.get(id) as? MemoryManagedLlm ?: return
        model.unloadFromMemory()
    }

    override suspend fun generate(
        request: OrchestrationRequest,
        onToken: (String) -> Unit,
        onPhaseChange: ((LoadingPhase) -> Unit)?
    ) {
        inferenceTracker?.beginInference()
        try {
            val models = registry.listAll()
            val model = routingPolicy.selectModel(request, models)
                ?: throw IllegalStateException("No model available for inference")
            val genRequest = request.toGenerateRequest()

            if (model is PhasedLlm && onPhaseChange != null) {
                model.generateWithPhase(
                    request = genRequest,
                    onToken = onToken,
                    onPhaseChange = onPhaseChange
                )
            } else {
                model.generate(genRequest, onToken)
            }
        } finally {
            inferenceTracker?.endInference()
        }
    }

    override fun getAvailableModels(): List<ModelInfo> {
        return registry.listAll().mapNotNull { registry.getModelInfo(it.modelId) }
    }

    override fun getDefaultModelId(): String? = defaultModelId
}
