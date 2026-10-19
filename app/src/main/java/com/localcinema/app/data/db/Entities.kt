package com.localcinema.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 专辑的目录访问方式 */
object AccessMode {
    /** SAF 持久化 URI —— 目前唯一的访问方式 */
    const val SAF = "SAF"
}

object SortMode {
    const val NAME_NATURAL = "name_natural"
    const val ADDED = "added_at"
    const val MODIFIED = "modified_at"
    const val DURATION = "duration"
}

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** SAF 持久化 URI，accessMode = SAF 时使用 */
    val treeUri: String?,
    /** 展示用的裸路径，可能为空 */
    val rootPath: String?,
    val coverPath: String?,
    val sortMode: String = SortMode.NAME_NATURAL,
    val recursive: Boolean = true,
    val accessMode: String = AccessMode.SAF,
    val createdAt: Long,
    val lastAccessedAt: Long
)

@Entity(
    tableName = "videos",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("albumId"),
        Index(value = ["albumId", "uri"], unique = true)
    ]
)
data class VideoEntity(
    /** sha1(uri + size + lastModified)，避免重命名/移动后进度全丢 */
    @PrimaryKey val id: String,
    val albumId: String,
    val uri: String,
    val path: String?,
    val displayName: String,
    val size: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val container: String?,
    val vcodec: String?,
    val acodec: String?,
    val lastModified: Long,
    val thumbPath: String?,
    /** 同目录下同名外挂字幕的 uri（扫描时自动匹配） */
    val subtitleUri: String? = null,
    /**
     * 用户手动挂载的字幕 uri。
     * 优先级高于自动匹配的 subtitleUri，重扫不会覆盖，下次进来自动加载。
     */
    val manualSubtitleUri: String? = null,
    /** 扫描时文件已不存在，灰显而非物理删除 */
    val missing: Boolean = false,
    val addedAt: Long
)

@Entity(
    tableName = "playback_state",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaybackStateEntity(
    @PrimaryKey val videoId: String,
    val positionMs: Long = 0L,
    val durationMs: Long,
    val finished: Boolean = false,
    val playCount: Int = 0,
    val updatedAt: Long
)

/**
 * 播放记录 + 视频信息 + 专辑名。
 * 首页「继续观看」与「最近」共用这一个投影，避免写两套 join。
 */
data class RecentRow(
    @Embedded val video: VideoEntity,
    val statePositionMs: Long,
    val stateDurationMs: Long,
    val stateUpdatedAt: Long,
    val stateFinished: Boolean,
    val albumName: String?
)

/** 按专辑统计视频数，用于文件夹页一次查出全部计数 */
data class AlbumVideoCount(
    val albumId: String,
    val count: Int
)
