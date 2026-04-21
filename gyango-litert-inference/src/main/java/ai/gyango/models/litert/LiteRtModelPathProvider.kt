package ai.gyango.models.litert

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Resolves a filesystem path for a LiteRT bundle so the engine can load from a real file path.
 */
class LiteRtModelPathProvider(
    private val context: Context,
    private val assetName: String,
    private val absoluteModelPathOverride: String? = null,
    private val absoluteModelPathOverrideProvider: (() -> String?)? = null,
    private val verboseInferenceTiming: Boolean = false,
) {
    private val copyLock = Any()

    fun getOrCopyModel(): String = synchronized(copyLock) {
        val t0 = SystemClock.elapsedRealtime()
        val name = assetName.substringAfterLast('/')
        val targetDir = context.getExternalFilesDir(null) ?: context.filesDir
        val outFile = File(targetDir, name)

        val override = (
            absoluteModelPathOverrideProvider?.invoke()
                ?: absoluteModelPathOverride
            )?.trim().orEmpty()
        if (override.isNotEmpty()) {
            val f = File(override)
            require(f.isFile && f.canRead()) {
                "LiteRT model override path is not a readable file: $override"
            }
            require(f.length() >= MIN_MODEL_BYTES) {
                "LiteRT model override file too small (${f.length()} bytes): $override"
            }
            if (verboseInferenceTiming) {
                Log.d(TAG, "[perf] getOrCopyModel source=override totalMs=${SystemClock.elapsedRealtime() - t0}")
            }
            return f.absolutePath
        }

        val expectedSize = assetFdLengthOrMinusOne()
        if (outFile.exists() && isValidCachedFile(outFile, expectedSize)) {
            if (verboseInferenceTiming) {
                Log.d(TAG, "[perf] getOrCopyModel source=reuse_cache totalMs=${SystemClock.elapsedRealtime() - t0}")
            }
            return outFile.absolutePath
        }

        if (!canOpenAssetForCopy()) {
            throw IllegalStateException(
                "No usable LiteRT model: asset '$assetName' is missing or unreadable and " +
                    "no valid file at ${outFile.absolutePath} (expected >= $MIN_MODEL_BYTES bytes)."
            )
        }

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
        if (verboseInferenceTiming) {
            Log.d(TAG, "[perf] getOrCopyModel source=asset_copy totalMs=${SystemClock.elapsedRealtime() - t0}")
        }
        outFile.absolutePath
    }

    private fun assetFdLengthOrMinusOne(): Long =
        runCatching { context.assets.openFd(assetName).use { fd -> fd.length } }.getOrElse { -1L }

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
