package ai.pocket.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build

enum class ModelTier {
    BRONZE_2GB_3GB,
    SILVER_4GB_6GB,
    GOLD_8GB_PLUS,
}

data class HardwareProfile(
    val totalRamBytes: Long,
    val ramTier: ModelTier,
    val npuAvailable: Boolean,
    val socManufacturer: String?,
    val socModel: String?,
)

class HardwareAuditor(private val context: Context) {

    fun inspect(): HardwareProfile {
        val totalRam = totalRamBytes()
        return HardwareProfile(
            totalRamBytes = totalRam,
            ramTier = classifyRamTier(totalRam),
            npuAvailable = detectNpuAvailability(),
            socManufacturer = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() },
            socModel = Build.SOC_MODEL.takeIf { it.isNotBlank() },
        )
    }

    fun classifyRamTier(totalRamBytes: Long = totalRamBytes()): ModelTier {
        val gb = totalRamBytes.toDouble() / BYTES_PER_GB
        return when {
            gb >= 8.0 -> ModelTier.GOLD_8GB_PLUS
            gb >= 4.0 -> ModelTier.SILVER_4GB_6GB
            else -> ModelTier.BRONZE_2GB_3GB
        }
    }

    private fun totalRamBytes(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    /**
     * Conservative heuristic. We only advertise "available" when modern SoC metadata is present.
     * Runtime backend selection should still degrade gracefully if acceleration init fails.
     */
    private fun detectNpuAvailability(): Boolean {
        val socManufacturer = Build.SOC_MANUFACTURER.lowercase()
        val socModel = Build.SOC_MODEL.lowercase()
        if (socManufacturer.isBlank() || socModel.isBlank()) return false
        return socManufacturer.contains("qualcomm") ||
            socManufacturer.contains("mediatek") ||
            socManufacturer.contains("samsung") ||
            socManufacturer.contains("google")
    }

    private companion object {
        private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
    }
}
