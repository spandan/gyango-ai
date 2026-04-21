package ai.gyango.modeldelivery

import ai.gyango.BuildConfig
import android.content.Context
import android.util.Log
import com.google.android.play.core.assetpacks.AssetLocation
import com.google.android.play.core.assetpacks.AssetPackLocation
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Base LiteRT model delivered as five on-demand Play Asset packs (~500 MiB each),
 * fetched in order, then merged into app-internal storage for a single file path.
 *
 * After each pack completes, its chunk bytes are appended to a single merge temp file under [filesDir], then
 * [AssetPackManager.removePack] runs for that pack. We never rely on five simultaneous [getPackLocation] paths
 * staying valid until a late merge (Play can invalidate earlier packs once later ones install). The temp file
 * is renamed to the final merged model when all packs are concatenated.
 *
 * The merged bundle lives under [Context.filesDir] (private app-internal storage). LiteRT's
 * XNNPack disk weight cache is written next to the model file, so keeping the model internal
 * avoids external-storage policy pitfalls on newer Android/emulator images.
 *
 * If a previous install wrote the merged model under [Context.getExternalFilesDir], we migrate
 * it into [filesDir] once.
 */
object GyangoPadDelivery {
    private const val TAG = "GyangoPadDelivery"
    private const val MIN_MERGED_BYTES = 512L * 1024L * 1024L

    /** When > 0, merged model and resume tmp must match this exact byte count (see app/build.gradle.kts). */
    private val mergedExpectedBytes: Long = BuildConfig.PAD_MERGED_MODEL_EXPECTED_BYTES

    /** One merge/download at a time (MainActivity PAD UI vs [GyangoApplication] LiteRT path hook). */
    private val ensurePadMutex = Mutex()

    private sealed class PadChunkSource {
        data class FromFile(val file: File) : PadChunkSource()
        data class FromApk(val location: AssetLocation) : PadChunkSource()
    }

    private const val MERGED_FILENAME = "gemma-4-E2B-it.litertlm"
    private const val MERGED_SUBDIR = "pad_merged_models"

    fun chunkAssetRelativeForPackIndex(index: Int): String = "models/pad_chunk_$index.bin"

    val PACK_NAMES: List<String> = listOf(
        "gyango_base_llm_0",
        "gyango_base_llm_1",
        "gyango_base_llm_2",
        "gyango_base_llm_3",
        "gyango_base_llm_4",
    )

    private fun mergedModelDir(appCtx: Context): File = File(appCtx.filesDir, MERGED_SUBDIR)

    private fun externalMergedFile(appCtx: Context): File? =
        appCtx.getExternalFilesDir(null)?.let { File(it, "$MERGED_SUBDIR/$MERGED_FILENAME") }

    private fun mergedModelFile(appCtx: Context): File =
        File(mergedModelDir(appCtx), MERGED_FILENAME)

    private fun mergeTmpFile(appCtx: Context): File =
        File(mergedModelDir(appCtx), "$MERGED_FILENAME.tmp")

    /**
     * One-time copy from prior external merged path into internal [filesDir] path.
     */
    private fun migrateLegacyMergedIfPresent(appCtx: Context) {
        val target = mergedModelFile(appCtx)
        if (target.isFile && isCompleteMergedLength(target.length())) return
        val legacy = externalMergedFile(appCtx) ?: return
        if (!legacy.isFile || !isCompleteMergedLength(legacy.length())) return
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        if (BuildConfig.VERBOSE_DEBUG_LOGS) {
            Log.i(TAG, "Migrating merged model from external path to ${target.absolutePath}")
        }
        legacy.copyTo(target = target, overwrite = true, bufferSize = 8 * 1024 * 1024)
        if (!legacy.delete()) {
            Log.w(TAG, "Could not delete external merged file after copy: ${legacy.absolutePath}")
        }
    }

    fun mergedModelAbsolutePath(context: Context): String {
        val appCtx = context.applicationContext
        migrateLegacyMergedIfPresent(appCtx)
        return mergedModelFile(appCtx).absolutePath
    }

