package com.feelcool.tv

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var splashView: ImageView
    private lateinit var webView: WebView
    
    private var backPressCount = 0
    private var lastBackPressTime = 0L

    // 🔥 黑科技：创建一个内置 TMDB 真实无污染 IP 的自定义 DNS 客户端
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    // 强行将 TMDB 域名指向其官方海外未被污染的 ASN IP 节点
                    if (hostname == "api.themoviedb.org" || hostname == "image.tmdb.org") {
                        return listOf(
                            InetAddress.getByName("143.244.50.212"),
                            InetAddress.getByName("169.150.247.38")
                        )
                    }
                    return Dns.SYSTEM.lookup(hostname)
                }
            })
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = FrameLayout(this)
        
        webView = WebView(this)
        webView.setBackgroundColor(Color.BLACK)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100 
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        
        // 🔥 核心拦截逻辑
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                
                // 只要是 TMDB 的请求，全部劫持交给 OkHttp 处理
                if (url.contains("themoviedb.org") || url.contains("tmdb.org")) {
                    try {
                        val reqBuilder = Request.Builder().url(url)
                        // 伪装浏览器请求头
                        request?.requestHeaders?.forEach { (key, value) ->
                            reqBuilder.addHeader(key, value)
                        }
                        reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        
                        val response = okHttpClient.newCall(reqBuilder.build()).execute()
                        val inputStream = response.body?.byteStream()
                        
                        // 推断文件类型
                        var mimeType = "application/json"
                        if (url.endsWith(".jpg") || url.endsWith(".jpeg") || url.contains("/t/p/")) mimeType = "image/jpeg"
                        if (url.endsWith(".png")) mimeType = "image/png"

                        // 注入跨域允许头，防止 WebView 的 Fetch API 报 CORS 错误
                        val headers = mutableMapOf<String, String>()
                        response.headers.forEach { headers[it.first] = it.second }
                        headers["Access-Control-Allow-Origin"] = "*"
                        headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"

                        return WebResourceResponse(
                            mimeType,
                            "UTF-8",
                            response.code,
                            "OK",
                            headers,
                            inputStream
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        
        webView.addJavascriptInterface(JSBridge(), "FeelCoolTV")
        
        splashView = ImageView(this)
        splashView.setBackgroundColor(Color.BLACK)
        val splashResId = resources.getIdentifier("splash", "drawable", packageName)
        if (splashResId != 0) {
            splashView.setImageResource(splashResId)
            splashView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
        
        layout.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        layout.addView(splashView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(layout)

        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        webView.evaluateJavascript("javascript:window.handleHardwareBack()") { result ->
            if (result == "\"true\"" || result == "true") {
                backPressCount = 0
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime > 2000) {
                    backPressCount = 1
                    lastBackPressTime = currentTime
                    webView.evaluateJavascript("javascript:showToast('再按两次返回键退出很酷TV')", null)
                } else {
                    backPressCount++
                    if (backPressCount >= 3) {
                        super.onBackPressed()
                    } else {
                        webView.evaluateJavascript("javascript:showToast('再按 ${3 - backPressCount} 次返回键退出')", null)
                    }
                }
            }
        }
    }

    inner class JSBridge {
        @JavascriptInterface
        fun executeAction(action: String, title: String) {
            if (action.startsWith("iptv:")) {
                val url = action.substring(5)
                val intent = Intent(this@MainActivity, ExoPlayerActivity::class.java)
                intent.putExtra("VIDEO_URL", url)
                startActivity(intent)
            } else if (action.startsWith("app:")) {
                val pkg = action.substring(4)
                
                var launchIntent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                if (launchIntent == null) {
                    launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                }
                
                if (launchIntent == null) {
                    val targetActivity = when (pkg) {
                        "com.xiaomi.mitv.tvplayer" -> "com.xiaomi.mitv.tvplayer.MainActivity"
                        "com.xiaomi.mitv.mediaexplorer" -> "com.xiaomi.mitv.mediaexplorer.MainActivity"
                        "com.xiaomi.mitv.hyper.screensaver" -> "com.xiaomi.mitv.hyper.screensaver.MainActivity"
                        "com.xiaomi.smarthome.tv" -> "com.xiaomi.smarthome.tv.MainActivity"
                        else -> null
                    }
                    
                    if (targetActivity != null) {
                        launchIntent = Intent(Intent.ACTION_MAIN)
                        launchIntent.component = ComponentName(pkg, targetActivity)
                    }
                }
                
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(launchIntent)
                    } catch (e: Exception) {
                        runOnUiThread { webView.evaluateJavascript("javascript:showToast('应用组件被系统限制启动')", null) }
                    }
                } else {
                    runOnUiThread { webView.evaluateJavascript("javascript:showToast('未找到应用，请确认是否安装')", null) }
                }
            }
        }

        @JavascriptInterface
        fun hideSplash() {
            runOnUiThread {
                splashView.animate().alpha(0f).setDuration(600).withEndAction {
                    splashView.visibility = View.GONE
                }
            }
        }
    }
}
