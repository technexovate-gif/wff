package com.wifitools.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/** قياس السرعة عن طريق سيرفرات Cloudflare العامة. */
object SpeedTest {

    private const val PING_URL = "https://www.google.com/generate_204"
    private const val DOWN_URL = "https://speed.cloudflare.com/__down?bytes=30000000"
    private const val UP_URL = "https://speed.cloudflare.com/__up"
    private const val MAX_SECONDS = 8.0

    /** متوسط زمن الاستجابة بالميلي ثانية، أو null لو فشل. */
    suspend fun ping(): Int? = withContext(Dispatchers.IO) {
        val times = mutableListOf<Long>()
        repeat(5) {
            ensureActive()
            try {
                val c = URL(PING_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 3000
                c.readTimeout = 3000
                c.useCaches = false
                val t0 = System.nanoTime()
                c.responseCode
                times += (System.nanoTime() - t0) / 1_000_000
                c.disconnect()
            } catch (_: Exception) {
            }
        }
        // أول محاولة بتشمل وقت الاتصال الأول فبنتجاهلها لو عندنا غيرها
        val usable = if (times.size > 1) times.drop(1) else times
        if (usable.isEmpty()) null else usable.average().toInt()
    }

    /** سرعة التحميل بالميجابت/ث، أو null لو فشل. */
    suspend fun download(onProgress: (Double) -> Unit): Double? = withContext(Dispatchers.IO) {
        try {
            val c = URL(DOWN_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 5000
            c.useCaches = false
            val buf = ByteArray(64 * 1024)
            var total = 0L
            val t0 = System.nanoTime()
            var lastReport = t0
            c.inputStream.use { input ->
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    val now = System.nanoTime()
                    val secs = (now - t0) / 1e9
                    if (now - lastReport > 250_000_000L) {
                        onProgress(total * 8 / secs / 1e6)
                        lastReport = now
                    }
                    if (secs >= MAX_SECONDS) break
                }
            }
            c.disconnect()
            val secs = (System.nanoTime() - t0) / 1e9
            if (total == 0L || secs <= 0.0) null else total * 8 / secs / 1e6
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** سرعة الرفع بالميجابت/ث، أو null لو فشل. */
    suspend fun upload(onProgress: (Double) -> Unit): Double? = withContext(Dispatchers.IO) {
        try {
            val size = 6 * 1024 * 1024
            val payload = Random.nextBytes(size)
            val c = URL(UP_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 15000
            c.doOutput = true
            c.requestMethod = "POST"
            c.useCaches = false
            c.setFixedLengthStreamingMode(size)
            c.setRequestProperty("Content-Type", "application/octet-stream")

            var sent = 0L
            val t0 = System.nanoTime()
            var lastReport = t0
            c.outputStream.use { out ->
                val chunk = 32 * 1024
                var off = 0
                while (off < size) {
                    coroutineContext.ensureActive()
                    val len = minOf(chunk, size - off)
                    out.write(payload, off, len)
                    off += len
                    sent += len
                    val now = System.nanoTime()
                    val secs = (now - t0) / 1e9
                    if (now - lastReport > 250_000_000L) {
                        onProgress(sent * 8 / secs / 1e6)
                        lastReport = now
                    }
                    if (secs >= MAX_SECONDS) break
                }
            }
            val secs = (System.nanoTime() - t0) / 1e9
            c.disconnect()
            if (sent == 0L || secs <= 0.0) null else sent * 8 / secs / 1e6
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
