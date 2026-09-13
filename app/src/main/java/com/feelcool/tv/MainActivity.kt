package com.feelcool.tv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webView = WebView(this)
        webView.setBackgroundColor(Color.BLACK)
        setContentView(webView)

        // 彻底解决虚焦、卡顿和比例放大问题的核心黑魔法
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            
            // 强制 1:1 物理像素渲染，拒绝系统二次拉伸拉黑
            useWideViewPort = true
            loadWithOverviewMode = true
            // 锁定字体缩放比例，防止电视系统的“大字号”设置干扰 UI 布局
            textZoom = 100 
        }
        
        // 强制开启 GPU 硬件加速图层，让动画帧率翻倍，边缘更加锐利
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(JSBridge(), "FeelCoolTV")
        
        webView.loadUrl("https://cooltv.netlify.app")
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
                }
            }
        }
    }
}
    inner class JSBridge {
        @JavascriptInterface
        fun executeAction(action: String, title: String) {
            if (action.startsWith("iptv:")) {
                // 收到 iptv: 指令，启动原生播放器
                val url = action.substring(5)
                val intent = Intent(this@MainActivity, ExoPlayerActivity::class.java)
                intent.putExtra("VIDEO_URL", url)
                startActivity(intent)
            } else if (action.startsWith("app:")) {
                // 收到 app: 指令，唤起第三方应用 (如 VidHub)
                val pkg = action.substring(4)
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
        }
    }
}
