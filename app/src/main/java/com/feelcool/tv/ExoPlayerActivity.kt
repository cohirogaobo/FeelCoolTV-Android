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
        
        val playerView = PlayerView(this)
        playerView.setBackgroundColor(Color.BLACK)
        // 🔥 修复：强制禁用所有的播放控件（暂停、快进、时间轴全部隐藏）
        playerView.useController = false 
        
        setContentView(playerView)

        val videoUrl = intent.getStringExtra("VIDEO_URL") ?: return
        
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        
        val mediaItem = MediaItem.fromUri(videoUrl)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play() 
    }

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
