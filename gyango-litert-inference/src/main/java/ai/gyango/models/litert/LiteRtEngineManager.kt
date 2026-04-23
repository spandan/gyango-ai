package ai.gyango.models.litert

import ai.gyango.assistant.GyangoOutputEnvelope
import ai.gyango.core.hardware.ModelHardwareGate
import ai.gyango.litert.BuildConfig
import android.content.Context
import android.os.Build
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
import java.io.File

/**
 * Manages LiteRT engine lifecycle with prioritized backend selection (NPU -> GPU with
 * context-size fallbacks for small WebGPU buffer limits, then CPU).
 *
 * **Emulator-only mitigations** (skip WebGPU on AVD, external cache workspace, etc.) run only when
 * [BuildConfig.ENABLE_EMULATOR_LITERT_WORKAROUNDS] is true **and** the runtime image looks like an
 * emulator. That flag defaults to false so Play / phone builds behave like normal devices. Build with
 * `gradle/emulator.properties` via
 * `-Pgyango.buildTarget=emulator` or `GYANGO_BUILD_TARGET=emulator` (see root `build.gradle.kts`).
 *
 * XNNPack's on-disk weight cache is disabled for all backends due to native aborts observed on some
 * environments when appending to `*.xnnpack_cache` fails.
 *
 * **Streaming:** [Conversation.sendMessageAsync] returns a cold [Flow] of partial [Message] updates
 * (same idea as async decode / `generateResponseAsync` in other LiteRT APIs) — we never block on a
 * full completion before emitting text deltas.
 */
class LiteRtEngineManager(private val context: Context) {
    private var engine: Engine? = null
    private var turboQuantMode = TurboQuantMode.ON
    private var turboQuantFallbackUsed = false
    private var lastInitParams: EngineInitParams? = null

    val isInitialized: Boolean get() = engine != null

    suspend fun initialize(
        modelPath: String,
        cacheDir: String = context.cacheDir.absolutePath,
        maxNumTokens: Int,
        verboseLogs: Boolean,
    ) {
        if (engine != null) return
        withContext(Dispatchers.Default) {
            val effectiveCacheDir = effectiveLiteRtCacheDirectory(
                appProvidedCacheDir = cacheDir,
                verboseLogs = verboseLogs,
            )
            if (verboseLogs) {
                Log.i(
                    TAG,
                    "LiteRT disk cache workspace=$effectiveCacheDir",
                )
            }
            engine = runCatching {
                createEngineWithConfiguredTurboQuant(
                    modelPath = modelPath,
                    cacheDir = effectiveCacheDir,
                    maxNumTokens = maxNumTokens,
                    verboseLogs = verboseLogs,
                )
            }.recoverCatching { initError ->
                if (!canFallbackTurboQuant()) throw initError
                Log.w(
                    TAG,
                    "LiteRT init failed with TurboQuant ON; retrying once with TurboQuant OFF: ${initError.message}",
                )
                switchTurboQuantMode(TurboQuantMode.OFF, verboseLogs = verboseLogs)
                createEngineWithConfiguredTurboQuant(
                    modelPath = modelPath,
                    cacheDir = effectiveCacheDir,
                    maxNumTokens = maxNumTokens,
                    verboseLogs = verboseLogs,
                )
            }.getOrThrow()
            lastInitParams = EngineInitParams(
                modelPath = modelPath,
                cacheDir = effectiveCacheDir,
                maxNumTokens = maxNumTokens,
                verboseLogs = verboseLogs,
            )
        }
    }

    private fun createEngineWithConfiguredTurboQuant(
        modelPath: String,
        cacheDir: String,
        maxNumTokens: Int,
        verboseLogs: Boolean,
    ): Engine {
        applyTurboQuantForKvCache(
            enable = (turboQuantMode == TurboQuantMode.ON),
            verboseLogs = verboseLogs,
        )
        return createEngineWithBackendPriority(
            modelPath = modelPath,
            cacheDir = cacheDir,
            maxNumTokens = maxNumTokens,
            verboseLogs = verboseLogs,
        )
    }

