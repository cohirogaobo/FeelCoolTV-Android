package com.feelcool.tv

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.TextureView
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var splashView: ImageView
    private lateinit var webView: WebView
    
    private var player: ExoPlayer? = null
    private lateinit var textureView: TextureView
    
    private var backPressCount = 0
    private var lastBackPressTime = 0L

    private var isFullScreenIptv = false
    private var iptvExitCount = 0

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
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val layout = FrameLayout(this)
        layout.setBackgroundColor(Color.BLACK)
        
        textureView = TextureView(this)
        layout.addView(textureView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        webView = WebView(this)
        webView.setBackgroundColor(Color.BLACK)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true 
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100 
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
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
                        val corsHeaders = mutableMapOf("Access-Control-Allow-Origin" to "*", "Access-Control-Allow-Methods" to "GET, POST, OPTIONS", "Access-Control-Allow-Headers" to "*", "Access-Control-Max-Age" to "86400")
                        return WebResourceResponse("text/plain", "UTF-8", 200, "OK", corsHeaders, ByteArrayInputStream(ByteArray(0)))
                    }
                    try {
                        val reqBuilder = Request.Builder().url(url)
                        request?.requestHeaders?.forEach { (key, value) -> if (!key.equals("Host", ignoreCase = true)) reqBuilder.addHeader(key, value) }
                        reqBuilder.header("User-Agent", "Mozilla/5.0")
                        val response = okHttpClient.newCall(reqBuilder.build()).execute()
                        val headers = mutableMapOf<String, String>()
                        response.headers.forEach { (key, value) -> headers[key] = value }
                        headers["Access-Control-Allow-Origin"] = "*"
                        return WebResourceResponse("application/json", "UTF-8", response.code, if (response.message.isEmpty()) "OK" else response.message, headers, response.body?.byteStream())
                    } catch (e: Exception) { e.printStackTrace() }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        
        webView.addJavascriptInterface(JSBridge(), "FeelCoolTV")
        layout.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        
        splashView = ImageView(this)
        splashView.setBackgroundColor(Color.TRANSPARENT)
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
        if (isFullScreenIptv) {
            iptvExitCount++
            if (iptvExitCount >= 3) {
                isFullScreenIptv = false
                webView.visibility = View.VISIBLE
                webView.requestFocus() 
                player?.volume = 0f    
                backPressCount = 0 
            } else {
                Toast.makeText(this, "再按 ${3 - iptvExitCount} 次退出全屏", Toast.LENGTH_SHORT).show()
            }
            return
        }

        webView.evaluateJavascript("javascript:window.handleHardwareBack()") { result ->
            if (result == "\"true\"" || result == "true") {
                backPressCount = 0
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime > 2000) {
                    backPressCount = 1
                    lastBackPressTime = currentTime
                    Toast.makeText(this, "再按两次返回键退出", Toast.LENGTH_SHORT).show()
                } else {
                    backPressCount++
                    if (backPressCount >= 3) {
                        super.onBackPressed()
                    } else {
                        Toast.makeText(this, "再按 ${3 - backPressCount} 次返回键退出", Toast.LENGTH_SHORT).show()
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
                runOnUiThread {
                    isFullScreenIptv = true
                    iptvExitCount = 0
                    webView.visibility = View.GONE 
                    
                    if (player?.currentMediaItem?.localConfiguration?.uri?.toString() == url && player?.isPlaying == true) {
                        player?.volume = 1f
                    } else {
                        if (player == null) setupPlayer()
                        player?.volume = 1f
                        player?.setMediaItem(MediaItem.fromUri(url))
                        player?.prepare()
                        player?.play()
                    }
                }
            } else if (action.startsWith("app:")) {
                stopBackgroundVideo()
                val pkg = action.substring(4)
                var launchIntent = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
                
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
                        Toast.makeText(this@MainActivity, "应用被系统限制启动", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "未找到应用，请确认是否安装", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun hideSplash() {
            runOnUiThread {
                splashView.animate().alpha(0f).setDuration(300).withEndAction {
                    splashView.visibility = View.GONE
                    splashView.setImageDrawable(null)
                }
            }
        }

        private fun setupPlayer() {
            player = ExoPlayer.Builder(this@MainActivity).build().apply {
                setVideoTextureView(textureView)
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        webView.setBackgroundColor(Color.TRANSPARENT)
                        webView.evaluateJavascript("javascript:if(window.onBackgroundVideoStarted) window.onBackgroundVideoStarted();", null)
                    }
                })
            }
        }

        @JavascriptInterface
        fun playBackgroundVideo(url: String) {
            runOnUiThread {
                if (player == null) setupPlayer()
                player?.volume = 0f 
                player?.setMediaItem(MediaItem.fromUri(url))
                player?.prepare()
                player?.play()
            }
        }

        @JavascriptInterface
        fun stopBackgroundVideo() {
            runOnUiThread {
                webView.setBackgroundColor(Color.BLACK)
                player?.stop()
                player?.clearMediaItems()
            }
        }

        @JavascriptInterface
        fun cacheCurrentFrame(streamUrl: String) {
            runOnUiThread {
                try {
                    val bitmap = textureView.bitmap ?: return@runOnUiThread
                    val scaled = Bitmap.createScaledBitmap(bitmap, 640, 360, true)
                    val file = File(cacheDir, "frame_${streamUrl.hashCode()}.jpg")
                    val out = FileOutputStream(file)
                    scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
                    out.flush()
                    out.close()
                    
                    val path = "file://${file.absolutePath}"
                    webView.evaluateJavascript("javascript:if(window.onFrameCached) window.onFrameCached('$streamUrl', '$path');", null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
