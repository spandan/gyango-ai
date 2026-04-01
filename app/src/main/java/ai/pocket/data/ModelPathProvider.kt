package ai.pocket.data

import android.content.Context
import java.io.File

/**
 * Default weights: **RWKV-7 G1e ~1.5B** GGUF `Q4_K_M` (quantized for on-device RAM).
 * Override [assetName] / output name if you switch checkpoints.
 */
class ModelPathProvider(
    private val context: Context,
    private val assetName: String = "models/rwkv7-g1e-1.5b-Q4_K_M.gguf"
) {

    fun getOrCopyModel(): String {
        val outFile = File(context.filesDir, assetName.substringAfterLast('/'))
        if (!outFile.exists()) {
            context.assets.open(assetName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }
}
