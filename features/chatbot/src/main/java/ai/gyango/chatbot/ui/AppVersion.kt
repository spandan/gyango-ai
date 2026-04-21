package ai.gyango.chatbot.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class AppVersionInfo(
    val versionName: String,
    val versionCode: Long,
)

@Composable
fun rememberAppVersionInfo(): AppVersionInfo? {
    val context = LocalContext.current
    return remember(context.packageName) {
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            val name = info.versionName.orEmpty()
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            AppVersionInfo(name, code)
        }.getOrNull()
    }
}
