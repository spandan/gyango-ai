package ai.gyango.core.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.StatFs

data class ModelHardwareSupport(
    val hasGpu: Boolean,
    val hasNpu: Boolean,
    /** Total device RAM from [ActivityManager.MemoryInfo.totalMem], in megabytes. */
    val totalRamMb: Long,
    /**
     * Phase 1 gate for the ~2.3 GB on-device model: need acceleration **and** enough system RAM
     * for weights, runtime cache, and the OS without chronic swapping.
     */
    val hasEnoughRam: Boolean,
    /** Free space on the app private storage volume, in megabytes (see [freeDiskMegabytes]). */
    val freeDiskMb: Long,
    /** Room for model assets / PAD merge / runtime without immediate install failure. */
    val hasEnoughFreeDisk: Boolean,
) {
    /** LiteRT model is allowed only with GPU or NPU, sufficient RAM, and sufficient free disk. */
    val canRunSelectedModel: Boolean get() =
        (hasGpu || hasNpu) && hasEnoughRam && hasEnoughFreeDisk
}

object ModelHardwareGate {
    private const val FEATURE_NEURAL_NETWORKS = "android.hardware.neuralnetworks"

    /** Minimum total RAM for the current on-device model pack (see product notes). */
    const val MIN_TOTAL_RAM_MB: Long = 6144L

    /** Minimum free app-storage space for the ~2.3 GB model plus merge headroom. */
    const val MIN_FREE_DISK_MB: Long = 3584L

    const val UNSUPPORTED_DEVICE_MESSAGE: String =
        "GyanGo needs a supported GPU or NPU, at least 6 GB of memory, and enough free storage. This device cannot run the on-device model."

    fun inspect(context: Context): ModelHardwareSupport {
        val pm = context.packageManager
        val hasNpu = pm.hasSystemFeature(FEATURE_NEURAL_NETWORKS)
        val hasGpu = hasGpuFeature(pm) || hasModernGles(context)
        val totalRamMb = totalRamMegabytes(context)
        val hasEnoughRam = totalRamMb >= MIN_TOTAL_RAM_MB
        val freeDiskMb = freeDiskMegabytes(context)
        val hasEnoughFreeDisk = freeDiskMb >= MIN_FREE_DISK_MB
        return ModelHardwareSupport(
            hasGpu = hasGpu,
            hasNpu = hasNpu,
            totalRamMb = totalRamMb,
            hasEnoughRam = hasEnoughRam,
            freeDiskMb = freeDiskMb,
            hasEnoughFreeDisk = hasEnoughFreeDisk,
        )
    }

    private fun freeDiskMegabytes(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong / (1024L * 1024L)
        } catch (_: Throwable) {
            0L
        }
    }

    private fun totalRamMegabytes(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024L * 1024L)
    }

    private fun hasGpuFeature(pm: PackageManager): Boolean {
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK)) return true
        return false
    }

    private fun hasModernGles(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        // GLES 3.1+ is a practical minimum for modern mobile ML GPU paths.
        return am.deviceConfigurationInfo?.reqGlEsVersion ?: 0 >= 0x30001
    }
}