    /** True when a previously merged model file looks present (skip network gate on return visits). */
    fun isMergedModelReady(context: Context): Boolean {
        val appCtx = context.applicationContext
        val internal = mergedModelFile(appCtx)
        if (internal.isFile && isCompleteMergedLength(internal.length())) return true
        val external = externalMergedFile(appCtx)
        return external?.isFile == true && isCompleteMergedLength(external.length())
    }

    /**
     * Ensures all packs are present (downloads on-demand packs in order), then concatenates
     * chunk files. [onProgress] is overall 0..1 (~92% download, merge to 1).
     * [onPackDetail]: one-based pack index while downloading; [merging]=true during file assembly.
     */
    suspend fun ensureChunksFetchedAndMerged(
        context: Context,
        onProgress: suspend (overall01: Float) -> Unit = {},
        onPackDetail: suspend (oneBasedPack: Int, merging: Boolean) -> Unit = { _, _ -> },
    ) {
        ensurePadMutex.withLock {
            withContext(Dispatchers.IO) {
                val appCtx = context.applicationContext
                migrateLegacyMergedIfPresent(appCtx)
                val manager = AssetPackManagerFactory.getInstance(appCtx)

                if (isMergedFilePresent(appCtx)) {
                    mergeTmpFile(appCtx).delete()
                    removeDownloadedPacksBestEffort(manager)
                    onProgress(1f)
                    return@withContext
                }

                val tmp = mergeTmpFile(appCtx)
                if (tmp.isFile && isCompleteMergedLength(tmp.length())) {
                    finalizeMergeTmpToOutFile(appCtx, manager)
                    onProgress(1f)
                    return@withContext
                }
                if (tmp.exists()) {
                    tmp.delete()
                }

                val packCount = PACK_NAMES.size
                for ((index, pack) in PACK_NAMES.withIndex()) {
                    onPackDetail(index + 1, false)
                    val chunkSource = fetchPackWithProgress(
                        manager = manager,
                        packName = pack,
                        packIndex0 = index,
                        packCount = packCount,
                        onProgress = { packFraction ->
                            val overall = (index + packFraction) / packCount * 0.92f
                            onProgress(overall)
                        },
                    )
                    appendPackChunkToMergeTmp(appCtx, chunkSource, packIndex = index)
                    removeSinglePackBestEffort(manager, pack)
                }

                onPackDetail(0, true)
                onProgress(0.94f)
                finalizeMergeTmpToOutFile(appCtx, manager)
                onProgress(1f)
            }
        }
    }

    private fun isMergedFilePresent(appCtx: Context): Boolean {
        val internal = mergedModelFile(appCtx)
        if (internal.isFile && isCompleteMergedLength(internal.length())) return true
        val external = externalMergedFile(appCtx)
        return external?.isFile == true && isCompleteMergedLength(external.length())
    }

    /**
     * True when [len] is a plausible completed merge. If we know the exact PAD layout from Gradle,
     * require an exact match so a partial tmp (e.g. exactly four 500 MiB chunks) is never renamed
     * to the final `.litertlm`.
     */
    private fun isCompleteMergedLength(len: Long): Boolean {
        if (len < MIN_MERGED_BYTES) return false
        if (mergedExpectedBytes > 0L) return len == mergedExpectedBytes
        return true
    }

