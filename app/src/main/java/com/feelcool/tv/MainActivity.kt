package com.feelcool.tv

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
    
    // ExoPlayer 资源（用于常规 IPTV）
    private var player: ExoPlayer? = null
    private lateinit var textureView: TextureView
    
    // WebLive 资源（用于将计就计的网页全屏直播）
    private var liveWebView: WebView? = null
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var loadingOverlay: TextView
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    
    private var backPressCount = 0
    private var lastBackPressTime = 0L

    private var isFullScreenIptv = false
    private var iptvExitCount = 0
    
    private var customToast: Toast? = null
    private var isSplashHidden = false

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
                Handler(Looper.getMainLooper()).postDelayed({ executeHideSplash() }, 800)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                if (url.contains("api.themoviedb.org")) {
                    if (request?.method.equals("OPTIONS", ignoreCase = true)) {
                        val corsHeaders = mutableMapOf("Access-Control-Allow-Origin" to "*", "Access-Control-Allow-Methods" to "GET, POST, OPTIONS", "Access-Control-Allow-Headers" to "*")
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
        rootLayout.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        
        // --- 网页全屏播放器的专属遮罩层与容器 ---
        fullscreenContainer = FrameLayout(this)
        fullscreenContainer.setBackgroundColor(Color.BLACK)
        fullscreenContainer.visibility = View.GONE
        rootLayout.addView(fullscreenContainer, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        loadingOverlay = TextView(this).apply {
            text = "正在安全接入实况画面..."
            setTextColor(Color.WHITE)
            textSize = 20f
            letterSpacing = 0.1f
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        rootLayout.addView(loadingOverlay, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        // ------------------------------------
        
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
        stopWebLive()
    }

    override fun onBackPressed() {
        if (isFullScreenIptv) {
            iptvExitCount++
            if (iptvExitCount >= 3) {
                isFullScreenIptv = false
                webView.visibility = View.VISIBLE
                webView.requestFocus() 
                player?.volume = 0f    
                stopWebLive() // 退出时销毁后台全屏网页
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
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
                .createDataSource()
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

    private fun startExoPlayer(url: String, targetVolume: Float) {
        if (player == null) setupPlayer()
        player?.volume = targetVolume
        player?.setMediaItem(MediaItem.fromUri(url))
        player?.prepare()
        player?.play()
    }

    private fun stopWebLive() {
        runOnUiThread {
            liveWebView?.let {
                it.stopLoading()
                rootLayout.removeView(it)
                it.destroy()
            }
            liveWebView = null
            
            fullscreenContainer.removeAllViews()
            fullscreenContainer.visibility = View.GONE
            loadingOverlay.visibility = View.GONE
            
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
        }
    }

    // 核心降维打击：将计就计，直接用隐藏的 WebView 加载网页，注入 CSS 把播放器撑满全屏
    private fun startWebLive(targetUrl: String, isBackground: Boolean, actionStr: String) {
        runOnUiThread {
            stopWebLive()

            val wv = WebView(this@MainActivity)
            liveWebView = wv
            
            // 垫在底部的索引 0 位置
            rootLayout.addView(wv, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            
            if (!isBackground) {
                loadingOverlay.visibility = View.VISIBLE
                loadingOverlay.alpha = 1f
            }

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false 
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }
            
            wv.webChromeClient = object : WebChromeClient() {
                // 如果网页播放器自带了全屏 API，完美接管
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    fullscreenContainer.addView(view)
                    fullscreenContainer.visibility = View.VISIBLE
                    loadingOverlay.visibility = View.GONE
                    customViewCallback = callback
                }
                override fun onHideCustomView() {
                    super.onHideCustomView()
                    fullscreenContainer.removeAllViews()
                    fullscreenContainer.visibility = View.GONE
                    customViewCallback?.onCustomViewHidden()
                }
            }

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val mutedStr = if (isBackground) "true" else "false"
                    val js = """
                        (function() {
                            // 1. CSS 手术刀：隐藏网页上的所有花里胡哨，把播放器绝对定位撑满全屏
                            var style = document.createElement('style');
                            style.innerHTML = `
                                header, footer, .global-navigation, .sideContents, 
                                .liveContent-header, .liveContent-subtext, .liveContent-content,
                                .publicity-contents, .ranking-list, .sponsoredArticles, #taboola-widget, iframe { 
                                    display: none !important; opacity: 0 !important; pointer-events: none !important;
                                }
                                body, html, #appMountPoint, .mainView, .contentsWrapper, 
                                .pcLayoutWrapper, .mainContents, .articleDetailWrapper, 
                                .liveContent, .liveContent-body {
                                    margin: 0 !important; padding: 0 !important;
                                    width: 100vw !important; height: 100vh !important;
                                    max-width: none !important; min-width: 0 !important;
                                    background: #000 !important; overflow: hidden !important;
                                }
                                .player-block {
                                    position: fixed !important; top: 0 !important; left: 0 !important;
                                    width: 100vw !important; height: 100vh !important;
                                    z-index: 999999 !important; background: #000 !important;
                                }
                                video {
                                    width: 100vw !important; height: 100vh !important;
                                    object-fit: contain !important;
                                }
                            `;
                            document.head.appendChild(style);

                            // 2. 暴力交互模拟：解开静音并疯狂点击播放按钮
                            var attempt = 0;
                            var evOpts = {bubbles:true, cancelable:true, view: window};
                            var forcePlay = setInterval(function() {
                                attempt++;
                                var v = document.querySelector('video');
                                if(v) { 
                                    v.muted = $mutedStr; 
                                    var p = v.play(); 
                                    if(p) p.catch(function(){}); 
                                }
                                
                                var playBtns = document.querySelectorAll('button.play-button, .vjs-big-play-button, .handle-play-button');
                                playBtns.forEach(function(b) { 
                                    b.click(); 
                                    b.dispatchEvent(new MouseEvent('mousedown', evOpts));
                                    b.dispatchEvent(new MouseEvent('mouseup', evOpts));
                                });
                                
                                // 如果是全屏模式，尝试点击网页自带的全屏按钮
                                if (!$mutedStr) {
                                    var fsBtns = document.querySelectorAll('button[title*="全屏"], button[aria-label*="ullscreen"], .vjs-fullscreen-control');
                                    fsBtns.forEach(function(b) { b.click(); });
                                }

                                if(attempt > 20) clearInterval(forcePlay);
                            }, 500);

                            // 3. 收尾工作：通知安卓层拉开黑幕
                            setTimeout(function() {
                                if ($mutedStr) {
                                    // 背景静音预览模式：直接通知开始，并利用 Canvas 截取网页视频帧回传！
                                    window.FeelCoolTV.onBackgroundVideoStarted();
                                    
                                    var v = document.querySelector('video');
                                    if (v && v.readyState >= 2) {
                                        var canvas = document.createElement('canvas');
                                        canvas.width = 640; canvas.height = 360;
                                        canvas.getContext('2d').drawImage(v, 0, 0, canvas.width, canvas.height);
                                        var dataUrl = canvas.toDataURL('image/jpeg', 0.7);
                                        window.FeelCoolTV.cacheWebFrame(dataUrl, '$actionStr');
                                    }
                                } else {
                                    // 全屏播放模式：通知撤掉黑色遮罩
                                    window.FeelCoolTV.onWebLiveReady();
                                }
                            }, 3500); // 留3.5秒给网页缓冲视频
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }
            }
            wv.loadUrl(targetUrl)
        }
    }

    inner class JSBridge {
        @JavascriptInterface
        fun onWebLiveReady() {
            runOnUiThread {
                loadingOverlay.animate().alpha(0f).setDuration(600).withEndAction {
                    loadingOverlay.visibility = View.GONE
                }
            }
        }
        
        @JavascriptInterface
        fun onBackgroundVideoStarted() {
            runOnUiThread {
                webView.setBackgroundColor(Color.TRANSPARENT)
                webView.evaluateJavascript("javascript:if(window.onBackgroundVideoStarted) window.onBackgroundVideoStarted();", null)
            }
        }

        // 神奇的 Canvas 回传：将网页上画出的图像转回安卓保存为缓存海报
        @JavascriptInterface
        fun cacheWebFrame(base64DataUrl: String, streamActionUrl: String) {
            runOnUiThread {
                try {
                    val base64Image = base64DataUrl.split(",")[1]
                    val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
                    
                    val file = File(cacheDir, "frame_${streamActionUrl.hashCode()}.jpg")
                    val out = FileOutputStream(file)
                    out.write(decodedBytes)
                    out.flush()
                    out.close()
                    
                    val path = "file://${file.absolutePath}"
                    webView.evaluateJavascript("javascript:if(window.onFrameCached) window.onFrameCached('$streamActionUrl', '$path');", null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
                        // 将计就计模式，直接全屏渲染网页
                        startWebLive(sniffUrl, false, action)
                    } else {
                        val url = action.substring(5)
                        if (player?.currentMediaItem?.localConfiguration?.uri?.toString() == url && player?.isPlaying == true) {
                            player?.volume = 1f
                        } else {
                            startExoPlayer(url, 1f)
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
                    // 背景静音预览模式
                    startWebLive(sniffUrl, true, action)
                } else {
                    startExoPlayer(action.substring(5), 0f)
                }
            }
        }

        @JavascriptInterface
        fun stopBackgroundVideo() {
            runOnUiThread {
                webView.setBackgroundColor(Color.BLACK)
                player?.stop()
                player?.clearMediaItems()
                stopWebLive()
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
