package com.wifitools.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address

data class WifiDetails(
    val ssid: String?,
    val bssid: String?,
    val ip: String?,
    val prefixLength: Int,
    val gateway: String?,
    val dns: String,
    val rssi: Int,
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val standard: String?
) {
    val band: String
        get() = when {
            frequencyMhz in 2400..2500 -> "2.4 GHz"
            frequencyMhz in 4900..5900 -> "5 GHz"
            frequencyMhz >= 5925 -> "6 GHz"
            else -> "—"
        }

    val channel: Int
        get() = when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2400..2500 -> (frequencyMhz - 2407) / 5
            frequencyMhz in 4900..5900 -> (frequencyMhz - 5000) / 5
            frequencyMhz >= 5925 -> (frequencyMhz - 5950) / 5
            else -> 0
        }

    /** نسبة قوة الإشارة من 0 إلى 100 */
    val signalPercent: Int
        get() = ((rssi + 100) * 2).coerceIn(0, 100)
}

object NetworkInfoReader {

    /**
     * يقرأ بيانات شبكة الواي فاي الحالية.
     * بيربط التطبيق بشبكة الواي فاي (حتى لو مفيهاش إنترنت)
     * علشان فحص الأجهزة وصفحة الراوتر يشتغلوا صح.
     */
    fun read(ctx: Context): WifiDetails? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java) ?: return null

        @Suppress("DEPRECATION")
        val wifiNet = cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: run {
            cm.bindProcessToNetwork(null)
            return null
        }
        cm.bindProcessToNetwork(wifiNet)

        val lp = cm.getLinkProperties(wifiNet)
        @Suppress("DEPRECATION")
        val wi = wm.connectionInfo

        val v4 = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val gateway = lp?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val dns = lp?.dnsServers?.mapNotNull { it.hostAddress }?.joinToString("  ") ?: ""

        val rawSsid = wi?.ssid?.trim('"')
        val ssid = if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") null else rawSsid
        val rawBssid = wi?.bssid
        val bssid = if (rawBssid.isNullOrBlank() || rawBssid == "02:00:00:00:00:00") null else rawBssid

        val standard = if (Build.VERSION.SDK_INT >= 30) {
            when (wi?.wifiStandard) {
                1 -> "Wi-Fi Legacy (a/b/g)"
                4 -> "Wi-Fi 4 (n)"
                5 -> "Wi-Fi 5 (ac)"
                6 -> "Wi-Fi 6 (ax)"
                7 -> "WiGig (ad)"
                8 -> "Wi-Fi 7 (be)"
                else -> null
            }
        } else null

        @Suppress("DEPRECATION")
        return WifiDetails(
            ssid = ssid,
            bssid = bssid,
            ip = v4?.address?.hostAddress,
            prefixLength = v4?.prefixLength ?: 24,
            gateway = gateway,
            dns = dns,
            rssi = wi?.rssi ?: -100,
            linkSpeedMbps = wi?.linkSpeed ?: 0,
            frequencyMhz = wi?.frequency ?: 0,
            standard = standard
        )
    }
}
