package ai.pocket.models.gemma

import ai.pocket.core.GenerateRequest
import ai.pocket.core.LoadingPhase
import ai.pocket.core.LlmDefaults
import ai.pocket.core.MemoryManagedLlm
import ai.pocket.core.PhasedLlm
import android.content.Context
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device **Gemma** via **LiteRT-LM** (TFLite/LiteRT stack).
 * Delegates engine lifecycle and streaming to [GemmaModelManager] (**NPU → GPU → CPU**).
 * Requires optional OpenCL / NPU native libs in the app manifest when using those backends.
 *
 * **Audio:** multimodal models can use `Content.AudioBytes` in LiteRT; this class is text-only for now.
 *
 * **Settings:** [InferenceSettings.temperature] is passed into each [GemmaModelManager.chatStream].
 * [InferenceSettings.maxTokens] is not a per-message cap in the published API; [EngineConfig.maxNumTokens]
 * is set once at init to [LlmDefaults.CONTEXT_LENGTH_TOKENS] as KV budget (input+output sum).
 */
class GemmaLiteRtLlm(
    private val applicationContext: Context,
    private val assetRelativePath: String = DEFAULT_MODEL_ASSET,
    override val modelId: String = DEFAULT_MODEL_ID,
    /**
     * Optional absolute path to a `.litertlm` file; when set, assets are not read and no duplicate
     * copy is made from the APK (see [LiteRtModelPathProvider]).
     */
    private val absoluteModelPathOverride: String? = null,
) : PhasedLlm, MemoryManagedLlm {

    private val mutex = Mutex()
    private val manager = GemmaModelManager(applicationContext)
    private val pathProvider = LiteRtModelPathProvider(
        applicationContext,
        assetRelativePath,
        absoluteModelPathOverride,
    )

    private suspend fun ensureEngine(onPhaseChange: ((LoadingPhase) -> Unit)?) {
        if (manager.isInitialized) return
        onPhaseChange?.invoke(LoadingPhase.LOADING_MODEL)
        val path = withContext(Dispatchers.IO) { pathProvider.getOrCopyModel() }
        val cacheDir = applicationContext.cacheDir.absolutePath
        manager.initialize(
            modelPath = path,
            cacheDir = cacheDir,
            maxNumTokens = LlmDefaults.CONTEXT_LENGTH_TOKENS,
        )
        onPhaseChange?.invoke(LoadingPhase.GENERATING)
    }

    override suspend fun loadIntoMemory() {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                ensureEngine(null)
            }
        }
    }

    override suspend fun unloadFromMemory() {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                manager.close()
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
                ensureEngine(onPhaseChange)
                onPhaseChange(LoadingPhase.GENERATING)
                manager
                    .chatStream(
                        prompt = request.prompt,
                        temperature = request.settings.temperature.toDouble(),
                    )
                    .catch { e -> throw e }
                    .collect { chunk -> onToken(chunk) }
            }
        }
    }

    companion object {
        /** Asset path relative to `app/src/main/assets/` (copy `gemma-4-E2B-it.litertlm` into `models/`). */
        const val DEFAULT_MODEL_ASSET = "models/gemma-4-E2B-it.litertlm"

        const val DEFAULT_MODEL_ID = "gemma-4-e2b-litertlm"
    }
}
