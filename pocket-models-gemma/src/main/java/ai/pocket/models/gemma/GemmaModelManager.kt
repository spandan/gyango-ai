package ai.pocket.models.gemma

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Loads a `.litertlm` bundle with **NPU → GPU → CPU** fallback ([Engine.initialize] is tried per backend).
 *
 * Sample docs mention [com.google.ai.edge.litertlm.LlmEngine], `useMemoryMapping`, and
 * `isBackendSupported`; the **litertlm-android** Maven artifact exposes [Engine] and this
 * shape only—those calls map here to [EngineConfig] + sequential backend attempts.
 *
 * @param maxNumTokens Optional KV budget (sum of input + output tokens, see [EngineConfig.maxNumTokens]);
 *   `null` lets the model/engine pick defaults (best for unknown context sizes).
 */
class GemmaModelManager(private val context: Context) {

    private var engine: Engine? = null

    val isInitialized: Boolean get() = engine != null

    suspend fun initialize(
        modelPath: String,
        cacheDir: String = context.cacheDir.absolutePath,
        maxNumTokens: Int? = null,
    ) {
        if (engine != null) return
        withContext(Dispatchers.Default) {
            engine = createEngineWithBackendFallback(modelPath, cacheDir, maxNumTokens)
        }
    }

    private fun createEngineWithBackendFallback(
        modelPath: String,
        cacheDir: String,
        maxNumTokens: Int?,
    ): Engine {
        val npuLibDir = context.applicationInfo.nativeLibraryDir
        val attempts = listOf(
            Backend.NPU(nativeLibraryDir = npuLibDir) to "NPU",
            Backend.GPU() to "GPU",
            Backend.CPU() to "CPU",
        )
        var last: Throwable? = null
        for ((backend, label) in attempts) {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                cacheDir = cacheDir,
                maxNumTokens = maxNumTokens,
            )
            val outcome = runCatching {
                val eng = Engine(config)
                try {
                    eng.initialize()
                    Log.i(TAG, "LiteRT engine using $label backend")
                    eng
                } catch (t: Throwable) {
                    runCatching { eng.close() }
                    throw t
                }
            }
            outcome.onSuccess { return it }
            val err = outcome.exceptionOrNull()
            last = err
            Log.w(TAG, "$label backend unavailable: ${err?.message}")
        }
        throw last ?: IllegalStateException("No LiteRT backend available")
    }

    /**
     * Streams assistant text chunks (suitable for UI on slower hardware).
     */
    fun chatStream(
        prompt: String,
        temperature: Double = 0.7,
        topP: Double = 0.95,
        topK: Int = 64,
    ): Flow<String> {
        val eng = engine ?: throw IllegalStateException("Engine not initialized")
        return flow {
            val sampler = SamplerConfig(
                topK = topK,
                topP = topP,
                temperature = temperature,
            )
            eng.createConversation(ConversationConfig(samplerConfig = sampler)).use { conv ->
                var accumulated = ""
                conv.sendMessageAsync(prompt)
                    .catch { throw it }
                    .collect { message: Message ->
                        val full = message.toString()
                        when {
                            full.startsWith(accumulated) && full.length > accumulated.length -> {
                                val delta = full.substring(accumulated.length)
                                if (delta.isNotEmpty()) emit(delta)
                                accumulated = full
                            }
                            full.isNotEmpty() && full != accumulated -> {
                                emit(full)
                                accumulated = full
                            }
                        }
                    }
            }
        }.flowOn(Dispatchers.Default)
    }

    fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        private const val TAG = "GemmaModelManager"
    }
}
