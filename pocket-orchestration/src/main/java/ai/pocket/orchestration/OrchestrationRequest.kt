package ai.pocket.orchestration

import ai.pocket.core.GenerateRequest
import ai.pocket.core.InferenceSettings

/**
 * Request to the orchestrator. Tools use this instead of calling models directly.
 */
data class OrchestrationRequest(
    val prompt: String,
    val settings: InferenceSettings,
    val preferredModel: String? = null,
    val toolId: String? = null
) {
    fun toGenerateRequest(): GenerateRequest = GenerateRequest(
        prompt = prompt,
        settings = settings
    )
}
