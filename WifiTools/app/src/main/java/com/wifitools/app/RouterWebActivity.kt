package com.wifitools.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.wifitools.app.databinding.ActivityRouterBinding

/**
 * بيفتح صفحة إدارة الراوتر جوه التطبيق.
 * مفيش تخزين لأي يوزر أو باسورد؛ المستخدم بيسجل الدخول بنفسه.
 */
class RouterWebActivity : AppCompatActivity() {

    private lateinit var b: ActivityRouterBinding
    private var gateway: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRouterBinding.inflate(layoutInflater)
        setContentView(b.root)

        gateway = intent.getStringExtra(EXTRA_GATEWAY).orEmpty()
        if (gateway.isBlank()) {
            finish()
            return
        }

        b.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            // صفحة الراوتر مش محتاجة ملفات أو محتوى من برّه
            allowFileAccess = false
            allowContentAccess = false
        }

        b.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                b.webProgress.progress = newProgress
                b.webProgress.visibility = if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
            }
        }

        b.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                b.webProgress.visibility = android.view.View.VISIBLE
            }

            // بعض الراوترات بتستخدم HTTPS بشهادة موقّعة ذاتيًا، فبنسمح بيها لعنوان الراوتر بس
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                val host = Uri.parse(error?.url ?: "").host
                if (host == gateway) handler?.proceed() else handler?.cancel()
            }

            // منع الخروج لأي موقع غير الراوتر
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean = request?.url?.host != gateway
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (b.webView.canGoBack()) b.webView.goBack() else finish()
            }
        })

        b.webView.loadUrl("http://$gateway")
    }

    override fun onDestroy() {
        if (::b.isInitialized) {
            b.webView.stopLoading()
            b.webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GATEWAY = "gateway"
    }
}
