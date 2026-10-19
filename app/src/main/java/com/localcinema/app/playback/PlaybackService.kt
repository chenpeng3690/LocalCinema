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

    override fun onCreate() {
        super.onCreate()

        val loadControl = DefaultLoadControl.Builder()
            /**
             * 前向缓冲压到 20 秒（默认 50 秒）。
             *
             * 目的：让「快进 30 秒」的目标点落在缓冲之外，从而触发重新加载。
             * 缓冲内跳转不走 SeekParameters，只能从最近的关键帧一路解码过来（实测 350~660ms）；
             * 超出缓冲则按 2 秒容差直接请求关键帧（实测 80~150ms）。
             *
             * 代价：缓冲更薄，慢盘或高码率下更容易卡。若出现卡顿请改回 50_000。
             */
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 20_000,
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
        // 注意单位是「微秒」：5_000_000us = 5 秒。写成 5000 只是 5 毫秒，等于没放宽。
        // 实测该片源 GOP ≈ 10 秒，2 秒容差只能覆盖 ±2s（命中率约 40%，其余仍要解码 200+ 帧）；
        // 5 秒容差正好覆盖半个 GOP，理论命中率接近 100%。
        // 代价：落点最多偏离 5 秒，即「快进 30 秒」可能只前进 25 秒或前进 35 秒。
        player.setSeekParameters(SeekParameters(5_000_000L, 5_000_000L))

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
