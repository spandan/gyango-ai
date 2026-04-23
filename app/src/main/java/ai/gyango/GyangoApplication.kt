package ai.gyango

import ai.gyango.BuildConfig
import ai.gyango.core.LiteRtPathPrepareHook
import ai.gyango.modeldelivery.GyangoPadDelivery
import ai.gyango.models.litert.LiteRtLlm
import ai.gyango.orchestration.DefaultModelRegistry
import ai.gyango.orchestration.DefaultOrchestrator
import ai.gyango.orchestration.DefaultRoutingPolicy
import ai.gyango.orchestration.InferenceActivityTracker
import ai.gyango.orchestration.Orchestrator
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shared on-device LLM [Orchestrator] (LiteRT-LM base model only). */
class GyangoApplication : Application(), InferenceActivityTracker {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val idleHandler = Handler(Looper.getMainLooper())
    private val inferenceDepth = AtomicInteger(0)
    @Volatile private var appOutOfFocus: Boolean = false
    private val evictModelsRunnable = Runnable {
        if (!appOutOfFocus) return@Runnable
        if (inferenceDepth.get() > 0) {
            scheduleModelEvictionDelayed()
            return@Runnable
        }
        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
            Log.i(TAG, "Idle background timeout reached: unloading base model")
        }
        appScope.launch {
            runCatching { orchestrator.unloadDefaultModelFromMemory() }
        }
    }

    val orchestrator: Orchestrator by lazy {
        val llm = LiteRtLlm(
            applicationContext = this,
            assetRelativePath = LITERT_MODEL_ASSET,
            modelId = LITERT_MODEL_ID,
            absoluteModelPathOverrideProvider = ::resolveModelOverridePath,
            pathPrepareHook = if (BuildConfig.USE_PAD) {
                LiteRtPathPrepareHook { ctx -> GyangoPadDelivery.ensureChunksFetchedAndMerged(ctx) }
            } else {
                null
            },
            verboseLogs = BuildConfig.VERBOSE_DEBUG_LOGS,
            verboseInferenceTiming = BuildConfig.VERBOSE_DEBUG_LOGS,
        )
        val registry = DefaultModelRegistry()
        registry.register(llm)
        DefaultOrchestrator(
            registry = registry,
            routingPolicy = DefaultRoutingPolicy(defaultModelId = llm.modelId),
            defaultModelId = llm.modelId,
            inferenceTracker = this,
            enablePromptPayloadLogs = BuildConfig.LLM_IO_LOGS,
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
            Log.i(TAG, "LiteRT base model active")
        }
    }

    override fun beginInference() {
        inferenceDepth.incrementAndGet()
        idleHandler.removeCallbacks(evictModelsRunnable)
        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
            Log.d(TAG, "beginInference depth=${inferenceDepth.get()}")
        }
    }

    override fun endInference() {
        val d = inferenceDepth.decrementAndGet()
        if (d < 0) {
            inferenceDepth.set(0)
        }
        if (EVICT_MODEL_AFTER_BACKGROUND_OR_MEMORY_TRIM && appOutOfFocus && inferenceDepth.get() == 0) {
            scheduleModelEvictionDelayed()
        }
        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
            Log.d(TAG, "endInference depth=${inferenceDepth.get()}")
        }
    }

    /** Host activity is foregrounded again: keep base resident and cancel background idle-unload timer. */
    fun resetModelIdleTimer() {
        appOutOfFocus = false
        idleHandler.removeCallbacks(evictModelsRunnable)
        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
            Log.d(TAG, "App active: canceled idle unload timer")
        }
    }

    /** Explicit manual unload hook (not used by default flow). */
    fun unloadAllModelsFromMemoryNow() {
        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
            Log.w(TAG, "Manual model unload requested")
        }
        idleHandler.removeCallbacks(evictModelsRunnable)
        appScope.launch {
            runCatching { orchestrator.unloadDefaultModelFromMemory() }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            // App is backgrounded/out of focus: optionally schedule idle unload (disabled by default on Android
            // — teardown/reload of large mmap+native heaps can worsen allocation behavior under memory pressure).
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                appOutOfFocus = true
                if (EVICT_MODEL_AFTER_BACKGROUND_OR_MEMORY_TRIM && inferenceDepth.get() == 0) {
                    scheduleModelEvictionDelayed()
                }
                if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                    Log.i(
                        TAG,
                        "onTrimMemory(level=$level): app out of focus, idleEvict=" +
                            EVICT_MODEL_AFTER_BACKGROUND_OR_MEMORY_TRIM,
                    )
                }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                if (EVICT_MODEL_AFTER_BACKGROUND_OR_MEMORY_TRIM) {
                    Log.w(TAG, "onTrimMemory(level=$level): memory pressure")
                }
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (EVICT_MODEL_AFTER_BACKGROUND_OR_MEMORY_TRIM) {
            Log.w(TAG, "onLowMemory")
        }
    }

    companion object {
        private const val TAG = "GyangoApp"
        private const val MODEL_IDLE_MS = 10 * 60 * 1000L

        /**
         * When false (default), we never unload the LiteRT session from background idle or from
         * [onTrimMemory]/[onLowMemory]. Proactive unload can interact badly with large native allocations
         * on some devices; [unloadAllModelsFromMemoryNow] remains available for explicit unload.
         */
        private const val EVICT_MODEL_AFTER_BACKGROUND_OR_MEMORY_TRIM = false

        /** LiteRT: `app/src/main/assets/models/gemma-4-E2B-it.litertlm` (see [LiteRtLlm]). */
        private const val LITERT_MODEL_ASSET = LiteRtLlm.DEFAULT_MODEL_ASSET
        private const val LITERT_MODEL_ID = LiteRtLlm.DEFAULT_MODEL_ID
    }

    private fun resolveModelOverridePath(): String? {
        val gradleOverride = BuildConfig.LITERT_MODEL_ABSOLUTE_PATH.takeIf { it.isNotBlank() }
        if (!gradleOverride.isNullOrBlank()) return gradleOverride
        if (BuildConfig.USE_PAD) {
            return GyangoPadDelivery.mergedModelAbsolutePath(this)
        }
        return null
    }

    private fun scheduleModelEvictionDelayed() {
        if (!EVICT_MODEL_AFTER_BACKGROUND_OR_MEMORY_TRIM) return
        idleHandler.removeCallbacks(evictModelsRunnable)
        idleHandler.postDelayed(evictModelsRunnable, MODEL_IDLE_MS)
    }

}
