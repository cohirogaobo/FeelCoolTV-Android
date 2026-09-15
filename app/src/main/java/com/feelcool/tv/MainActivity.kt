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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var splashView: ImageView
    private lateinit var webView: WebView
    
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    
    private var backPressCount = 0
    private var lastBackPressTime = 0L

    private val antiSleepHandler = Handler(Looper.getMainLooper())
    private val antiSleepRunnable = object : Runnable {
        override fun run() {
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN))
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN))
            antiSleepHandler.postDelayed(this, 120 * 1000) 
        }
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val layout = FrameLayout(this)
        layout.setBackgroundColor(Color.BLACK)
        
        playerView = PlayerView(this).apply {
            useController = false
            setBackgroundColor(Color.BLACK)
        }
        layout.addView(playerView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        webView = WebView(this)
        // 性能关键：默认设置为不透明黑色，消除双层 Alpha 混合！仅播放背景视频时才透明
        webView.setBackgroundColor(Color.BLACK)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true // 启用本地存储提升索引速度
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100 
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // 禁用无用的手势与缩放系统，减轻事件监听链负担
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            
            // 提高渲染管线调度优先级
            @Suppress("DEPRECATION")
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                if (url.contains("api.themoviedb.org")) {
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
                        reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0")
                        
                        val response = okHttpClient.newCall(reqBuilder.build()).execute()
                        val inputStream = response.body?.byteStream()

                        val headers = mutableMapOf<String, String>()
                        response.headers.forEach { (key, value) -> 
                            headers[key] = value 
                        }
                        headers["Access-Control-Allow-Origin"] = "*"

                        return WebResourceResponse(
                            "application/json",
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
            // 👇 核心修复：无论是打开 IPTV 还是第三方应用，跳转前必须先释放背景播放器，交出硬件解码器！
            stopBackgroundVideo()

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
                splashView.animate().alpha(0f).setDuration(300).withEndAction {
                    splashView.visibility = View.GONE
                    // 彻底释放开屏图 Bitmap 显存
                    splashView.setImageDrawable(null)
                }
            }
        }

        @JavascriptInterface
        fun playBackgroundVideo(url: String) {
            runOnUiThread {
                if (player == null) {
                    player = ExoPlayer.Builder(this@MainActivity).build().apply {
                        playerView.player = this
                        volume = 0f 
                        addListener(object : Player.Listener {
                            override fun onRenderedFirstFrame() {
                                // 视频出首帧时，将 WebView 背景透明以便透出底层视频
                                webView.setBackgroundColor(Color.TRANSPARENT)
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
                // 停止视频时立即恢复黑色背景，关闭透明图层混合，释放解码器
                webView.setBackgroundColor(Color.BLACK)
                player?.stop()
                player?.clearMediaItems()
            }
        }
    }
}
