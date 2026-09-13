package com.feelcool.tv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var splashView: ImageView
    private lateinit var webView: WebView
    
    private var backPressCount = 0
    private var lastBackPressTime = 0L

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
        }
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.webViewClient = WebViewClient()
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

        // 🔥 修改点：不再请求 Netlify，直接从本地 APK 内部极速加载 HTML
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
                
                // 🔥 修改点：针对小米等系统隐藏 Launcher 的暴力唤醒策略
                var launchIntent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                
                if (launchIntent == null) {
                    launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                }
                
                // 如果常规意图和电视意图都拿不到（比如米家或信号源），采用强制隐式意图匹配
                if (launchIntent == null) {
                    launchIntent = Intent(Intent.ACTION_MAIN)
                    launchIntent.setPackage(pkg)
                    launchIntent.addCategory(Intent.CATEGORY_DEFAULT)
                    launchIntent.addCategory(Intent.CATEGORY_INFO)
                    // 验证该隐式意图是否真的能解析到应用，防崩溃
                    if (launchIntent.resolveActivity(packageManager) == null) {
                        launchIntent = null
                    }
                }
                
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    runOnUiThread { 
                        webView.evaluateJavascript("javascript:showToast('未找到应用或该应用禁止外部唤起')", null) 
                    }
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
