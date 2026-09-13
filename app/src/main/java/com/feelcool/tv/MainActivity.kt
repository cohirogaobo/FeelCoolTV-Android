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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var splashView: ImageView
    private lateinit var webView: WebView
    
    // 用于3连击退出的计数器
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

        webView.loadUrl("https://cooltv.netlify.app")
    }

    // 🔥 核心修改：接管系统返回键
    override fun onBackPressed() {
        // 向网页发射一个信号，询问网页当前状态
        webView.evaluateJavascript("javascript:window.handleHardwareBack()") { result ->
            // 如果网页返回 "true"，说明它自己处理了返回（比如关闭了子菜单，或退回了侧边栏）
            if (result == "\"true\"" || result == "true") {
                backPressCount = 0 // 计数器清零
            } else {
                // 网页没东西可退了，触发 3 次防误触机制
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime > 2000) {
                    backPressCount = 1
                    lastBackPressTime = currentTime
                    Toast.makeText(this, "再按两次返回键退出很酷TV", Toast.LENGTH_SHORT).show()
                } else {
                    backPressCount++
                    if (backPressCount >= 3) {
                        super.onBackPressed() // 真正退出应用
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
                val intent = Intent(this@MainActivity, ExoPlayerActivity::class.java)
                intent.putExtra("VIDEO_URL", url)
                startActivity(intent)
            } else if (action.startsWith("app:")) {
                val pkg = action.substring(4)
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    runOnUiThread { Toast.makeText(this@MainActivity, "未找到该应用", Toast.LENGTH_SHORT).show() }
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
