package ai.gyango.models.litert

import ai.gyango.core.GenerateRequest
import ai.gyango.core.LiteRtPathPrepareHook
import ai.gyango.core.LoadingPhase
import ai.gyango.core.LlmDefaults
import ai.gyango.core.MemoryManagedLlm
import ai.gyango.core.PhasedLlm
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device chat via LiteRT-LM using the base model only.
 */
class LiteRtLlm(
    private val applicationContext: Context,
    private val assetRelativePath: String = DEFAULT_MODEL_ASSET,
    override val modelId: String = DEFAULT_MODEL_ID,
    private val absoluteModelPathOverride: String? = null,
    private val absoluteModelPathOverrideProvider: (() -> String?)? = null,
    private val pathPrepareHook: LiteRtPathPrepareHook? = null,
    private val verboseLogs: Boolean = false,
    private val verboseInferenceTiming: Boolean = false,
) : PhasedLlm, MemoryManagedLlm {
    private val mutex = Mutex()
    private val manager = LiteRtEngineManager(applicationContext)
    private val pathProvider = LiteRtModelPathProvider(
        context = applicationContext,
        assetName = assetRelativePath,
        absoluteModelPathOverride = absoluteModelPathOverride,
        absoluteModelPathOverrideProvider = absoluteModelPathOverrideProvider,
        verboseInferenceTiming = verboseInferenceTiming,
    )
    private var lastInitBasePath: String? = null
    private var lastInitLowPower: Boolean? = null

    private suspend fun ensureEngine(
        onPhaseChange: ((LoadingPhase) -> Unit)?,
        lowPowerMode: Boolean,
    ) {
        if (!manager.isInitialized) {
            onPhaseChange?.invoke(LoadingPhase.LOADING_MODEL)
        }
        val ioT0 = SystemClock.elapsedRealtime()
        val basePath = withContext(Dispatchers.IO) {
            pathPrepareHook?.prepare(applicationContext)
            pathProvider.getOrCopyModel()
        }
        if (verboseInferenceTiming) {
            Log.d(TAG, "[perf] ensureEngine io_resolve_path_ms=${SystemClock.elapsedRealtime() - ioT0}")
        }
        val unchanged = manager.isInitialized &&
            lastInitBasePath == basePath &&
            lastInitLowPower == lowPowerMode
        if (unchanged) {
            onPhaseChange?.invoke(LoadingPhase.GENERATING)
            return
        }
        onPhaseChange?.invoke(LoadingPhase.LOADING_MODEL)
        if (manager.isInitialized) {
            manager.close()
            lastInitBasePath = null
            lastInitLowPower = null
        }
        val maxNumTokens = if (lowPowerMode) {
            LlmDefaults.LOW_RAM_CONTEXT_LENGTH_TOKENS
        } else {
            LlmDefaults.CONTEXT_LENGTH_TOKENS
        }
        val initT0 = SystemClock.elapsedRealtime()
        manager.initialize(
            modelPath = basePath,
            cacheDir = applicationContext.filesDir.absolutePath,
            maxNumTokens = maxNumTokens,
            verboseLogs = verboseLogs,
        )
        if (verboseInferenceTiming) {
            Log.d(
                TAG,
                "[perf] ensureEngine initialize_wall_ms=${SystemClock.elapsedRealtime() - initT0} " +
                    "maxNumTokens=$maxNumTokens lowPower=$lowPowerMode",
            )
        }
        lastInitBasePath = basePath
        lastInitLowPower = lowPowerMode
        onPhaseChange?.invoke(LoadingPhase.GENERATING)
    }

    override suspend fun loadIntoMemory() {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                ensureEngine(onPhaseChange = null, lowPowerMode = false)
            }
        }
    }

    override suspend fun unloadFromMemory() {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                manager.close()
                lastInitBasePath = null
                lastInitLowPower = null
            }
        }
    }

    override suspend fun generate(request: GenerateRequest, onToken: (String) -> Unit) {
        generateWithPhase(request, onToken, {})
    }

    override suspend fun generateWithPhase(
        request: GenerateRequest,
        onToken: (String) -> Unit,
        onPhaseChange: (LoadingPhase) -> Unit,
    ) {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                ensureEngine(onPhaseChange = onPhaseChange, lowPowerMode = request.settings.lowPowerMode)
                val topicSampling = LlmDefaults.samplingForSubject(request.settings.subjectMode)
                val temperature = topicSampling.temperature
                    .coerceIn(LlmDefaults.LITERT_MIN_TEMPERATURE, LlmDefaults.LITERT_MAX_TEMPERATURE)
                    .toDouble()
                val topP = if (request.settings.lowPowerMode) {
                    LlmDefaults.LITERT_LOW_POWER_TOP_P
                } else {
                    topicSampling.topP
                        .toDouble()
                        .coerceIn(LlmDefaults.LITERT_MIN_TOP_P.toDouble(), LlmDefaults.LITERT_MAX_TOP_P.toDouble())
                }
                val topK = if (request.settings.lowPowerMode) {
                    LlmDefaults.LITERT_LOW_POWER_TOP_K
                } else {
                    topicSampling.topK.coerceIn(LlmDefaults.LITERT_MIN_TOP_K, LlmDefaults.LITERT_MAX_TOP_K)
                }
                val maxCap = if (request.settings.lowPowerMode) {
                    LlmDefaults.LOW_RAM_MAX_NEW_TOKENS_CAP
                } else {
                    LlmDefaults.MAX_NEW_TOKENS_CAP
                }
                val maxNew = request.settings.maxTokens.coerceIn(64, maxCap)
                val genT0 = SystemClock.elapsedRealtime()
                manager.generateWithFallback(
                    prompt = request.prompt,
                    temperature = temperature,
                    topP = topP,
                    topK = topK,
                    maxNewTokens = maxNew,
                    onToken = onToken,
                )
                if (verboseInferenceTiming) {
                    Log.d(
                        TAG,
                        "[perf] generateWithPhase litert_generate_wall_ms=" +
                            "${SystemClock.elapsedRealtime() - genT0} topK=$topK topP=$topP",
                    )
                }
            }
        }
    }

    companion object {
        const val DEFAULT_MODEL_ASSET = "models/gemma-4-E2B-it.litertlm"
        const val DEFAULT_MODEL_ID = "gyango-litert"
        private const val TAG = "LiteRtLlm"
    }
}
