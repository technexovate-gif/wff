package com.wifitools.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wifitools.app.databinding.ActivityMainBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var current: WifiDetails? = null
    private var speedJob: Job? = null
    private var scanJob: Job? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { loadNetworkInfo() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnRefresh.setOnClickListener { ensurePermissionThenLoad() }
        b.btnSpeed.setOnClickListener { runSpeedTest() }
        b.btnScan.setOnClickListener { runDeviceScan() }
        b.btnRestart.setOnClickListener { openRouterPage() }
        b.btnRestartHelp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.restart_help)
                .setMessage(R.string.restart_help_text)
                .setPositiveButton(R.string.close, null)
                .show()
        }

        ensurePermissionThenLoad()
    }

    // ---------------------------------------------------------------- الشبكة

    private fun ensurePermissionThenLoad() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) loadNetworkInfo() else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun loadNetworkInfo() {
        val d = NetworkInfoReader.read(this)
        current = d

        if (d == null) {
            b.tvSsid.setText(R.string.not_connected)
            b.tvDetails.text = ""
            b.signalBar.progress = 0
            b.tvPermissionNote.visibility = android.view.View.GONE
            return
        }

        b.tvSsid.text = d.ssid ?: getString(R.string.ssid_hidden)
        b.tvPermissionNote.visibility =
            if (d.ssid == null) android.view.View.VISIBLE else android.view.View.GONE
        b.signalBar.setProgressCompat(d.signalPercent, true)

        val lines = mutableListOf<String>()
        lines += "قوة الإشارة: ${d.signalPercent}%  (${ltr("${d.rssi} dBm")})"
        lines += "سرعة الاتصال: ${ltr("${d.linkSpeedMbps} Mbps")}"
        lines += "التردد: ${ltr(d.band)}  •  القناة ${ltr(d.channel.toString())}"
        d.standard?.let { lines += "المعيار: ${ltr(it)}" }
        d.ip?.let { lines += "عنوان IP: ${ltr("$it/${d.prefixLength}")}" }
        d.gateway?.let { lines += "عنوان الراوتر: ${ltr(it)}" }
        if (d.dns.isNotBlank()) lines += "DNS: ${ltr(d.dns)}"
        d.bssid?.let { lines += "BSSID: ${ltr(it)}" }
        b.tvDetails.text = lines.joinToString("\n")
    }

    // ---------------------------------------------------------------- السرعة

    private fun runSpeedTest() {
        if (speedJob?.isActive == true) {
            speedJob?.cancel()
            return
        }
        if (current == null) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        b.btnSpeed.setText(R.string.stop)
        b.speedProgress.visibility = android.view.View.VISIBLE
        b.tvPing.text = getString(R.string.speed_ping)
        b.tvDown.text = getString(R.string.speed_down)
        b.tvUp.text = getString(R.string.speed_up)

        speedJob = lifecycleScope.launch {
            try {
                b.tvSpeedStatus.setText(R.string.speed_testing_ping)
                val ping = SpeedTest.ping()
                b.tvPing.text = "${getString(R.string.speed_ping)}: " +
                        (ping?.let { ltr("$it ms") } ?: "—")

                b.tvSpeedStatus.setText(R.string.speed_testing_down)
                val down = SpeedTest.download { mbps ->
                    runOnUiThread { b.tvDown.text = "${getString(R.string.speed_down)}: ${ltr(fmt(mbps))}" }
                }
                b.tvDown.text = "${getString(R.string.speed_down)}: " + (down?.let { ltr(fmt(it)) } ?: "—")

                b.tvSpeedStatus.setText(R.string.speed_testing_up)
                val up = SpeedTest.upload { mbps ->
                    runOnUiThread { b.tvUp.text = "${getString(R.string.speed_up)}: ${ltr(fmt(mbps))}" }
                }
                b.tvUp.text = "${getString(R.string.speed_up)}: " + (up?.let { ltr(fmt(it)) } ?: "—")

                b.tvSpeedStatus.setText(
                    if (ping == null && down == null && up == null) R.string.speed_failed
                    else R.string.speed_done
                )
            } catch (e: CancellationException) {
                b.tvSpeedStatus.setText(R.string.speed_idle)
                throw e
            } finally {
                b.speedProgress.visibility = android.view.View.GONE
                b.btnSpeed.setText(R.string.start_speed)
            }
        }
    }

    private fun fmt(mbps: Double) = String.format(java.util.Locale.US, "%.1f Mbps", mbps)

    // ---------------------------------------------------------------- الأجهزة

    private fun runDeviceScan() {
        if (scanJob?.isActive == true) return
        val d = current
        val ip = d?.ip
        if (d == null || ip == null) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        b.btnScan.isEnabled = false
        b.devProgress.progress = 0
        b.devProgress.visibility = android.view.View.VISIBLE
        b.tvDevList.text = ""
        b.tvDevCount.text = getString(R.string.devices_scanning, 0, 254)

        scanJob = lifecycleScope.launch {
            try {
                val devices = DeviceScanner.scan(ip, d.gateway) { done, total ->
                    runOnUiThread {
                        b.devProgress.progress = done
                        b.tvDevCount.text = getString(R.string.devices_scanning, done, total)
                    }
                }
                b.tvDevCount.text = "عدد الأجهزة: ${devices.size}"
                b.tvDevList.text = devices.joinToString("\n") { dev ->
                    buildString {
                        append(ltr(dev.ip))
                        dev.label?.let { append("  —  $it") }
                        dev.mac?.let { append("\n    ${ltr(it)}") }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                b.devProgress.visibility = android.view.View.GONE
                b.btnScan.isEnabled = true
            }
        }
    }

    // ---------------------------------------------------------------- الراوتر

    private fun openRouterPage() {
        // لو لسه مفيش بيانات اتقرت، نحاول نقراها الأول
        val gw = (current ?: NetworkInfoReader.read(this))?.gateway
        if (gw.isNullOrBlank()) {
            Toast.makeText(this, R.string.no_gateway, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, RouterWebActivity::class.java).putExtra(RouterWebActivity.EXTRA_GATEWAY, gw))
    }

    /** يعزل النص الإنجليزي/الأرقام علشان يتعرض صح جوه جملة عربي. */
    private fun ltr(s: String) = "⁦$s⁩"
}