    /**
     * Resolves a stable subdirectory for LiteRT / XNNPack disk caches and removes broken or legacy
     * `*.xnnpack_cache` files that previously lived directly under [appProvidedCacheDir].
     */
    private fun effectiveLiteRtCacheDirectory(
        appProvidedCacheDir: String,
        verboseLogs: Boolean,
    ): String {
        val internalCacheRoot = File(appProvidedCacheDir)
        deleteXnnpackCacheFiles(internalCacheRoot)

        val workspaceParent = if (useEmulatorLitertWorkarounds()) {
            context.externalCacheDir ?: internalCacheRoot
        } else {
            internalCacheRoot
        }
        if (workspaceParent != internalCacheRoot) {
            deleteXnnpackCacheFiles(workspaceParent)
        }

        val workspace = File(workspaceParent, "litert_lm_workspace").apply { mkdirs() }
        if (useEmulatorLitertWorkarounds()) {
            deleteXnnpackCacheFiles(workspace)
            if (verboseLogs) {
                Log.i(TAG, "Emulator LiteRT workarounds: cleared *.xnnpack_cache under workspace before init")
            }
        }
        return workspace.absolutePath
    }

    private fun deleteXnnpackCacheFiles(directory: File) {
        val listed = directory.listFiles() ?: return
        for (f in listed) {
            if (f.isFile && f.name.endsWith(".xnnpack_cache")) {
                runCatching { f.delete() }
            }
        }
    }

    /**
     * LiteRT/TFLite places the XNNPack on-disk weight cache next to the model file, not under
     * [EngineConfig.cacheDir]. Remove a stale or partial cache before [Engine.initialize].
     */
    private fun deleteXnnpackCacheBesideModel(modelPath: String) {
        File(modelPath).parentFile?.let { deleteXnnpackCacheFiles(it) }
    }

    private fun createEngineWithBackendPriority(
        modelPath: String,
        cacheDir: String,
        maxNumTokens: Int,
        verboseLogs: Boolean,
    ): Engine {
        if (!ModelHardwareGate.inspect(context).canRunSelectedModel) {
            throw IllegalStateException(ModelHardwareGate.UNSUPPORTED_DEVICE_MESSAGE)
        }
        val npuLibDir = context.applicationInfo.nativeLibraryDir
        val omitDiskCacheDir = shouldOmitDiskCacheDirForStability(verboseLogs)
        var last: Throwable? = null

        if (useEmulatorLitertWorkarounds()) {
            tryCreateEngine(
                modelPath = modelPath,
                cacheDir = cacheDir,
                backend = Backend.CPU(numOfThreads = 1),
                label = "CPU",
                maxNumTokens = maxNumTokens,
                verboseLogs = verboseLogs,
                omitDiskCacheDir = omitDiskCacheDir,
            ).fold(
                onSuccess = {
                    Log.w(
                        TAG,
                        "Using CPU backend (single-thread) with emulator LiteRT workarounds " +
                            "(NPU/GPU probe skipped; avoids missing dispatch libs and cache races).",
                    )
                    return it
                },
                onFailure = { last = it },
            )
            throw IllegalStateException(
                "LiteRT could not initialize CPU backend with emulator workarounds enabled.",
                last,
            )
        }

        if (hasBundledDispatchLibrary(npuLibDir)) {
            tryCreateEngine(
                modelPath = modelPath,
                cacheDir = cacheDir,
                backend = Backend.NPU(nativeLibraryDir = npuLibDir),
                label = "NPU",
                maxNumTokens = maxNumTokens,
                verboseLogs = verboseLogs,
                omitDiskCacheDir = omitDiskCacheDir,
            ).fold(
                onSuccess = { return it },
                onFailure = { last = it },
            )
        } else {
            Log.w(
                TAG,
                "Skipping NPU probe: no dispatch .so found under $npuLibDir (avoids native dispatch spam); trying GPU/CPU.",
            )
        }

        val gpuTokenAttempts = buildList {
            add(maxNumTokens)
            for (t in listOf(3072, 2048, 1536, 1024, 768, 512, 384, 256)) {
                if (t < maxNumTokens && !contains(t)) add(t)
            }
        }.let { attempts ->
            // With emulator workarounds: probe smaller GPU contexts first (WebGPU SIGSEGV risk).
            if (useEmulatorLitertWorkarounds()) attempts.sorted() else attempts
        }
        for (tokens in gpuTokenAttempts) {
            tryCreateEngine(
                modelPath = modelPath,
                cacheDir = cacheDir,
                backend = Backend.GPU(),
                label = "GPU",
                maxNumTokens = tokens,
                verboseLogs = verboseLogs,
                omitDiskCacheDir = omitDiskCacheDir,
            ).fold(
                onSuccess = { eng ->
                    if (tokens < maxNumTokens) {
                        Log.w(
                            TAG,
                            "GPU backend active with reduced maxNumTokens=$tokens " +
                                "(requested $maxNumTokens) due to prior init failure; " +
                                "likely WebGPU max buffer limit on this device.",
                        )
                    }
                    return eng
                },
                onFailure = { last = it },
            )
        }

        tryCreateEngine(
            modelPath = modelPath,
            cacheDir = cacheDir,
            // Pin XNNPack thread count on CPU fallback: unbounded defaults can oversubscribe small
            // cores and add context-switching latency during prefill/decode on budget phones.
            backend = Backend.CPU(numOfThreads = 4),
            label = "CPU",
            maxNumTokens = maxNumTokens,
            verboseLogs = verboseLogs,
            omitDiskCacheDir = omitDiskCacheDir,
        ).fold(
            onSuccess = {
                Log.w(
                    TAG,
                    "Using CPU backend after NPU/GPU init failures (slower but avoids a dead GPU path).",
                )
                return it
            },
            onFailure = { last = it },
        )

        throw IllegalStateException(
            "LiteRT could not use an NPU, GPU, or CPU backend.",
            last,
        )
    }

