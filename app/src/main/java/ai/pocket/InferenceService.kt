package ai.pocket

import ai.pocket.api.PromptBuilder
import ai.pocket.core.InferenceSettings
import ai.pocket.core.LlmDefaults
import ai.pocket.orchestration.OrchestrationRequest
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Bound service that exposes Pocket AI inference to other apps via AIDL.
 * Uses the shared Orchestrator (single model instance).
 *
 * Actions: "summarize", "analyze", "draft"
 */
class InferenceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val orchestrator by lazy {
        (application as PocketAIApplication).orchestrator
    }

    private val binder = object : IInferenceService.Stub() {
        override fun isReady(): Boolean = true

        override fun generateStreaming(text: String, action: String, callback: IInferenceCallback) {
            serviceScope.launch {
                try {
                    val userTask = when (action.lowercase()) {
                        "analyze" ->
                            "List key points, themes, and insights from the text below. Use short bullets.\n\n$text"
                        "draft" ->
                            "Draft a short professional reply to the message below. No subject line.\n\n$text"
                        else ->
                            "Summarize the following text in a few short paragraphs or bullets. Be concise.\n\n$text"
                    }
                    val prompt = PromptBuilder.formatPocketSingleTurn(userTask)
                    orchestrator.generate(
                        request = OrchestrationRequest(
                            prompt = prompt,
                            settings = InferenceSettings(temperature = 0.80f, maxTokens = LlmDefaults.MAX_NEW_TOKENS_CAP),
                            toolId = "inference_service"
                        ),
                        onToken = { token -> callback.onToken(token) }
                    )
                    callback.onComplete()
                } catch (e: Exception) {
                    try {
                        callback.onError(e.message ?: "Unknown error")
                    } catch (_: Exception) { /* callback may be dead */ }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
