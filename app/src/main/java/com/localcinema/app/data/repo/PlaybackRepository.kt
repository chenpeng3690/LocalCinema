package com.localcinema.app.data.repo

import com.localcinema.app.data.db.PlaybackStateDao
import com.localcinema.app.data.db.PlaybackStateEntity
import com.localcinema.app.data.db.VideoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 续播位置计算（纯函数，便于单测）。
 *
 * 规则：
 * 1. 上次位置 <= 回退量 → 从头播，避免算出负数
 * 2. 距结尾不足 10 秒 → 视为已看完，从头播（否则会卡在片尾字幕里反复进出）
 * 3. 否则从「上次位置 - 5 秒」开始，帮用户回忆剧情
 */
object ResumePolicy {
    const val BACK_MS = 5_000L
    const val FINISH_THRESHOLD_MS = 10_000L

    fun resolve(
        positionMs: Long,
        durationMs: Long,
        backMs: Long = BACK_MS,
        finishMs: Long = FINISH_THRESHOLD_MS
    ): Long {
        if (positionMs <= 0L) return 0L
        if (durationMs > 0L && durationMs - positionMs <= finishMs) return 0L
        val target = positionMs - backMs
        return if (target < 0L) 0L else target
    }
}

@Singleton
class PlaybackRepository @Inject constructor(
    private val playbackDao: PlaybackStateDao
) {

    /** videoId → 播放状态，供列表显示进度条 */
    fun observeStates(): Flow<Map<String, PlaybackStateEntity>> =
        playbackDao.observeAll().map { list -> list.associateBy { it.videoId } }

    suspend fun resumePosition(video: VideoEntity): Long {
        val state = playbackDao.get(video.id) ?: return 0L
        val duration = if (video.durationMs > 0) video.durationMs else state.durationMs
        return ResumePolicy.resolve(state.positionMs, duration)
    }

    suspend fun save(video: VideoEntity, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val existing = playbackDao.get(video.id)
        val finished = durationMs - positionMs <= ResumePolicy.FINISH_THRESHOLD_MS
        playbackDao.upsert(
            PlaybackStateEntity(
                videoId = video.id,
                positionMs = positionMs.coerceIn(0L, durationMs),
                durationMs = durationMs,
                finished = finished,
                playCount = existing?.playCount ?: 0,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markStarted(video: VideoEntity) {
        val existing = playbackDao.get(video.id)
        playbackDao.upsert(
            PlaybackStateEntity(
                videoId = video.id,
                positionMs = existing?.positionMs ?: 0L,
                durationMs = if (video.durationMs > 0) video.durationMs else (existing?.durationMs ?: 0L),
                finished = false,
                playCount = (existing?.playCount ?: 0) + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 手动标记已看完：进度推到结尾，列表显示 ✓ */
    suspend fun markFinished(video: VideoEntity) {
        val existing = playbackDao.get(video.id)
        val duration = if (video.durationMs > 0) video.durationMs else (existing?.durationMs ?: 0L)
        if (duration <= 0L) return
        playbackDao.upsert(
            PlaybackStateEntity(
                videoId = video.id,
                positionMs = duration,
                durationMs = duration,
                finished = true,
                playCount = existing?.playCount ?: 0,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear(videoId: String) = playbackDao.delete(videoId)
}
