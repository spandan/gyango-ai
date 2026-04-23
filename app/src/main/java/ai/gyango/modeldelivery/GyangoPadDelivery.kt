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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Base LiteRT model delivered as five on-demand Play Asset packs (~500 MiB each).
 *
 * **Download:** packs that are not yet readable are requested together with one [AssetPackManager.fetch] call so
 * the Play Store can transfer them in parallel. Packs stay installed until the merged model file is valid, then
 * [AssetPackManager.removePack] runs for all of them—interrupted runs resume without re-downloading finished packs.
 *
 * **Merge:** after every chunk is readable, bytes are concatenated in pack order into one temp file under
 * [filesDir], then renamed to the final `.litertlm`. Peak disk is roughly **installed packs + merged file** until
 * cleanup runs.
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
     * Ensures all packs are present (parallel fetch via Play for packs not yet readable), then concatenates
     * chunk files and only then removes PAD packs. [onProgress] is overall 0..1 (~88% download, merge, finalize).
     * [onPackDetail]: `merging=false` during download; `merging=true` during file assembly.
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

                val packCount = PACK_NAMES.size
                onPackDetail(0, false)
                awaitAllPacksDownloadedAndReadable(manager) { download01 ->
                    onProgress((download01 * 0.88f).coerceIn(0f, 0.88f))
                }

                if (tmp.exists() && !isCompleteMergedLength(tmp.length())) {
                    tmp.delete()
                }

                onPackDetail(0, true)
                for ((index, pack) in PACK_NAMES.withIndex()) {
                    val chunkRel = chunkAssetRelativeForPackIndex(index)
                    val chunkSource =
                        resolveReadableChunkSource(manager, pack, chunkRel)
                            ?: throw IllegalStateException(
                                "PAD pack \"$pack\" was readable before merge but not during merge (chunk=$chunkRel)",
                            )
                    appendPackChunkToMergeTmp(appCtx, chunkSource, packIndex = index)
                    onProgress((0.88f + (index + 1).toFloat() / packCount * 0.10f).coerceIn(0f, 0.98f))
                }

                onProgress(0.98f)
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
     * Fetches every pack that is not yet readable in one [AssetPackManager.fetch] (Play may download in parallel),
     * then blocks until each chunk path is available. Packs already complete from a prior run are left alone.
     *
     * Uses [AssetPackManager.registerListener] for progress; [getPackStates] only on idle ticks to limit Finsky
     * log noise. After [AssetPackStatus.COMPLETED], [resolveReadableChunkSource] can lag—same slow readiness as
     * single-pack flow.
     */
    private suspend fun awaitAllPacksDownloadedAndReadable(
        manager: AssetPackManager,
        onDownloadProgress: suspend (Float) -> Unit,
    ) {
        val updates = Channel<AssetPackState>(Channel.UNLIMITED)
        val listener = AssetPackStateUpdateListener { st ->
            if (PACK_NAMES.contains(st.name())) {
                updates.trySend(st)
            }
        }
        manager.registerListener(listener)
        try {
            fun readableSource(i: Int): PadChunkSource? =
                resolveReadableChunkSource(
                    manager,
                    PACK_NAMES[i],
                    chunkAssetRelativeForPackIndex(i),
                )

            fun allReadable(): Boolean = PACK_NAMES.indices.all { readableSource(it) != null }

            suspend fun refreshStates(): Map<String, AssetPackState> =
                manager.getPackStates(PACK_NAMES).await().packStates()

            var states = refreshStates()
            for (name in PACK_NAMES) {
                states[name]
                    ?: throw IllegalStateException("PAD pack \"$name\": no state from Play")
            }

            val notReadableNames =
                PACK_NAMES.filterIndexed { index, _ -> readableSource(index) == null }
            if (notReadableNames.isNotEmpty()) {
                manager.fetch(notReadableNames).await()
                states = refreshStates()
            }

            suspend fun emitProgress() {
                var sum = 0f
                for (i in PACK_NAMES.indices) {
                    sum +=
                        if (readableSource(i) != null) {
                            1f
                        } else {
                            states[PACK_NAMES[i]]?.let { packFractionFromState(it) } ?: 0f
                        }
                }
                onDownloadProgress((sum / PACK_NAMES.size).coerceIn(0f, 1f))
            }

            emitProgress()
            if (allReadable()) return

            var idleTicks = 0
            val deadlineMs = System.currentTimeMillis() + 6L * 60L * 60L * 1000L
            while (!allReadable()) {
                if (System.currentTimeMillis() > deadlineMs) {
                    throw IllegalStateException("PAD download timed out waiting for all packs to become readable")
                }
                val ev = withTimeoutOrNull(750L) { updates.receive() }
                if (ev != null) {
                    idleTicks = 0
                    states = states.toMutableMap().apply { put(ev.name(), ev) }
                    when (ev.status()) {
                        AssetPackStatus.FAILED ->
                            throw IllegalStateException(
                                "PAD pack \"${ev.name()}\" download failed (Play errorCode=${ev.errorCode()})",
                            )
                        AssetPackStatus.CANCELED ->
                            throw IllegalStateException("PAD pack \"${ev.name()}\" download was canceled")
                        else -> Unit
                    }
                } else {
                    idleTicks++
                    if (idleTicks % 4 == 0) {
                        states = refreshStates()
                    }
                }
                emitProgress()
            }
            if (BuildConfig.VERBOSE_DEBUG_LOGS) {
                Log.d(TAG, "PAD all packs readable (${PACK_NAMES.size})")
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
