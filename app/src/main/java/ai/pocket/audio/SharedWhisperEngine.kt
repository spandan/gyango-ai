package ai.pocket.audio

import ai.pocket.data.WhisperModelPathProvider
import ai.pocket.whisper.WhisperBridge
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Process-wide Whisper context until [releaseFromMemory]. */
class SharedWhisperEngine(context: Context) {

    private val pathProvider = WhisperModelPathProvider(context.applicationContext)
    private val threadCap = min(4, max(1, Runtime.getRuntime().availableProcessors()))

    fun resolveModelPathOrNull(): String? = pathProvider.resolvePathOrNull()

    suspend fun prepareModelIfPresent() {
        val path = resolveModelPathOrNull() ?: return
        withContext(Dispatchers.IO) {
            if (!WhisperBridge.init(path, threadCap)) {
                error("Whisper init failed")
            }
        }
    }

    suspend fun transcribe16kMono(floats: FloatArray): String {
        val path = resolveModelPathOrNull() ?: error("Whisper model missing")
        return withContext(Dispatchers.IO) {
            if (!WhisperBridge.init(path, threadCap)) {
                error("Whisper init failed")
            }
            WhisperBridge.transcribe(floats, threadCap).trim()
        }
    }

    suspend fun releaseFromMemory() {
        withContext(Dispatchers.IO) {
            WhisperBridge.release()
        }
    }
}
