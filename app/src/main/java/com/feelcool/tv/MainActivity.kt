package com.feelcool.tv

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var splashView: ImageView
    private lateinit var webView: WebView
    
    // 动态背景播放器
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    
    private var backPressCount = 0
    private var lastBackPressTime = 0L

    // 防休眠心跳包
    private val antiSleepHandler = Handler(Looper.getMainLooper())
    private val antiSleepRunnable = object : Runnable {
        override fun run() {
            // 发送无害空按键，欺骗小米系统的屏幕保护监控
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN))
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN))
            antiSleepHandler.postDelayed(this, 120 * 1000) // 2分钟跳动一次
        }
    }

    // 🔥 全局物理级强缓存引擎 (100MB 磁盘缓存)
    private val okHttpClient by lazy {
        val cacheDir = File(applicationContext.cacheDir, "FeelCool_Image_Cache")
        val cache = Cache(cacheDir, 100L * 1024L * 1024L) // 100MB 极限缓存空间
        
        // 强制改写服务端响应头，让所有图片必须在本地乖乖缓存 10 天
        val forceCacheInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            response.newBuilder()
                .header("Cache-Control", "public, max-age=864000") // 10 days
                .removeHeader("Pragma")
                .build()
        }

        OkHttpClient.Builder()
            .cache(cache)
            .addNetworkInterceptor(forceCacheInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    if (hostname == "api.themoviedb.org") {
                        return listOf(
                            InetAddress.getByName("104.16.61.155"),
                            InetAddress.getByName("104.16.62.155"),
                            InetAddress.getByName("13.224.157.34")
                        )
                    }
                    return Dns.SYSTEM.lookup(hostname)
                }
            })
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val layout = FrameLayout(this)
        layout.setBackgroundColor(Color.BLACK)
        
        // 1. 最底层：ExoPlayer 视图（用于直播动态背景预览）
        playerView = PlayerView(this).apply {
            useController = false
            setBackgroundColor(Color.BLACK)
        }
        layout.addView(playerView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // 2. 中间层：WebView 容器
        webView = WebView(this)
        // 彻底透明化，让底层的播放器能透视出来
        webView.setBackgroundColor(Color.TRANSPARENT)
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
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                
                // 智能拦截：只接管图片、配置文件和 API，直接略过音视频流，避免影响播放性能
                val ext = url.substringAfterLast(".").lowercase()
                val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif") || 
                              url.contains("/t/p/") || url.contains("unsplash.com") || 
                              url.contains("weserv.nl") || url.contains("bdstatic.com") || url.contains("hdslb.com")
                val isApi = url.contains("api.themoviedb.org") || url.contains("config.json")
                
                if (isImage || isApi) {
                    if (request?.method.equals("OPTIONS", ignoreCase = true)) {
                        val corsHeaders = mutableMapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                            "Access-Control-Allow-Headers" to "*",
                            "Access-Control-Max-Age" to "86400"
                        )
                        return WebResourceResponse("text/plain", "UTF-8", 200, "OK", corsHeaders, ByteArrayInputStream(ByteArray(0)))
                    }

                    try {
                        val reqBuilder = Request.Builder().url(url)
                        request?.requestHeaders?.forEach { (key, value) ->
                            if (!key.equals("Host", ignoreCase = true)) {
                                reqBuilder.addHeader(key, value)
                            }
                        }
                        reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                        
                        val response = okHttpClient.newCall(reqBuilder.build()).execute()
                        val inputStream = response.body?.byteStream()

                        var mimeType = "application/json"
                        if (isImage) mimeType = "image/jpeg"
                        if (url.endsWith(".png")) mimeType = "image/png"

                        val headers = mutableMapOf<String, String>()
                        response.headers.forEach { (key, value) -> headers[key] = value }
                        headers["Access-Control-Allow-Origin"] = "*"

                        return WebResourceResponse(
                            mimeType,
                            "UTF-8",
                            response.code,
                            if (response.message.isEmpty()) "OK" else response.message,
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
        layout.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        
        // 3. 最顶层：原生开屏启动图
        splashView = ImageView(this)
        splashView.setBackgroundColor(Color.BLACK)
        val splashResId = resources.getIdentifier("splash", "drawable", packageName)
        if (splashResId != 0) {
            splashView.setImageResource(splashResId)
            splashView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
        layout.addView(splashView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        
        setContentView(layout)
        webView.loadUrl("file:///android_asset/index.html")

        // 启动防休眠心跳包
        antiSleepHandler.postDelayed(antiSleepRunnable, 60 * 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        antiSleepHandler.removeCallbacksAndMessages(null)
        player?.release()
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

        // 🔥 动态预览黑科技 API
        @JavascriptInterface
        fun playBackgroundVideo(url: String) {
            runOnUiThread {
                if (player == null) {
                    player = ExoPlayer.Builder(this@MainActivity).build().apply {
                        playerView.player = this
                        volume = 0f // 预览静音
                        addListener(object : Player.Listener {
                            override fun onRenderedFirstFrame() {
                                // 当视频第一帧渲染出来时，通知前端隐藏静态海报，完美衔接！
                                webView.evaluateJavascript("javascript:if(window.onBackgroundVideoStarted) window.onBackgroundVideoStarted();", null)
                            }
                        })
                    }
                }
                player?.setMediaItem(MediaItem.fromUri(url))
                player?.prepare()
                player?.play()
            }
        }

        @JavascriptInterface
        fun stopBackgroundVideo() {
            runOnUiThread {
                player?.stop()
                player?.clearMediaItems()
            }
        }
    }
}