    private fun tryCreateEngine(
        modelPath: String,
        cacheDir: String,
        backend: Backend,
        label: String,
        maxNumTokens: Int,
        verboseLogs: Boolean,
        /**
         * When true, [EngineConfig] is built without `cacheDir` so native LiteRT/TFLite does not
         * enable XNNPack’s file-backed weight cache (avoids SIGABRT on emulator when appends fail).
         */
        omitDiskCacheDir: Boolean = false,
    ): Result<Engine> {
        val config = if (omitDiskCacheDir) {
            EngineConfig(
                modelPath = modelPath,
                backend = backend,
                maxNumTokens = maxNumTokens,
            )
        } else {
            EngineConfig(
                modelPath = modelPath,
                backend = backend,
                cacheDir = cacheDir,
                maxNumTokens = maxNumTokens,
            )
        }
        val outcome = runCatching {
            if (!omitDiskCacheDir) {
                deleteXnnpackCacheBesideModel(modelPath)
            }
            val eng = Engine(config)
            try {
                eng.initialize()
                if (!eng.isInitialized()) {
                    throw IllegalStateException("initialize() returned without isInitialized")
                }
                Log.i(TAG, "LiteRT engine using $label backend (maxNumTokens=$maxNumTokens)")
                eng
            } catch (t: Throwable) {
                runCatching { eng.close() }
                throw t
            }
        }
        outcome.exceptionOrNull()?.let { err ->
            Log.w(TAG, "$label backend unavailable (maxNumTokens=$maxNumTokens): ${err.message}")
        }
        return outcome
    }

