package ai.pocket

import ai.pocket.data.ModelPathProvider
import ai.pocket.audio.SharedWhisperEngine
import ai.pocket.models.llm.RwkvLlm
import ai.pocket.orchestration.DefaultModelRegistry
import ai.pocket.orchestration.DefaultOrchestrator
import ai.pocket.orchestration.DefaultRoutingPolicy
import ai.pocket.orchestration.InferenceActivityTracker
import ai.pocket.orchestration.Orchestrator
import ai.pocket.core.LlmDefaults
import android.app.Application
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shared on-device LLM [Orchestrator] (RWKV GGUF) and [SharedWhisperEngine]; both unload after prolonged idle. */
class PocketAIApplication : Application(), InferenceActivityTracker {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val idleHandler = Handler(Looper.getMainLooper())
    private val inferenceDepth = AtomicInteger(0)

    val sharedWhisperEngine: SharedWhisperEngine by lazy { SharedWhisperEngine(this) }

    private val evictModelsRunnable = Runnable {
        if (inferenceDepth.get() > 0) {
            scheduleModelEvictionDelayed()
            return@Runnable
        }
        appScope.launch {
            runCatching { orchestrator.unloadDefaultModelFromMemory() }
            runCatching { sharedWhisperEngine.releaseFromMemory() }
        }
    }

    val orchestrator: Orchestrator by lazy {
        val modelPath = ModelPathProvider(this).getOrCopyModel()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val threads = minOf(cores, LlmDefaults.LLM_INFERENCE_THREADS_CAP)
        val rwkv = RwkvLlm(modelPath = modelPath, nThreads = threads)
        val registry = DefaultModelRegistry()
        registry.register(rwkv)
        DefaultOrchestrator(
            registry = registry,
            routingPolicy = DefaultRoutingPolicy(defaultModelId = rwkv.modelId),
            defaultModelId = rwkv.modelId,
            inferenceTracker = this
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Lazy-load LLM on first chat and Whisper on first audio screen — avoids ~multi‑GB RSS at cold start.
        appScope.launch { scheduleModelEvictionDelayed() }
    }

    override fun beginInference() {
        inferenceDepth.incrementAndGet()
        idleHandler.removeCallbacks(evictModelsRunnable)
    }

    override fun endInference() {
        val d = inferenceDepth.decrementAndGet()
        if (d < 0) {
            inferenceDepth.set(0)
        }
        if (inferenceDepth.get() == 0) {
            scheduleModelEvictionDelayed()
        }
    }

    /** Call when the user is active in the app (e.g. [Activity.onResume]) to restart the idle clock. */
    fun resetModelIdleTimer() {
        if (inferenceDepth.get() == 0) {
            scheduleModelEvictionDelayed()
        }
    }

    private fun scheduleModelEvictionDelayed() {
        idleHandler.removeCallbacks(evictModelsRunnable)
        idleHandler.postDelayed(evictModelsRunnable, MODEL_IDLE_MS)
    }

    companion object {
        private const val MODEL_IDLE_MS = 15 * 60 * 1000L
    }
}
