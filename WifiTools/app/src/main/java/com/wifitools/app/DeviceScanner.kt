package com.wifitools.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

data class Device(val ip: String, val mac: String?, val label: String?)

object DeviceScanner {

    private val PROBE_PORTS = intArrayOf(80, 443, 53, 62078, 8080)

    /**
     * بيفحص شبكة /24 الخاصة بالجهاز ويرجع الأجهزة اللي ردت.
     * onProgress بتتنده من threads خلفية.
     */
    suspend fun scan(
        localIp: String,
        gateway: String?,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<Device> = coroutineScope {
        val base = localIp.substringBeforeLast('.')
        val total = 254
        val done = AtomicInteger(0)
        val gate = Semaphore(64)
        val alive = Collections.synchronizedSet(mutableSetOf<String>())

        (1..total).map { i ->
            async(Dispatchers.IO) {
                val ip = "$base.$i"
                gate.withPermit {
                    if (isAlive(ip)) alive.add(ip)
                }
                val d = done.incrementAndGet()
                if (d % 4 == 0 || d == total) onProgress(d, total)
            }
        }.awaitAll()

        // أندرويد 10+ غالبًا بيمنع قراءة جدول ARP، فلو اتقرا بنستفيد منه، ولو لأ بنكمل عادي
        val arp = readArp()
        arp.keys.filter { it.startsWith("$base.") }.forEach { alive.add(it) }

        alive.add(localIp)
        gateway?.takeIf { it.startsWith("$base.") }?.let { alive.add(it) }

        alive.sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }.map { ip ->
            Device(
                ip = ip,
                mac = arp[ip],
                label = when (ip) {
                    localIp -> "هذا الجهاز"
                    gateway -> "الراوتر"
                    else -> null
                }
            )
        }
    }

    private fun isAlive(ip: String): Boolean {
        try {
            if (InetAddress.getByName(ip).isReachable(350)) return true
        } catch (_: Exception) {
        }
        for (port in PROBE_PORTS) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 200)
                    return true
                }
            } catch (e: ConnectException) {
                // "Connection refused" معناها الجهاز موجود ورفض البورت
                if (e.message?.contains("refused", ignoreCase = true) == true) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun readArp(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        try {
            File("/proc/net/arp").readLines().drop(1).forEach { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 4) {
                    val mac = p[3]
                    if (mac != "00:00:00:00:00:00" && p[2] != "0x0") out[p[0]] = mac
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
