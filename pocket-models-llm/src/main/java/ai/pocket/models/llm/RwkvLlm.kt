package ai.pocket.models.llm

import ai.pocket.core.GenerateRequest
import ai.pocket.core.InferenceSettings
import ai.pocket.core.LoadingPhase
import ai.pocket.core.LlmDefaults
import ai.pocket.core.MemoryManagedLlm
import ai.pocket.core.PhasedLlm
import ai.pocket.native.NativeLlmBridge
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device **RWKV-7 G1e** GGUF via llama.cpp (e.g. Q4_K_M); prompts should be short and use `PromptBuilder`.
 */
class RwkvLlm(
    private val modelPath: String,
    nThreads: Int = Runtime.getRuntime().availableProcessors(),
    private val contextLength: Int = LlmDefaults.CONTEXT_LENGTH_TOKENS
) : PhasedLlm, MemoryManagedLlm {

    /** Capped so we never spin up “all cores” during long generations (thermal / battery). */
    private val nThreads = max(1, min(nThreads, LlmDefaults.LLM_INFERENCE_THREADS_CAP))

    override val modelId: String = "rwkv7-g1e-1.5b-q4_k_m"

    private val mutex = Mutex()
    private var initialized = false

    /**
     * Always use the capped thread count for llama.cpp. [InferenceSettings.lowPowerMode] is kept for
     * UI/settings compatibility but no longer halves threads or forces a reload — that path was prone
     * to flaky re-inits and single-thread edge cases on some devices.
     */
    private suspend fun ensureInitialized(onPhaseChange: ((LoadingPhase) -> Unit)?) {
        if (!initialized) {
            onPhaseChange?.invoke(LoadingPhase.LOADING_MODEL)
            val ok = NativeLlmBridge.init(modelPath, nThreads, contextLength)
            if (!ok) {
                throw IllegalStateException("Failed to initialize native LLM runtime")
            }
            initialized = true
        }
        onPhaseChange?.invoke(LoadingPhase.GENERATING)
    }

    override suspend fun loadIntoMemory() {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                ensureInitialized(null)
            }
        }
    }

    override suspend fun unloadFromMemory() {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                if (initialized) {
                    NativeLlmBridge.release()
                    initialized = false
                }
            }
        }
    }

    override suspend fun generate(request: GenerateRequest, onToken: (String) -> Unit) {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                ensureInitialized(null)
                streamWithCallback(request, onToken)
            }
        }
    }

    override suspend fun generateWithPhase(
        request: GenerateRequest,
        onToken: (String) -> Unit,
        onPhaseChange: (LoadingPhase) -> Unit
    ) {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                ensureInitialized(onPhaseChange)
                streamWithCallback(request, onToken)
            }
        }
    }

    private fun streamWithCallback(request: GenerateRequest, onTokenLambda: (String) -> Unit) {
        NativeLlmBridge.generateStreaming(
            prompt = request.prompt,
            temperature = request.settings.temperature,
            topP = 0.95f,
            maxTokens = request.settings.maxTokens,
            callback = { token ->
                if (token.isNotEmpty()) {
                    onTokenLambda(token)
                }
            }
        )
    }
}
