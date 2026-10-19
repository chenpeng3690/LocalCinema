package com.localcinema.app.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.DefaultLoadControl
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

        val loadControl = DefaultLoadControl.Builder()
            // 缓冲 50 秒：够快进 30 秒命中，又不至于在 4K 上吃掉太多内存
            .setBufferDurationsMs(
                /* minBufferMs = */ 50_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            // 保留已播过的 30 秒：快退不必重新读盘。默认值是 0，也就是播完全丢
            .setBackBuffer(/* backBufferMs = */ 30_000, /* retainBackBufferFromKeyframe = */ true)
            // 按字节封顶：1080p 能缓冲上百秒，4K 高码率则自动压缩，避免后台被系统杀掉
            .setTargetBufferBytes(96 * 1024 * 1024)
            .build()

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
            .setLoadControl(loadControl)
            .build()

        // 精确 seek 要从上一个关键帧一路解到目标帧（实测 200~700ms），
        // 放宽 2 秒容差后可以直接落到附近的关键帧，解一帧即可出画面。
        // 2 秒误差对「快进 30 秒」「拖进度条」都察觉不到。
        // 注意单位是「微秒」：2_000_000us = 2 秒。写成 2000 只是 2 毫秒，等于没放宽
        player.setSeekParameters(SeekParameters(2_000_000L, 2_000_000L))

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
