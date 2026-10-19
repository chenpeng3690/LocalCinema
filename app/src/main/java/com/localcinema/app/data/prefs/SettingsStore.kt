package com.localcinema.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val lastAlbumId: String? = null,
    val lastVideoId: String? = null,
    val lastOpenedAt: Long = 0L,
    val startIntoLastAlbum: Boolean = true,
    val autoResumeLastVideo: Boolean = false,
    /** 续播回退量：从上次位置 -5 秒开始 */
    val resumeBackMs: Long = 5_000L,
    /** 距结尾不足 10 秒视为已看完 */
    val finishThresholdMs: Long = 10_000L,
    /** auto / hw / sw */
    val hardwareDecode: String = "auto",
    val audioPitchCorrection: Boolean = true,
    val keepScreenOn: Boolean = true,
    val backgroundPlay: Boolean = false,
    val defaultLandscape: Boolean = true,
    /** 播放速度，百分数：100 = 1.0x，范围 50–500 */
    val speedPercent: Int = 100,
    val jumpLeft: JumpButtonConfig = JumpButtonConfig.DEFAULT_LEFT,
    val jumpRight: JumpButtonConfig = JumpButtonConfig.DEFAULT_RIGHT,
    /** 播放列表模式，见 [PlaylistMode] */
    val playlistMode: String = PlaylistMode.SEQUENCE,
    /** 播完后是否弹「N 秒后播下一集」倒计时，关掉则直接跳 */
    val nextCountdown: Boolean = true,
    /** 长按画面临时加速到该倍速（百分数），0 表示关闭 */
    val longPressSpeedPercent: Int = 200,

    // ---------- 字幕样式（跨视频、跨启动保留） ----------
    val subtitleEnabled: Boolean = true,
    val subtitleFontSize: Float = 14f,
    val subtitleColorArgb: Long = 0xFFFFFFFF,
    /** 字幕延迟，毫秒，仅对外挂字幕生效 */
    val subtitleDelayMs: Long = 0L,
    val subtitleBottomPadding: Int = 48,
    /** 主题模式：dark / light / system */
    val themeMode: String = "dark",
    /** 快退/快进后是否在屏幕中央弹出「快进 30s」这类提示 */
    val jumpHintEnabled: Boolean = true
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val lastAlbumId = stringPreferencesKey("last_album_id")
        val lastVideoId = stringPreferencesKey("last_video_id")
        val lastOpenedAt = longPreferencesKey("last_opened_at")
        val startIntoLastAlbum = booleanPreferencesKey("start_into_last_album")
        val autoResumeLastVideo = booleanPreferencesKey("auto_resume_last_video")
        val hardwareDecode = stringPreferencesKey("hardware_decode")
        val audioPitchCorrection = booleanPreferencesKey("audio_pitch_correction")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val backgroundPlay = booleanPreferencesKey("background_play")
        val defaultLandscape = booleanPreferencesKey("default_landscape")
        val speedPercent = intPreferencesKey("speed_percent")
        val jumpLeft = stringPreferencesKey("jump_left")
        val jumpRight = stringPreferencesKey("jump_right")
        val playlistMode = stringPreferencesKey("playlist_mode")
        val nextCountdown = booleanPreferencesKey("next_countdown")
        val longPressSpeedPercent = intPreferencesKey("long_press_speed_percent")
        val subtitleEnabled = booleanPreferencesKey("subtitle_enabled")
        val subtitleFontSize = floatPreferencesKey("subtitle_font_size")
        val subtitleColorArgb = longPreferencesKey("subtitle_color_argb")
        val subtitleDelayMs = longPreferencesKey("subtitle_delay_ms")
        val subtitleBottomPadding = intPreferencesKey("subtitle_bottom_padding")
        val themeMode = stringPreferencesKey("theme_mode")
        val jumpHintEnabled = booleanPreferencesKey("jump_hint_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            lastAlbumId = p[Keys.lastAlbumId],
            lastVideoId = p[Keys.lastVideoId],
            lastOpenedAt = p[Keys.lastOpenedAt] ?: 0L,
            startIntoLastAlbum = p[Keys.startIntoLastAlbum] ?: true,
            autoResumeLastVideo = p[Keys.autoResumeLastVideo] ?: false,
            hardwareDecode = p[Keys.hardwareDecode] ?: "auto",
            audioPitchCorrection = p[Keys.audioPitchCorrection] ?: true,
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
            backgroundPlay = p[Keys.backgroundPlay] ?: false,
            defaultLandscape = p[Keys.defaultLandscape] ?: true,
            speedPercent = p[Keys.speedPercent] ?: 100,
            jumpLeft = JumpButtonConfig.fromJson(
                p[Keys.jumpLeft],
                JumpButtonConfig.DEFAULT_LEFT
            ),
            jumpRight = JumpButtonConfig.fromJson(
                p[Keys.jumpRight],
                JumpButtonConfig.DEFAULT_RIGHT
            ),
            playlistMode = p[Keys.playlistMode] ?: PlaylistMode.SEQUENCE,
            nextCountdown = p[Keys.nextCountdown] ?: true,
            longPressSpeedPercent = p[Keys.longPressSpeedPercent] ?: 200,
            subtitleEnabled = p[Keys.subtitleEnabled] ?: true,
            subtitleFontSize = p[Keys.subtitleFontSize] ?: 14f,
            subtitleColorArgb = p[Keys.subtitleColorArgb] ?: 0xFFFFFFFF,
            subtitleDelayMs = p[Keys.subtitleDelayMs] ?: 0L,
            subtitleBottomPadding = p[Keys.subtitleBottomPadding] ?: 48,
            themeMode = p[Keys.themeMode] ?: "dark",
            jumpHintEnabled = p[Keys.jumpHintEnabled] ?: true
        )
    }

    /** 进入专辑时调用 */
    suspend fun setLastAlbum(albumId: String) {
        context.dataStore.edit {
            it[Keys.lastAlbumId] = albumId
            it[Keys.lastOpenedAt] = System.currentTimeMillis()
        }
    }

    /** 开始播放时调用 */
    suspend fun setLastVideo(albumId: String, videoId: String) {
        context.dataStore.edit {
            it[Keys.lastAlbumId] = albumId
            it[Keys.lastVideoId] = videoId
            it[Keys.lastOpenedAt] = System.currentTimeMillis()
        }
    }

    suspend fun setStartIntoLastAlbum(value: Boolean) {
        context.dataStore.edit { it[Keys.startIntoLastAlbum] = value }
    }

    suspend fun setAutoResumeLastVideo(value: Boolean) {
        context.dataStore.edit { it[Keys.autoResumeLastVideo] = value }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[Keys.keepScreenOn] = value }
    }

    suspend fun setHardwareDecode(value: String) {
        context.dataStore.edit { it[Keys.hardwareDecode] = value }
    }

    suspend fun setBackgroundPlay(value: Boolean) {
        context.dataStore.edit { it[Keys.backgroundPlay] = value }
    }

    suspend fun setDefaultLandscape(value: Boolean) {
        context.dataStore.edit { it[Keys.defaultLandscape] = value }
    }

    suspend fun setSpeedPercent(value: Int) {
        context.dataStore.edit { it[Keys.speedPercent] = value.coerceIn(50, 500) }
    }

    suspend fun setJumpLeft(config: JumpButtonConfig) {
        context.dataStore.edit { it[Keys.jumpLeft] = config.normalized().toJson() }
    }

    suspend fun setJumpRight(config: JumpButtonConfig) {
        context.dataStore.edit { it[Keys.jumpRight] = config.normalized().toJson() }
    }

    suspend fun setPlaylistMode(mode: String) {
        context.dataStore.edit {
            it[Keys.playlistMode] = if (mode in PlaylistMode.ALL) mode else PlaylistMode.SEQUENCE
        }
    }

    suspend fun setNextCountdown(value: Boolean) {
        context.dataStore.edit { it[Keys.nextCountdown] = value }
    }

    suspend fun setLongPressSpeedPercent(value: Int) {
        context.dataStore.edit { it[Keys.longPressSpeedPercent] = value.coerceIn(0, 500) }
    }

    // ---------- 字幕样式 ----------

    suspend fun setSubtitleEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.subtitleEnabled] = value }
    }

    suspend fun setSubtitleFontSize(value: Float) {
        context.dataStore.edit { it[Keys.subtitleFontSize] = value.coerceIn(10f, 30f) }
    }

    suspend fun setSubtitleColor(value: Long) {
        context.dataStore.edit { it[Keys.subtitleColorArgb] = value }
    }

    suspend fun setSubtitleDelay(value: Long) {
        context.dataStore.edit { it[Keys.subtitleDelayMs] = value.coerceIn(-60_000L, 60_000L) }
    }

    suspend fun setJumpHintEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.jumpHintEnabled] = value }
    }

    suspend fun setThemeMode(value: String) {
        context.dataStore.edit { it[Keys.themeMode] = value }
    }

    suspend fun setSubtitleBottomPadding(value: Int) {
        context.dataStore.edit { it[Keys.subtitleBottomPadding] = value.coerceIn(8, 160) }
    }
}
