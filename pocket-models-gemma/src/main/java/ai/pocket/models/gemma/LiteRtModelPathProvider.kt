package ai.pocket.models.gemma

import android.content.Context
import java.io.File

/**
 * Copies a `.litertlm` bundle from assets into app files dir (same pattern as prior GGUF flow).
 * Place the model under `app/src/main/assets/` (e.g. from
 * [Hugging Face Gemma 3n E2B](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)).
 */
class LiteRtModelPathProvider(
    private val context: Context,
    private val assetName: String
) {

    fun getOrCopyModel(): String {
        val name = assetName.substringAfterLast('/')
        val outFile = File(context.filesDir, name)
        if (!outFile.isFile || outFile.length() == 0L) {
            context.assets.open(assetName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                }
            }
        }
        return outFile.absolutePath
    }
}
