package ai.gyango.orchestration

import ai.gyango.core.LoadingPhase
import ai.gyango.core.MemoryManagedLlm
import ai.gyango.core.ModelInfo
import ai.gyango.core.PhasedLlm
import android.util.Log

/**
 * Default orchestrator implementation.
 */
class DefaultOrchestrator(
    private val registry: ModelRegistry,
    private val routingPolicy: RoutingPolicy,
    private val defaultModelId: String? = null,
    private val inferenceTracker: InferenceActivityTracker? = null,
    /** When true, logs full prompt text to logcat (chunked). Off by default — very large. */
    private val enablePromptPayloadLogs: Boolean = false,
) : Orchestrator {

    companion object {
        private const val TAG = "DefaultOrchestrator"
        /** Logcat line limit safety margin. */
        private const val PROMPT_LOG_CHUNK_CHARS = 3500

        /**
         * Plain-text log of the exact prompt passed to the LLM (chunked for logcat).
         * Filter: `FINAL_PROMPT_TEXT`.
         */
        private fun logFinalPromptTextSentToLlm(prompt: String, toolId: String?) {
            val n = prompt.length
            val tool = toolId ?: "default"
            Log.i(TAG, "FINAL_PROMPT_TEXT tool=$tool length=$n")
            if (n == 0) {
                Log.i(TAG, "FINAL_PROMPT_TEXT part=1/1\n<empty>")
                return
            }
            var offset = 0
            var part = 0
            val totalParts = (n + PROMPT_LOG_CHUNK_CHARS - 1) / PROMPT_LOG_CHUNK_CHARS
            while (offset < n) {
                val end = (offset + PROMPT_LOG_CHUNK_CHARS).coerceAtMost(n)
                part++
                Log.i(TAG, "FINAL_PROMPT_TEXT tool=$tool part=$part/$totalParts\n${prompt.substring(offset, end)}")
                offset = end
            }
        }
    }

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
            if (enablePromptPayloadLogs) {
                logFinalPromptTextSentToLlm(genRequest.prompt, request.toolId)
            }

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
