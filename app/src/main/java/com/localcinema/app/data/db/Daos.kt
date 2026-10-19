package com.localcinema.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY lastAccessedAt DESC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE name LIKE '%' || :keyword || '%' ORDER BY lastAccessedAt DESC")
    fun search(keyword: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AlbumEntity?

    /** 必须用 Upsert：REPLACE 会先 DELETE，触发 videos 的级联删除 */
    @Upsert
    suspend fun upsert(album: AlbumEntity)

    @Query("UPDATE albums SET lastAccessedAt = :timestamp WHERE id = :albumId")
    suspend fun touch(albumId: String, timestamp: Long)

    @Delete
    suspend fun delete(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteById(albumId: String)
}

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos WHERE albumId = :albumId AND missing = 0 ORDER BY displayName COLLATE NOCASE ASC")
    fun observeByAlbum(albumId: String): Flow<List<VideoEntity>>

    /** 失效文件（目录里已经找不到的文件），用于提示与清理 */
    @Query("SELECT * FROM videos WHERE albumId = :albumId AND missing = 1 ORDER BY displayName COLLATE NOCASE ASC")
    fun observeMissing(albumId: String): Flow<List<VideoEntity>>

    /** 跨专辑搜索，按名称模糊匹配 */
    @Query(
        "SELECT * FROM videos WHERE missing = 0 AND displayName LIKE '%' || :keyword || '%' " +
            "ORDER BY displayName COLLATE NOCASE ASC LIMIT 300"
    )
    fun search(keyword: String): Flow<List<VideoEntity>>

    @Query("UPDATE videos SET manualSubtitleUri = :uri WHERE id = :id")
    suspend fun setManualSubtitle(id: String, uri: String?)

    @Query("SELECT * FROM videos WHERE albumId = :albumId ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun listByAlbum(albumId: String): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE albumId = :albumId AND uri = :uri LIMIT 1")
    suspend fun findByUri(albumId: String, uri: String): VideoEntity?

    /** 必须用 Upsert：REPLACE 会级联删掉 playback_state，重扫即丢进度 */
    @Upsert
    suspend fun upsertAll(videos: List<VideoEntity>)

    @Upsert
    suspend fun upsert(video: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE videos SET missing = 1 WHERE id = :id")
    suspend fun markMissing(id: String)

    @Query("DELETE FROM videos WHERE albumId = :albumId AND missing = 1")
    suspend fun deleteMissing(albumId: String)

    @Query("SELECT COUNT(*) FROM videos WHERE albumId = :albumId AND missing = 0")
    suspend fun countByAlbum(albumId: String): Int
}

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE videoId = :videoId LIMIT 1")
    suspend fun get(videoId: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state WHERE videoId = :videoId LIMIT 1")
    fun observe(videoId: String): Flow<PlaybackStateEntity?>

    @Query("SELECT * FROM playback_state ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<PlaybackStateEntity>>

    @Query("SELECT * FROM playback_state")
    fun observeAll(): Flow<List<PlaybackStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PlaybackStateEntity)

    @Query("DELETE FROM playback_state WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM playback_state")
    suspend fun clearAll()
}
