package com.localcinema.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 专辑的目录访问方式 */
object AccessMode {
    const val SAF = "SAF"           // SAF 持久化 URI，主通道
    const val FILE = "FILE"         // 完整文件权限下的裸路径
    const val MEDIASTORE = "MEDIASTORE"
}

object SortMode {
    const val NAME_NATURAL = "name_natural"
    const val ADDED = "added_at"
    const val MODIFIED = "modified_at"
    const val DURATION = "duration"
    const val MANUAL = "manual"
}

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** SAF 持久化 URI，accessMode = SAF 时使用 */
    val treeUri: String?,
    /** 完整文件权限下的绝对路径，accessMode = FILE 时使用 */
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