    private fun applyTurboQuantForKvCache(enable: Boolean, verboseLogs: Boolean) {
        val flagsClass = runCatching {
            Class.forName("com.google.ai.edge.litertlm.ExperimentalFlags")
        }.getOrElse { e ->
            Log.w(
                TAG,
                "ExperimentalFlags not found; skipping TurboQuant KV tuning (${e.message})",
            )
            return
        }

        val instance = runCatching { flagsClass.getField("INSTANCE").get(null) }.getOrNull()
            ?: runCatching { flagsClass.getMethod("getINSTANCE").invoke(null) }.getOrNull()
        if (instance == null) {
            Log.w(TAG, "ExperimentalFlags.INSTANCE unavailable; skipping TurboQuant KV tuning")
            return
        }

        val booleanFlagMethods = listOf(
            "setEnableTurboQuant",
            "setEnableKvCacheTurboQuant",
            "setEnableKvCacheQuantization",
            "setEnableTurboQuantKvCache",
        )
        var appliedAny = false
        for (methodName in booleanFlagMethods) {
            val applied = runCatching {
                val method = flagsClass.getMethod(methodName, Boolean::class.javaPrimitiveType)
                method.invoke(instance, enable)
                true
            }.getOrDefault(false)
            if (applied) appliedAny = true
        }

        val enumStringMethods = listOf(
            "setKvCacheQuantization",
            "setKvCacheQuantizationMode",
            "setTurboQuantMode",
        )
        val modeValues = if (enable) {
            listOf("turboquant")
        } else {
            listOf("none", "off", "disabled", "default")
        }
        for (methodName in enumStringMethods) {
            var appliedMethod = false
            for (modeValue in modeValues) {
                val applied = runCatching {
                    val method = flagsClass.getMethod(methodName, String::class.java)
                    method.invoke(instance, modeValue)
                    true
                }.getOrDefault(false)
                if (applied) {
                    appliedAny = true
                    appliedMethod = true
                    break
                }
            }
            if (!appliedMethod) {
                continue
            }
        }

        when {
            appliedAny && verboseLogs && enable ->
                Log.i(TAG, "TurboQuant KV-cache optimization enabled (experimental flags)")
            appliedAny && verboseLogs && !enable ->
                Log.i(TAG, "TurboQuant KV-cache optimization disabled for fallback mode")
            appliedAny -> Unit
            else ->
                Log.w(
                    TAG,
                    "No TurboQuant/KV-cache experimental flags matched this LiteRT build; " +
                        "continuing without them (slightly higher memory / latency possible).",
                )
        }
    }

    suspend fun generateWithFallback(
        prompt: String,
        temperature: Double,
        topP: Double,
        topK: Int,
        /** Stop decode after roughly this many new tokens (LiteRT-LM does not expose a hard cap on Flow). */
        maxNewTokens: Int,
        onToken: (String) -> Unit,
    ) {
        var emittedAnyToken = false
        val firstAttempt = runCatching {
            chatStreamInternal(
                prompt = prompt,
                temperature = temperature,
                topP = topP,
                topK = topK,
                maxNewTokens = maxNewTokens,
            ).collect { chunk ->
                if (chunk.isNotEmpty()) emittedAnyToken = true
                onToken(chunk)
            }
        }
        if (firstAttempt.isSuccess) return
        val generationError = firstAttempt.exceptionOrNull() ?: return
        if (emittedAnyToken || !canFallbackTurboQuant()) {
            throw generationError
        }
        val params = lastInitParams
            ?: throw generationError
        Log.w(
            TAG,
            "LiteRT generation failed with TurboQuant ON; retrying once with TurboQuant OFF: ${generationError.message}",
        )
        switchTurboQuantMode(TurboQuantMode.OFF, verboseLogs = params.verboseLogs)
        runCatching { close() }
        engine = createEngineWithConfiguredTurboQuant(
            modelPath = params.modelPath,
            cacheDir = params.cacheDir,
            maxNumTokens = params.maxNumTokens,
            verboseLogs = params.verboseLogs,
        )
        chatStreamInternal(
            prompt = prompt,
            temperature = temperature,
            topP = topP,
            topK = topK,
            maxNewTokens = maxNewTokens,
        ).collect { chunk -> onToken(chunk) }
    }

