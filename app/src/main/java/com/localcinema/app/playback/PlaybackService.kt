package com.localcinema.app.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * 播放器宿主。
 *
 * 播放器必须活在 Service 里，而不是 Activity/ViewModel 里：
 * 退到后台、锁屏、切到别的 App 时，Activity 可能被系统回收，
 * 播放器跟着没了就谈不上后台播放。Service 化之后，播放与界面解耦。
 *
 * 通知栏由 MediaSessionService 自动生成（media3 默认 provider），
 * 包含播放/暂停、上一集/下一集、进度条与封面。
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /** 打开后 Logcat 过滤 `tag:SeekPerf` 可看到每次 seek 的耗时拆解 */
    private companion object {
        const val ENABLE_SEEK_DIAGNOSTICS = true
    }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // 拔耳机 / 蓝牙断开时自动暂停
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 排查「快进后要等一下」用：定位瓶颈在读文件还是解码。
        // 平时关掉，否则每次加载都会刷日志。
        if (ENABLE_SEEK_DIAGNOSTICS) {
            player.addAnalyticsListener(SeekDiagnostics())
        }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /** 用户从最近任务里划掉 App：没在播放就收工，避免常驻通知 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
