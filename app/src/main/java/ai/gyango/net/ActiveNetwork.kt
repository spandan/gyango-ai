package ai.gyango.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun Context.isActiveNetworkWifi(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

fun Context.isActiveNetworkCellular(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}

fun Context.isActiveNetworkEthernet(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

/**
 * True if **any** known network uses Wi‑Fi and declares internet (not only the system default).
 * Use for PAD gating when cellular is default but Wi‑Fi is also connected.
 */
fun Context.hasWifiNetworkWithInternet(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    return cm.allNetworks.any { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@any false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * PAD / large downloads: default route is Wi‑Fi or Ethernet, **or** a Wi‑Fi interface with internet exists
 * (dual‑stack / cellular‑default devices).
 */
fun Context.isUnmeteredPadDownloadNetworkAvailable(): Boolean {
    if (isActiveNetworkWifi() || isActiveNetworkEthernet()) return true
    return hasWifiNetworkWithInternet()
}
