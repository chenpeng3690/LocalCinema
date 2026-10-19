package com.localcinema.app.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.localcinema.app.ui.theme.LocalCinemaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 播放页使用独立 Activity：
 * 横竖屏切换时不重建 Compose 树，避免播放器被销毁重来。
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemBars()

        setContent {
            LocalCinemaTheme {
                PlayerScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveNow()
        viewModel.player.pause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        fun intent(context: Context, albumId: String, videoId: String): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(PlayerViewModel.KEY_ALBUM_ID, albumId)
                putExtra(PlayerViewModel.KEY_VIDEO_ID, videoId)
            }
    }
}
