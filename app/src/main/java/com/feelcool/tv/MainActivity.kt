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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 创建一个层叠布局 (Frame)
        val layout = FrameLayout(this)
        
        // 2. 初始化底层网页
        val webView = WebView(this)
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
        
        // 3. 初始化顶层原生开屏画面
        splashView = ImageView(this)
        splashView.setBackgroundColor(Color.BLACK)
        // 读取你上传的 splash 图片，自动裁剪适应屏幕
        val splashResId = resources.getIdentifier("splash", "drawable", packageName)
        if (splashResId != 0) {
            splashView.setImageResource(splashResId)
            splashView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
        
        // 4. 将网页和开屏图依次盖在布局上
        layout.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        layout.addView(splashView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(layout)

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

        // 供前端 HTML 调用的“销毁开屏”接口
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
