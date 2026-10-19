package com.localcinema.app.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.common.Player
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import java.util.Locale

/**
 * 快进/快退耗时诊断。
 *
 * 只为回答一个问题：那一下卡顿，时间花在**读文件**还是**解码**上。
 * 结论决定优化方向 —— io 占比高就调缓冲/换读法，decode 占比高就换 seek 策略。
 *
 * 只在 debug 包挂载（见 PlaybackService），正式包不加，避免每次加载都刷日志。
 *
 * Logcat 过滤：`tag:SeekPerf`
 */
class SeekDiagnostics : AnalyticsListener {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var pending = false

    @Volatile
    private var seekAt = 0L

    @Volatile
    private var fromMs = 0L

    @Volatile
    private var toMs = 0L

    @Volatile
    private var ioMs = 0L

    @Volatile
    private var ioBytes = 0L

    @Volatile
    private var ioChunks = 0

    @Volatile
    private var loadStartedAt = 0L

    @Volatile
    private var sawBuffering = false

    /**
     * 兜底结算：seek 后如果数据本来就在缓冲区里，播放器不会进 BUFFERING 也不会重渲首帧，
     * 没有这个定时器就永远看不到"最快情况"的数据 —— 而那恰恰是最有参考价值的一档。
     */
    private val settle = Runnable {
        if (pending) finish("数据命中缓冲区，未进入 BUFFERING")
    }

    override fun onPositionDiscontinuity(
        eventTime: AnalyticsListener.EventTime,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return

        // 连续快点时，先把上一次没结算的输出掉，避免数据串台
        if (pending) finish("被下一次 seek 打断，数据不完整")

        pending = true
        seekAt = SystemClock.elapsedRealtime()
        fromMs = oldPosition.positionMs
        toMs = newPosition.positionMs
        ioMs = 0L
        ioBytes = 0L
        ioChunks = 0
        sawBuffering = false

        Log.d(TAG, "──── seek ${fmt(fromMs)} → ${fmt(toMs)}  (${delta(toMs - fromMs)}) ────")
        handler.removeCallbacks(settle)
        handler.postDelayed(settle, SETTLE_TIMEOUT_MS)
    }

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        state: Int
    ) {
        if (!pending) return
        when (state) {
            Player.STATE_BUFFERING -> sawBuffering = true
            Player.STATE_READY -> finish(null)
        }
    }

    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        loadStartedAt = SystemClock.elapsedRealtime()
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        if (!pending) return
        val cost = SystemClock.elapsedRealtime() - loadStartedAt
        ioMs += cost
        ioBytes += loadEventInfo.bytesLoaded
        ioChunks++
        Log.d(TAG, "  io  ${loadEventInfo.bytesLoaded / 1024}KB  ${cost}ms")
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        if (pending) finish(null)
    }

    /** 解码器名字能直接看出硬解还是软解：c2./OMX.qti/OMX.MTK 是硬解，OMX.google 是软解 */
    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializationTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        Log.i(TAG, "decoder = $decoderName  init ${initializationDurationMs}ms")
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
        val kbps = if (format.bitrate > 0) "${format.bitrate / 1000}kbps" else "码率未知"
        Log.i(TAG, "video = ${format.codecs ?: "未知编码"}  ${format.width}x${format.height}  $kbps")
    }

    private fun finish(note: String?) {
        handler.removeCallbacks(settle)
        if (!pending) return
        pending = false

        val total = SystemClock.elapsedRealtime() - seekAt
        val decode = (total - ioMs).coerceAtLeast(0L)
        val bufferState = if (sawBuffering) "重新缓冲" else "命中缓冲区"

        Log.d(
            TAG,
            "  total ${total}ms  |  io ${ioMs}ms / ${ioChunks}块 / ${ioBytes / 1024}KB" +
                "  |  decode≈${decode}ms  |  $bufferState"
        )
        note?.let { Log.d(TAG, "  note: $it") }
    }

    private fun fmt(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    private fun delta(ms: Long): String =
        String.format(Locale.US, "%+.1fs", ms / 1000f)

    private companion object {
        const val TAG = "SeekPerf"
        const val SETTLE_TIMEOUT_MS = 1_500L
    }
}
