package ai.pocket.modeldelivery

import ai.pocket.hardware.HardwareAuditor
import ai.pocket.hardware.ModelTier
import android.content.Context
import com.google.android.play.core.aipacks.AiPackManager
import com.google.android.play.core.aipacks.AiPackManagerFactory
import com.google.android.play.core.aipacks.model.AiPackStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DownloadProgress(
    val packName: String,
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytesToDownload: Long,
) {
    val percent: Int
        get() = if (totalBytesToDownload <= 0L) 0 else ((bytesDownloaded * 100) / totalBytesToDownload).toInt()
}

class AiModelDownloader(
    context: Context,
    private val hardwareAuditor: HardwareAuditor = HardwareAuditor(context),
    private val aiPackManager: AiPackManager = AiPackManagerFactory.getInstance(context),
) {

    fun resolvePackNameForDevice(): String = when (hardwareAuditor.inspect().ramTier) {
        ModelTier.BRONZE_2GB_3GB -> PACK_BRONZE
        ModelTier.SILVER_4GB_6GB -> PACK_SILVER
        ModelTier.GOLD_8GB_PLUS -> PACK_GOLD
    }

    suspend fun ensureTeacherModelReady(onProgress: (DownloadProgress) -> Unit): String {
        val packName = resolvePackNameForDevice()
        aiPackManager.getPackLocation(packName)?.assetsPath()?.let { return it }

        val listener = com.google.android.play.core.aipacks.AiPackStateUpdateListener { state ->
            if (state.name() != packName) return@AiPackStateUpdateListener
            onProgress(
                DownloadProgress(
                    packName = packName,
                    status = state.status(),
                    bytesDownloaded = state.bytesDownloaded(),
                    totalBytesToDownload = state.totalBytesToDownload(),
                )
            )
        }

        aiPackManager.registerListener(listener)
        return try {
            suspendCancellableCoroutine { cont ->
                aiPackManager.fetch(listOf(packName))
                    .addOnSuccessListener {
                        val location = aiPackManager.getPackLocation(packName)
                        val path = location?.assetsPath()
                        if (path != null) {
                            cont.resume(path)
                        } else {
                            cont.resumeWithException(IllegalStateException("Pack fetched but assets path missing: $packName"))
                        }
                    }
                    .addOnFailureListener { err ->
                        cont.resumeWithException(err)
                    }
            }
        } finally {
            aiPackManager.unregisterListener(listener)
        }
    }

    companion object {
        const val PACK_BRONZE = "ai-pack-teacher-3gb"
        const val PACK_SILVER = "ai-pack-teacher-6gb"
        const val PACK_GOLD = "ai-pack-teacher-12gb"

        fun isTerminalStatus(status: Int): Boolean {
            return status == AiPackStatus.COMPLETED ||
                status == AiPackStatus.FAILED ||
                status == AiPackStatus.CANCELED
        }
    }
}