    private fun chatStreamInternal(
        prompt: String,
        temperature: Double,
        topP: Double,
        topK: Int,
        maxNewTokens: Int,
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
                var approxDecodeTokens = 0
                // litertlm-android exposes no stop_sequence; cancel once marker turn includes question section or max tokens.
                conv.sendMessageAsync(prompt)
                    .catch { throw it }
                    .collect { message: Message ->
                        val full = message.toString()
                        when {
                            full.startsWith(accumulated) && full.length > accumulated.length -> {
                                val delta = full.substring(accumulated.length)
                                if (delta.isNotEmpty()) {
                                    emit(delta)
                                    approxDecodeTokens += liteRtApproxNewTokens(delta)
                                    if (approxDecodeTokens >= maxNewTokens ||
                                        markerAssistantTurnLooksStructurallyComplete(full)
                                    ) {
                                        runCatching { conv.cancelProcess() }
                                    }
                                }
                                accumulated = full
                            }
                            full.isNotEmpty() && full != accumulated -> {
                                emit(full)
                                approxDecodeTokens += liteRtApproxNewTokens(full)
                                if (approxDecodeTokens >= maxNewTokens ||
                                    markerAssistantTurnLooksStructurallyComplete(full)
                                ) {
                                    runCatching { conv.cancelProcess() }
                                }
                                accumulated = full
                            }
                        }
                    }
            }
        }.flowOn(Dispatchers.Default)
    }

    /** Rough token estimate for decode throttling (chars → tokens). */
    private fun liteRtApproxNewTokens(text: String): Int =
        ((text.length + 3) / 4).coerceAtLeast(1)

    private fun markerAssistantTurnLooksStructurallyComplete(full: String): Boolean {
        val t = full.trim()
        if (GyangoOutputEnvelope.isLessonEnvelope(t)) {
            return GyangoOutputEnvelope.hasClosedLessonTail(t)
        }
        return false
    }

    fun close() {
        engine?.close()
        engine = null
    }

    private fun canFallbackTurboQuant(): Boolean =
        turboQuantMode == TurboQuantMode.ON && !turboQuantFallbackUsed

    private fun switchTurboQuantMode(mode: TurboQuantMode, verboseLogs: Boolean) {
        turboQuantMode = mode
        if (mode == TurboQuantMode.OFF) {
            turboQuantFallbackUsed = true
        }
        applyTurboQuantForKvCache(enable = (mode == TurboQuantMode.ON), verboseLogs = verboseLogs)
    }

    private fun useEmulatorLitertWorkarounds(): Boolean =
        BuildConfig.ENABLE_EMULATOR_LITERT_WORKAROUNDS && isLikelyEmulator()

    private fun isLikelyEmulator(): Boolean {
        val fp = Build.FINGERPRINT
        if (fp.startsWith("generic") || fp.startsWith("unknown")) return true
        if (fp.lowercase().contains("emulator")) return true
        if (Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")) return true
        if (Build.MODEL.contains("Emulator", ignoreCase = true)) return true
        if (Build.MODEL.contains("google_sdk", ignoreCase = true)) return true
        if (Build.MODEL.contains("Android SDK built for", ignoreCase = true)) return true
        if (Build.PRODUCT.contains("sdk_google")) return true
        if (Build.PRODUCT == "google_sdk") return true
        if (Build.PRODUCT.contains("emulator", ignoreCase = true)) return true
        if (Build.PRODUCT.contains("simulator", ignoreCase = true)) return true
        // e.g. sdk_gphone64_arm64, aosp_atd — typical AVD images
        if (Build.PRODUCT.startsWith("sdk_gphone")) return true
        return qemuSystemProperty() == "1"
    }

    private fun qemuSystemProperty(): String? = runCatching {
        val sp = Class.forName("android.os.SystemProperties")
        val get = sp.getMethod("get", String::class.java)
        get.invoke(null, "ro.kernel.qemu") as? String
    }.getOrNull()

    private fun hasBundledDispatchLibrary(nativeLibraryDir: String?): Boolean {
        if (nativeLibraryDir.isNullOrBlank()) return false
        val dir = File(nativeLibraryDir)
        val libs = dir.list() ?: return false
        return libs.any { name ->
            name.endsWith(".so", ignoreCase = true) &&
                name.contains("dispatch", ignoreCase = true)
        }
    }

    private fun shouldOmitDiskCacheDirForStability(verboseLogs: Boolean): Boolean {
        if (verboseLogs) {
            Log.w(
                TAG,
                "LiteRT XNNPack disk cache disabled: avoiding process-fatal native aborts on cache append failure.",
            )
        }
        return true
    }

    companion object {
        private const val TAG = "LiteRtEngineMgr"
    }

    private enum class TurboQuantMode {
        ON,
        OFF,
    }

    private data class EngineInitParams(
        val modelPath: String,
        val cacheDir: String,
        val maxNumTokens: Int,
        val verboseLogs: Boolean,
    )
}
