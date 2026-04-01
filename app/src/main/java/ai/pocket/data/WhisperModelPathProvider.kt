package ai.pocket.data

import android.content.Context
import java.io.File

/**
 * Copies the Whisper tiny quantized GGML model from assets to app storage.
 * Default: [Hugging Face ggml-org](https://huggingface.co/ggerganov/whisper.cpp) style `ggml-tiny-q8_0.bin`.
 */
class WhisperModelPathProvider(
    private val context: Context,
    private val assetName: String = "models/ggml-tiny-q8_0.bin"
) {

    private val fileName: String = assetName.substringAfterLast('/')

    fun resolvePathOrNull(): String? {
        val out = File(context.applicationContext.filesDir, fileName)
        return try {
            if (!out.isFile || out.length() == 0L) {
                context.assets.open(assetName).use { input ->
                    out.outputStream().use { output -> input.copyTo(output, bufferSize = 256 * 1024) }
                }
            }
            out.takeIf { it.isFile && it.length() > 0L }?.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
