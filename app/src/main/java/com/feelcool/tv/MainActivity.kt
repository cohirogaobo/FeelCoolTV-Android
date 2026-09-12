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
        webView.setBackgroundColor(Color.BLACK) // 网页没出来前用纯黑垫底
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        
        // 关键：把遥控器的焦点权限全部交给网页
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        
        webView.webViewClient = WebViewClient()
        
        // 注册 JS Bridge，名字叫 FeelCoolTV
        webView.addJavascriptInterface(JSBridge(), "FeelCoolTV")
        
        // 🔥 已经替换为你托管在 Netlify 的公网线上地址
        webView.loadUrl("https://cooltv.netlify.app")
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
