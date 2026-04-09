package ai.pocket

import ai.pocket.BuildConfig
import ai.pocket.models.gemma.GemmaLiteRtLlm
import ai.pocket.orchestration.DefaultModelRegistry
import ai.pocket.orchestration.DefaultOrchestrator
import ai.pocket.orchestration.DefaultRoutingPolicy
import ai.pocket.orchestration.InferenceActivityTracker
import ai.pocket.orchestration.Orchestrator
import android.app.Application
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shared on-device LLM [Orchestrator] (Gemma via LiteRT-LM / TFLite). */
class PocketAIApplication : Application(), InferenceActivityTracker {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val idleHandler = Handler(Looper.getMainLooper())
    private val inferenceDepth = AtomicInteger(0)

    private val evictModelsRunnable = Runnable {
        if (inferenceDepth.get() > 0) {
            scheduleModelEvictionDelayed()
            return@Runnable
        }
        appScope.launch {
            runCatching { orchestrator.unloadDefaultModelFromMemory() }
        }
    }

    val orchestrator: Orchestrator by lazy {
        val gemma = GemmaLiteRtLlm(
            applicationContext = this,
            assetRelativePath = GEMMA_LITE_ASSET,
            modelId = GEMMA_MODEL_ID,
            absoluteModelPathOverride = BuildConfig.GEMMA_MODEL_ABSOLUTE_PATH.takeIf { it.isNotBlank() },
        )
        val registry = DefaultModelRegistry()
        registry.register(gemma)
        DefaultOrchestrator(
            registry = registry,
            routingPolicy = DefaultRoutingPolicy(defaultModelId = gemma.modelId),
            defaultModelId = gemma.modelId,
            inferenceTracker = this
        )
    }

    override fun onCreate() {
        super.onCreate()
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

    fun resetModelIdleTimer() {
        if (inferenceDepth.get() == 0) {
            scheduleModelEvictionDelayed()
        }
    }

    /** Unload Gemma and voice models immediately (e.g. activity finishing). */
    fun unloadAllModelsFromMemoryNow() {
        idleHandler.removeCallbacks(evictModelsRunnable)
        appScope.launch {
            runCatching { orchestrator.unloadDefaultModelFromMemory() }
        }
    }

    private fun scheduleModelEvictionDelayed() {
        idleHandler.removeCallbacks(evictModelsRunnable)
        idleHandler.postDelayed(evictModelsRunnable, MODEL_IDLE_MS)
    }

    companion object {
        private const val MODEL_IDLE_MS = 10 * 60 * 1000L

        /** LiteRT-LM bundle: `app/src/main/assets/models/gemma-4-E2B-it.litertlm` (see [GemmaLiteRtLlm]). */
        private const val GEMMA_LITE_ASSET = GemmaLiteRtLlm.DEFAULT_MODEL_ASSET
        private const val GEMMA_MODEL_ID = GemmaLiteRtLlm.DEFAULT_MODEL_ID
    }
}
