package ai.pocket.models.gemma

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Resolves a filesystem path for a `.litertlm` bundle so [com.google.ai.edge.litertlm.Engine] can load it.
 *
 * **Why copy at all?** The published LiteRT-LM Android API takes a **file path** only; there is no
 * `AssetFileDescriptor` / mmap-from-APK hook in `EngineConfig`. Sherpa-ONNX TTS has the same constraint.
 *
 * **The “4GB = 2×2GB” confusion:** Anything under `assets/` is **inside the install (APK/AAB)**. If you
 * also copy that blob to `filesDir` / `getExternalFilesDir`, many users see **large app size + large
 * user data** — two full uncompressed bytes on disk. You **cannot** delete or “move out” an asset
 * from the APK after install; Android does not allow that.
 *
 * **Practical mitigations (single on-disk copy):**
 * 1. **Do not bundle** the model in `assets/` for production; download or deliver it once
 *    (Play Feature Module, PAD, or your own HTTPS) into [getExternalFilesDir] / [filesDir] only.
 * 2. Use **[absoluteModelPathOverride]** (or CI/sideload) so the engine reads **one** path you control.
 * 3. If the asset stays bundled, expect **compressed-in-APK + one extracted file** at runtime;
 *    compressed APK storage is usually smaller than the extracted file, but both still show under
 *    system “storage” breakdowns to varying degrees.
 */
class LiteRtModelPathProvider(
    private val context: Context,
    private val assetName: String,
    /**
     * When non-blank and the file exists, it is used **as-is** (no copy from assets).
     * Use for sideload, tests, or a downloader that writes outside `assets/`.
     */
    private val absoluteModelPathOverride: String? = null,
) {

    fun getOrCopyModel(): String {
        val name = assetName.substringAfterLast('/')
        val targetDir = context.getExternalFilesDir(null) ?: context.filesDir
        val outFile = File(targetDir, name)

        val override = absoluteModelPathOverride?.trim().orEmpty()
        if (override.isNotEmpty()) {
            val f = File(override)
            require(f.isFile && f.canRead()) {
                "GEMMA model override path is not a readable file: $override"
            }
            require(f.length() >= MIN_MODEL_BYTES) {
                "GEMMA model override file too small (${f.length()} bytes): $override"
            }
            Log.i(TAG, "Using model from override path (no asset copy): ${f.absolutePath}")
            return f.absolutePath
        }

        val expectedSize = assetFdLengthOrMinusOne()
        if (outFile.exists() && isValidCachedFile(outFile, expectedSize)) {
            Log.d(TAG, "Reusing existing model at ${outFile.absolutePath}")
            return outFile.absolutePath
        }

        if (!canOpenAssetForCopy()) {
            throw IllegalStateException(
                "No usable Gemma model: asset '$assetName' is missing or unreadable and " +
                    "no valid file at ${outFile.absolutePath} (expected ≥ $MIN_MODEL_BYTES bytes). " +
                    "Bundle the .litertlm under assets, place it at that path once, or set an absolute override path " +
                    "(${TAG} KDoc)."
            )
        }

        Log.i(TAG, "Copying model from assets → ${outFile.absolutePath} (bundled size hint: $expectedSize)")
        outFile.parentFile?.mkdirs()
        val tempFile = File(targetDir, "$name.tmp")
        try {
            context.assets.open(assetName).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = COPY_BUFFER)
                }
            }
            if (!tempFile.renameTo(outFile)) {
                throw IllegalStateException("Failed to rename temp file to $outFile")
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }

        return outFile.absolutePath
    }

    /** When the asset is stored uncompressed, [android.content.res.AssetFileDescriptor.getLength] matches the blob. */
    private fun assetFdLengthOrMinusOne(): Long =
        runCatching {
            context.assets.openFd(assetName).use { fd -> fd.length }
        }.getOrElse { -1L }

    private fun canOpenAssetForCopy(): Boolean =
        runCatching {
            context.assets.open(assetName).use { }
            true
        }.getOrDefault(false)

    private fun isValidCachedFile(file: File, expectedFromFd: Long): Boolean {
        if (!file.isFile || !file.canRead()) return false
        val len = file.length()
        if (len < MIN_MODEL_BYTES) return false
        return expectedFromFd < 0L || len == expectedFromFd
    }

    companion object {
        private const val TAG = "LiteRtModelPath"
        private const val COPY_BUFFER = 1024 * 1024
        private const val MIN_MODEL_BYTES = 1L * 1024L * 1024L
    }
}
