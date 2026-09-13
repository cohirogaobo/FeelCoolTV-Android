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
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100 
        }
        
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
