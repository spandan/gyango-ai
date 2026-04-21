package ai.gyango.core

import android.content.Context

/**
 * Optional suspend hook invoked on a worker dispatcher before LiteRT resolves the model file path
 * (e.g. Play Asset Delivery fetch + merge into app storage).
 */
fun interface LiteRtPathPrepareHook {
    suspend fun prepare(applicationContext: Context)
}
