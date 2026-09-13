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

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            
            // 强制 1:1 物理像素渲染，解决电视强制拉伸导致的虚焦
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100 
        }
        
        // 开启硬件加速图层，大幅提升渲染性能与清晰度
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.webViewClient = WebViewClient()
        
        // 注册桥接层，让 JS 可以调用下面的 JSBridge
        webView.addJavascriptInterface(JSBridge(), "FeelCoolTV")
        
        // 指向你的 Netlify 线上地址
        webView.loadUrl("https://cooltv.netlify.app")
    }

    // 注意这里：JSBridge 必须在 MainActivity 的大括号内部
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
}        webView.webViewClient = WebViewClient()
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
