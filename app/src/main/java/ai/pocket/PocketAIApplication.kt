package ai.pocket

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

    private fun scheduleModelEvictionDelayed() {
        idleHandler.removeCallbacks(evictModelsRunnable)
        idleHandler.postDelayed(evictModelsRunnable, MODEL_IDLE_MS)
    }

    companion object {
        private const val MODEL_IDLE_MS = 15 * 60 * 1000L

        /**
         * LiteRT-LM bundle under `app/src/main/assets/`. Default matches
         * [google/gemma-3n-E2B-it-litert-lm](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)
         * (multimodal: swap in Gemma 4 artifacts when available).
         */
        private const val GEMMA_LITE_ASSET = GemmaLiteRtLlm.DEFAULT_MODEL_ASSET
        private const val GEMMA_MODEL_ID = GemmaLiteRtLlm.DEFAULT_MODEL_ID
    }
}
