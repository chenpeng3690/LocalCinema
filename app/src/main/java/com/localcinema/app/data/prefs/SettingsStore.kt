package com.localcinema.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val defaultLandscape: Boolean = true
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
            defaultLandscape = p[Keys.defaultLandscape] ?: true
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
}
