package com.feelcool.tv

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var rootLayout: FrameLayout
    private lateinit var splashView: ImageView
    private lateinit var webView: WebView
    
    private var player: ExoPlayer? = null
    private lateinit var textureView: TextureView
    
    private var backPressCount = 0
    private var lastBackPressTime = 0L

    private var isFullScreenIptv = false
    private var iptvExitCount = 0
    
    private var customToast: Toast? = null
    private var isSplashHidden = false

    private var currentVideoHeaders: Map<String, String> = emptyMap()
    
    // 嗅探器全局状态
    private var snifferWebView: WebView? = null
    private var isSniffFound = false
    private var currentSniffUrl = ""
    private var currentPcUserAgent = ""
    private var sniffTimeoutRunnable: Runnable? = null
    private var sniffFoundCallback: ((String, String, String, String) -> Unit)? = null

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

    private fun showPureNativeToast(message: String) {
        runOnUiThread {
            customToast?.cancel()
            val toast = Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT)
            val textView = TextView(this@MainActivity).apply {
                text = message
                setTextColor(Color.WHITE)
                textSize = 15f
                letterSpacing = 0.06f
                gravity = android.view.Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(50, 14, 50, 16) 
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#D9000000")) 
                    cornerRadius = 50f
                }
            }
            @Suppress("DEPRECATION")
            toast.view = textView
            toast.show()
            customToast = toast
        }
    }

    private fun executeHideSplash() {
        if (isSplashHidden) return
        isSplashHidden = true
        runOnUiThread {
            splashView.animate().alpha(0f).setDuration(300).withEndAction {
                splashView.visibility = View.GONE
                splashView.setImageDrawable(null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        rootLayout = FrameLayout(this)
        rootLayout.setBackgroundColor(Color.BLACK)
        
        textureView = TextureView(this)
        rootLayout.addView(textureView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

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
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Handler(Looper.getMainLooper()).postDelayed({
                    executeHideSplash()
                }, 800)
            }

            // 统一接管前端 HTML 的请求，解决跨域及国内网络连通性问题
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                
                if (url.contains("api.themoviedb.org") || url.contains("raw.githubusercontent.com")) {
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
                            if (!key.equals("Host", ignoreCase = true) && !key.equals("Origin", ignoreCase = true)) {
                                reqBuilder.addHeader(key, value) 
                            }
                        }
                        reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        
                        val response = okHttpClient.newCall(reqBuilder.build()).execute()
                        
                        val headers = mutableMapOf<String, String>()
                        response.headers.forEach { (key, value) -> headers[key] = value }
                        headers["Access-Control-Allow-Origin"] = "*"
                        headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
                        headers["Access-Control-Allow-Headers"] = "*"
                        
                        val contentType = response.header("Content-Type", "application/json") ?: "application/json"
                        val mimeType = if (contentType.contains(";")) contentType.split(";")[0].trim() else contentType

                        return WebResourceResponse(
                            mimeType, 
                            "UTF-8", 
                            response.code, 
                            if (response.message.isEmpty()) "OK" else response.message, 
                            headers, 
                            response.body?.byteStream()
                        )
                    } catch (e: Exception) { 
                        e.printStackTrace() 
                        val corsHeaders = mutableMapOf("Access-Control-Allow-Origin" to "*")
                        return WebResourceResponse("application/json", "UTF-8", 500, "Server Error", corsHeaders, ByteArrayInputStream("{}".toByteArray()))
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        
        webView.addJavascriptInterface(JSBridge(), "FeelCoolTV")
        rootLayout.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        
        splashView = ImageView(this)
        splashView.setBackgroundColor(Color.TRANSPARENT)
        val splashResId = resources.getIdentifier("splash", "drawable", packageName)
        if (splashResId != 0) {
            splashView.setImageResource(splashResId)
            splashView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
        rootLayout.addView(splashView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        
        setContentView(rootLayout)
        webView.loadUrl("file:///android_asset/index.html")

        antiSleepHandler.postDelayed(antiSleepRunnable, 60 * 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        antiSleepHandler.removeCallbacksAndMessages(null)
        player?.release()
        snifferWebView?.destroy()
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
                showPureNativeToast("再按 ${3 - iptvExitCount} 次退出全屏")
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
                    showPureNativeToast("再按两次返回键退出")
                } else {
                    backPressCount++
                    if (backPressCount >= 3) {
                        super.onBackPressed()
                    } else {
                        showPureNativeToast("再按 ${3 - backPressCount} 次返回键退出")
                    }
                }
            }
        }
    }

    private fun setupPlayer() {
        val dataSourceFactory = DataSource.Factory {
            val factory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
            
            val defaultHeaders = mutableMapOf<String, String>()
            currentVideoHeaders.forEach { (key, value) ->
                if (value.isNotEmpty()) {
                    if (key.equals("User-Agent", ignoreCase = true)) {
                        factory.setUserAgent(value)
                    } else {
                        defaultHeaders[key] = value
                    }
                }
            }
            factory.setDefaultRequestProperties(defaultHeaders)
            factory.createDataSource()
        }

        player = ExoPlayer.Builder(this@MainActivity)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setVideoTextureView(textureView)
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        webView.setBackgroundColor(Color.TRANSPARENT)
                        webView.evaluateJavascript("javascript:if(window.onBackgroundVideoStarted) window.onBackgroundVideoStarted();", null)
                    }
                })
            }
    }

    private fun startExoPlayer(url: String, headers: Map<String, String>, targetVolume: Float) {
        currentVideoHeaders = headers
        if (player == null) setupPlayer()
        player?.volume = targetVolume
        player?.setMediaItem(MediaItem.fromUri(url))
        player?.prepare()
        player?.play()
    }

    // --- 线程安全的收网逻辑 ---
    private fun handleM3u8Found(m3u8Url: String) {
        if (!isSniffFound) {
            isSniffFound = true
            sniffTimeoutRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
            
            val cookieManager = android.webkit.CookieManager.getInstance()
            val cookie = cookieManager.getCookie(currentSniffUrl) ?: ""
            val uri = android.net.Uri.parse(currentSniffUrl)
            val referer = "${uri.scheme}://${uri.host}/"
            
            runOnUiThread {
                sniffFoundCallback?.invoke(m3u8Url, cookie, currentPcUserAgent, referer)
                
                // 卸磨杀驴，销毁幽灵容器
                snifferWebView?.let {
                    it.stopLoading()
                    rootLayout.removeView(it)
                    it.destroy()
                }
                snifferWebView = null
            }
        }
    }

    // --- 实体化物理级嗅探器 ---
    private fun sniffM3u8(targetUrl: String, onFound: (String, String, String, String) -> Unit) {
        runOnUiThread {
            snifferWebView?.let {
                rootLayout.removeView(it)
                it.destroy()
            }
            
            isSniffFound = false
            currentSniffUrl = targetUrl
            sniffFoundCallback = onFound
            
            val sniffer = WebView(this@MainActivity)
            snifferWebView = sniffer
            
            // 伪装 1：强行塞满全屏尺寸，完美骗过 IntersectionObserver 检测。
            val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            rootLayout.addView(sniffer, 0, params) 
            // 绝对不可见：设置为 1% 透明度
            sniffer.alpha = 0.01f
            
            // 伪装 2：采用高权重的 Windows Chrome (防 NTV Live FairPlay 报错)
            currentPcUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
            sniffer.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = currentPcUserAgent
                mediaPlaybackRequiresUserGesture = false 
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            val timeoutRunnable = Runnable {
                if (!isSniffFound) {
                    isSniffFound = true
                    showPureNativeToast("解析超时：该频道防盗链极强")
                    sniffer.stopLoading()
                    rootLayout.removeView(sniffer)
                    sniffer.destroy()
                    snifferWebView = null
                }
            }
            sniffTimeoutRunnable = timeoutRunnable
            // 放宽至 20 秒，给 API 数据交换留足时间
            Handler(Looper.getMainLooper()).postDelayed(timeoutRunnable, 20000) 
            
            sniffer.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 伪装 3：降维打击，不仅暴力点击，还劫持底层的 fetch 和 XHR 窃取 JSON 返回包
                    view?.evaluateJavascript("""
                        (function() {
                            // 劫持 Fetch
                            const originalFetch = window.fetch;
                            window.fetch = async function() {
                                var reqUrl = (arguments[0] instanceof Request) ? arguments[0].url : arguments[0];
                                
                                if(reqUrl && typeof reqUrl === 'string' && reqUrl.indexOf('playback.api.streaks.jp') !== -1) {
                                    var response = await originalFetch.apply(this, arguments);
                                    var clone = response.clone();
                                    clone.json().then(function(data) {
                                        var streamUrl = data.url || (data.stream && data.stream.url) || (data.session && data.session.stream && data.session.stream.url);
                                        if(streamUrl && (streamUrl.indexOf('.m3u8') !== -1)) {
                                            window.FeelCoolTV.onM3u8Found(streamUrl);
                                        }
                                    }).catch(function(e){});
                                    return response;
                                }
                                
                                if(reqUrl && typeof reqUrl === 'string' && (reqUrl.indexOf('.m3u8') !== -1 || reqUrl.indexOf('.mpd') !== -1)) {
                                    window.FeelCoolTV.onM3u8Found(reqUrl);
                                }
                                return originalFetch.apply(this, arguments);
                            };

                            // 劫持 XHR
                            const originalOpen = XMLHttpRequest.prototype.open;
                            XMLHttpRequest.prototype.open = function(method, reqUrl) {
                                if(reqUrl && typeof reqUrl === 'string' && (reqUrl.indexOf('.m3u8') !== -1 || reqUrl.indexOf('.mpd') !== -1)) {
                                    window.FeelCoolTV.onM3u8Found(reqUrl);
                                }
                                this.addEventListener('load', function() {
                                    if(reqUrl && typeof reqUrl === 'string' && reqUrl.indexOf('playback.api.streaks.jp') !== -1) {
                                        try {
                                            var data = JSON.parse(this.responseText);
                                            var streamUrl = data.url || (data.stream && data.stream.url);
                                            if(streamUrl) window.FeelCoolTV.onM3u8Found(streamUrl);
                                        } catch(e){}
                                    }
                                });
                                originalOpen.apply(this, arguments);
                            };

                            // 暴力人类模拟器
                            var attempt = 0;
                            var evOpts = {bubbles:true, cancelable:true, view: window};
                            var forcePlay = setInterval(function() {
                                attempt++;
                                var v = document.querySelector('video');
                                if(v) { v.muted = true; var p = v.play(); if(p) p.catch(function(){}); }
                                
                                var btns = document.querySelectorAll('button, .play-button, .player-block, .handle-play-button');
                                btns.forEach(function(b) { 
                                    b.click(); // 直接调用原生 click 处理 React 事件
                                    b.dispatchEvent(new MouseEvent('mousedown', evOpts));
                                    b.dispatchEvent(new MouseEvent('mouseup', evOpts));
                                });

                                if(attempt > 40) clearInterval(forcePlay);
                            }, 500);
                        })();
                    """.trimIndent(), null)
                }

                // 兜底拦截：如果视频没走 fetch 而是直接挂在了 DOM 上
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val reqUrl = request?.url.toString()
                    if (!isSniffFound && (reqUrl.contains(".m3u8") || reqUrl.contains(".mpd"))) {
                        runOnUiThread { handleM3u8Found(reqUrl) }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            sniffer.loadUrl(targetUrl)
        }
    }

    inner class JSBridge {
        @JavascriptInterface
        fun onM3u8Found(url: String) {
            runOnUiThread { handleM3u8Found(url) }
        }

        @JavascriptInterface
        fun executeAction(action: String, title: String) {
            val isIptv = action.startsWith("iptv:")
            val isSniff = action.startsWith("sniff:")

            if (isIptv || isSniff) {
                runOnUiThread {
                    isFullScreenIptv = true
                    iptvExitCount = 0
                    webView.visibility = View.GONE 
                    
                    if (isSniff) {
                        val sniffUrl = action.substring(6)
                        showPureNativeToast("正在突破防盗链并解析源...")
                        sniffM3u8(sniffUrl) { m3u8Url, cookie, userAgent, referer ->
                            runOnUiThread {
                                val headers = mapOf(
                                    "User-Agent" to userAgent, 
                                    "Referer" to referer, 
                                    "Origin" to "https://news.ntv.co.jp",
                                    "Cookie" to cookie
                                )
                                startExoPlayer(m3u8Url, headers, 1f)
                            }
                        }
                    } else {
                        val url = action.substring(5)
                        if (player?.currentMediaItem?.localConfiguration?.uri?.toString() == url && player?.isPlaying == true) {
                            player?.volume = 1f
                        } else {
                            startExoPlayer(url, emptyMap(), 1f)
                        }
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
                        showPureNativeToast("应用被系统限制启动")
                    }
                } else {
                    showPureNativeToast("未找到应用，请确认是否安装")
                }
            }
        }

        @JavascriptInterface
        fun hideSplash() {
            executeHideSplash()
        }

        @JavascriptInterface
        fun playBackgroundVideo(action: String) {
            runOnUiThread {
                if (action.startsWith("sniff:")) {
                    val sniffUrl = action.substring(6)
                    sniffM3u8(sniffUrl) { m3u8Url, cookie, userAgent, referer ->
                        runOnUiThread {
                            val headers = mapOf(
                                "User-Agent" to userAgent, 
                                "Referer" to referer, 
                                "Origin" to "https://news.ntv.co.jp",
                                "Cookie" to cookie
                            )
                            startExoPlayer(m3u8Url, headers, 0f)
                        }
                    }
                } else {
                    startExoPlayer(action, emptyMap(), 0f)
                }
            }
        }

        @JavascriptInterface
        fun stopBackgroundVideo() {
            runOnUiThread {
                webView.setBackgroundColor(Color.BLACK)
                player?.stop()
                player?.clearMediaItems()
                snifferWebView?.let {
                    it.stopLoading()
                    rootLayout.removeView(it)
                    it.destroy()
                }
                snifferWebView = null
            }
        }

        @JavascriptInterface
        fun cacheCurrentFrame(streamActionUrl: String) {
            runOnUiThread {
                try {
                    val bitmap = textureView.bitmap ?: return@runOnUiThread
                    val scaled = Bitmap.createScaledBitmap(bitmap, 640, 360, true)
                    val file = File(cacheDir, "frame_${streamActionUrl.hashCode()}.jpg")
                    val out = FileOutputStream(file)
                    scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
                    out.flush()
                    out.close()
                    
                    val path = "file://${file.absolutePath}"
                    webView.evaluateJavascript("javascript:if(window.onFrameCached) window.onFrameCached('$streamActionUrl', '$path');", null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
