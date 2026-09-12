package com.feelcool.tv

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class ExoPlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建全屏的原生播放视图
        val playerView = PlayerView(this)
        playerView.setBackgroundColor(Color.BLACK)
        setContentView(playerView)

        val videoUrl = intent.getStringExtra("VIDEO_URL") ?: return
        
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        
        val mediaItem = MediaItem.fromUri(videoUrl)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play() // 自动播放
    }

    // 按遥控器返回键时，销毁播放器并返回 HTML 界面
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
