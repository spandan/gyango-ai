package ai.pocket.models.gemma

import ai.pocket.core.GenerateRequest
import ai.pocket.core.LoadingPhase
import ai.pocket.core.MemoryManagedLlm
import ai.pocket.core.PhasedLlm
import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device **Gemma** via **LiteRT-LM** (TFLite/LiteRT stack). Targets Gemma 3n `.litertlm` bundles;
 * Gemma 4 bundles should work the same way when published—swap in [assetRelativePath].
 *
 * **Audio:** Gemma 3n supports multimodal input through LiteRT (`Content.AudioBytes`, etc.).
 * This class exposes **text** chat only; extend [generateWithPhase] to attach WAV bytes when you
 * wire the UI.
 */
class GemmaLiteRtLlm(
    private val applicationContext: Context,
    private val assetRelativePath: String = DEFAULT_MODEL_ASSET,
    override val modelId: String = DEFAULT_MODEL_ID,
) : PhasedLlm, MemoryManagedLlm {

    private val mutex = Mutex()
    private var engine: Engine? = null
    private val pathProvider = LiteRtModelPathProvider(applicationContext, assetRelativePath)

    private suspend fun ensureEngine(onPhaseChange: ((LoadingPhase) -> Unit)?) {
        if (engine != null) return
        onPhaseChange?.invoke(LoadingPhase.LOADING_MODEL)
        val path = withContext(Dispatchers.IO) { pathProvider.getOrCopyModel() }
        val config = EngineConfig(
            modelPath = path,
            backend = Backend.CPU(),
            cacheDir = applicationContext.cacheDir.absolutePath,
        )
        engine = Engine(config).also { it.initialize() }
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
                engine?.close()
                engine = null
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
                val eng = engine ?: error("LiteRT engine not initialized")
                onPhaseChange(LoadingPhase.GENERATING)
                val sampler = SamplerConfig(
                    temperature = request.settings.temperature.toDouble(),
                    topP = 0.95,
                    topK = 64,
                )
                val conversation = eng.createConversation(ConversationConfig(samplerConfig = sampler))
                conversation.use { conv ->
                    var accumulated = ""
                    conv.sendMessageAsync(request.prompt)
                        .catch { e -> throw e }
                        .collect { message: Message ->
                            val full = messageText(message)
                            when {
                                full.startsWith(accumulated) && full.length > accumulated.length -> {
                                    val delta = full.substring(accumulated.length)
                                    if (delta.isNotEmpty()) onToken(delta)
                                    accumulated = full
                                }
                                full.isNotEmpty() && full != accumulated -> {
                                    onToken(full)
                                    accumulated = full
                                }
                            }
                        }
                }
            }
        }
    }

    private fun messageText(message: Message): String =
        try {
            message.text.orEmpty()
        } catch (_: Throwable) {
            message.toString()
        }

    companion object {
        /** Default asset path—replace filename after downloading the bundle from Hugging Face. */
        const val DEFAULT_MODEL_ASSET = "models/gemma-3n-e2b-it.litertlm"

        const val DEFAULT_MODEL_ID = "gemma-3n-e2b-litertlm"
    }
}