    /**
     * Registers a pack download with Play, then blocks until [AssetPackStatus.COMPLETED] and chunk bytes are readable.
     *
     * Uses [AssetPackManager.registerListener] for progress instead of polling [AssetPackManager.getPackStates] in a
     * tight loop. Frequent `getPackStates` calls correlate with Play Store (`Finsky`) spamming `requestDownloadInfo()`
     * in logcat while PAD runs.
     *
     * After [AssetPackStatus.COMPLETED], [AssetPackManager.getPackLocation] can lag; we poll slowly with
     * [resolveReadableChunkSource] only until paths or [AssetPackManager.getAssetLocation] become available.
     */
    private suspend fun fetchPackWithProgress(
        manager: AssetPackManager,
        packName: String,
        packIndex0: Int,
        packCount: Int,
        onProgress: suspend (Float) -> Unit,
    ): PadChunkSource {
        val chunkRel = chunkAssetRelativeForPackIndex(packIndex0)
        manager.fetch(listOf(packName)).await()

        val updates = Channel<AssetPackState>(Channel.UNLIMITED)
        val listener = AssetPackStateUpdateListener { st ->
            if (st.name() == packName) {
                updates.trySend(st)
            }
        }
        manager.registerListener(listener)
        try {
            val initial = manager.getPackStates(listOf(packName)).await().packStates()[packName]
                ?: throw IllegalStateException("PAD pack \"$packName\": no state from Play after fetch")
            updates.trySend(initial)

            while (true) {
                val st = updates.receive()
                onProgress(packFractionFromState(st))
                when (st.status()) {
                    AssetPackStatus.FAILED -> {
                        val code = st.errorCode()
                        throw IllegalStateException("PAD pack \"$packName\" download failed (Play errorCode=$code)")
                    }
                    AssetPackStatus.CANCELED ->
                        throw IllegalStateException("PAD pack \"$packName\" download was canceled")
                    AssetPackStatus.COMPLETED -> {
                        var source = resolveReadableChunkSource(manager, packName, chunkRel)
                        if (source != null) {
                            onProgress(1f)
                            if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                                Log.d(TAG, "PAD fetch done: $packName (${packIndex0 + 1}/$packCount)")
                            }
                            return source
                        }
                        var completedWaitMs = 0L
                        while (completedWaitMs < 90_000L) {
                            delay(500L)
                            completedWaitMs += 500L
                            source = resolveReadableChunkSource(manager, packName, chunkRel)
                            if (source != null) {
                                onProgress(1f)
                                if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                                    Log.d(TAG, "PAD fetch done: $packName (${packIndex0 + 1}/$packCount)")
                                }
                                return source
                            }
                        }
                        throw IllegalStateException(
                            "PAD pack \"$packName\" reached COMPLETED but chunk is not readable yet " +
                                "(no filesystem path and no APK asset mapping from Play; waited ${completedWaitMs}ms)",
                        )
                    }
                    else -> Unit
                }
            }
        } finally {
            runCatching { manager.unregisterListener(listener) }
            updates.close()
        }
    }

    private fun pickPackLocation(manager: AssetPackManager, packName: String): AssetPackLocation? =
        manager.getPackLocation(packName) ?: manager.getPackLocations()[packName]

    /**
     * Play sometimes reports [AssetPackStatus.COMPLETED] before [AssetPackManager.getPackLocation] updates.
     * Prefer [getPackLocations] as a second source. Packs stored as split APKs expose bytes via [getAssetLocation]
     * while [AssetPackLocation.assetsPath] is null.
     */
    private fun resolveReadableChunkSource(
        manager: AssetPackManager,
        packName: String,
        chunkRelativeUnderAssets: String,
    ): PadChunkSource? {
        val loc = pickPackLocation(manager, packName)
        loc?.assetsPath()?.let { root ->
            val f = File(root, chunkRelativeUnderAssets)
            if (f.isFile) return PadChunkSource.FromFile(f)
        }
        loc?.path()?.let { packRoot ->
            val f = File(packRoot, "assets/$chunkRelativeUnderAssets")
            if (f.isFile) return PadChunkSource.FromFile(f)
        }
        val apkLoc = manager.getAssetLocation(packName, chunkRelativeUnderAssets)
        if (apkLoc != null && apkLoc.size() > 0L) {
            return PadChunkSource.FromApk(apkLoc)
        }
        return null
    }

    private suspend fun appendPackChunkToMergeTmp(
        appCtx: Context,
        source: PadChunkSource,
        packIndex: Int,
    ) {
        val tmp = mergeTmpFile(appCtx)
        tmp.parentFile?.mkdirs()
        val lenBefore = if (tmp.isFile) tmp.length() else 0L
        val buf = ByteArray(8 * 1024 * 1024)
        val expectedLen = when (source) {
            is PadChunkSource.FromFile -> {
                val src = source.file
                if (!src.isFile) {
                    throw IllegalStateException("PAD chunk not on disk at ${src.absolutePath}")
                }
                FileInputStream(src).use { input ->
                    FileOutputStream(tmp, packIndex > 0).use { output ->
                        while (true) {
                            val r = input.read(buf)
                            if (r == -1) break
                            if (r > 0) output.write(buf, 0, r)
                        }
                    }
                }
                src.length()
            }
            is PadChunkSource.FromApk -> {
                val al = source.location
                val apkPath = al.path()
                    ?: throw IllegalStateException("PAD pack index $packIndex: APK asset path is null")
                RandomAccessFile(File(apkPath), "r").use { raf ->
                    raf.seek(al.offset())
                    FileOutputStream(tmp, packIndex > 0).use { output ->
                        var remaining = al.size()
                        while (remaining > 0L) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val r = raf.read(buf, 0, toRead)
                            if (r == -1) break
                            if (r > 0) {
                                output.write(buf, 0, r)
                                remaining -= r.toLong()
                            }
                        }
                        if (remaining != 0L) {
                            throw IllegalStateException(
                                "PAD pack index $packIndex: short read from APK asset (left $remaining bytes)",
                            )
                        }
                    }
                }
                al.size()
            }
        }
        val lenAfter = tmp.length()
        if (lenAfter - lenBefore != expectedLen) {
            throw IllegalStateException(
                "PAD merge tmp size mismatch for pack $packIndex: appended ${lenAfter - lenBefore} expected $expectedLen",
            )
        }
    }

    private suspend fun removeSinglePackBestEffort(manager: AssetPackManager, packName: String) {
        if (pickPackLocation(manager, packName) == null) return
        runCatching {
            manager.removePack(packName).await()
            if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                Log.i(TAG, "Removed PAD pack after merge append: $packName")
            }
        }.onFailure { e ->
            Log.w(TAG, "removePack failed for $packName: ${e.message}")
        }
    }

    private fun packFractionFromState(st: AssetPackState): Float {
        val status = st.status()
        if (status == AssetPackStatus.COMPLETED) return 1f
        val total = st.totalBytesToDownload()
        if (total > 0L) {
            return (st.bytesDownloaded().toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
        val pct = st.transferProgressPercentage()
        return (pct / 100f).coerceIn(0f, 1f)
    }

    private suspend fun finalizeMergeTmpToOutFile(appCtx: Context, manager: AssetPackManager) {
        val tmp = mergeTmpFile(appCtx)
        val outFile = mergedModelFile(appCtx)
        outFile.parentFile?.mkdirs()
        val tmpLen = if (tmp.isFile) tmp.length() else 0L
        if (!tmp.isFile || tmpLen < MIN_MERGED_BYTES) {
            throw IllegalStateException(
                "PAD merged temp missing or too small (${tmpLen} bytes, min=$MIN_MERGED_BYTES)",
            )
        }
        if (mergedExpectedBytes > 0L && tmpLen != mergedExpectedBytes) {
            throw IllegalStateException(
                "PAD merged temp wrong size ($tmpLen bytes, expected $mergedExpectedBytes); delete tmp and retry",
            )
        }
        if (outFile.isFile && outFile.length() == tmp.length()) {
            tmp.delete()
            removeDownloadedPacksBestEffort(manager)
            return
        }
        if (outFile.exists() && !outFile.delete()) {
            throw IllegalStateException("Could not replace existing merged model at ${outFile.absolutePath}")
        }
        if (!tmp.renameTo(outFile)) {
            throw IllegalStateException("Failed to rename merged model to ${outFile.absolutePath}")
        }
        removeDownloadedPacksBestEffort(manager)
    }

    /**
     * Drops on-demand PAD payloads from app-internal PAD storage after the merged model exists.
     * Uses Play's API (do not delete pack paths manually).
     */
    private suspend fun removeDownloadedPacksBestEffort(manager: AssetPackManager) {
        for (packName in PACK_NAMES) {
            if (pickPackLocation(manager, packName) == null) continue
            runCatching {
                manager.removePack(packName).await()
                if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                    Log.i(TAG, "Removed PAD pack from device storage: $packName")
                }
            }.onFailure { e ->
                Log.w(TAG, "removePack failed for $packName: ${e.message}")
            }
        }
    }

}
