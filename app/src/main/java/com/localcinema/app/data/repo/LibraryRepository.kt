package com.localcinema.app.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.localcinema.app.data.db.AlbumDao
import com.localcinema.app.data.db.AlbumEntity
import com.localcinema.app.data.db.VideoDao
import com.localcinema.app.data.db.VideoEntity
import com.localcinema.app.data.scanner.SafScanner
import com.localcinema.app.data.scanner.VideoFormats
import com.localcinema.app.data.thumb.ThumbnailStore
import com.localcinema.app.util.VideoId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val albumId: String, val scanned: Int, val found: Int) : ScanState
    data class Done(val albumId: String, val count: Int) : ScanState
    data class Failed(val albumId: String?, val message: String) : ScanState
}

/**
 * 专辑与媒体库的唯一编排入口。
 * UI 不直接碰 SAF / 扫描器 / 缩略图，只调这里。
 */
@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumDao: AlbumDao,
    private val videoDao: VideoDao,
    private val scanner: SafScanner,
    private val thumbs: ThumbnailStore
) {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    @Volatile
    private var cancelled = false

    fun cancelScan() {
        cancelled = true
    }

    /**
     * 把用户选中的目录注册为专辑。已存在同一目录时直接复用，不重复建。
     */
    suspend fun addAlbum(treeUri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val uriString = treeUri.toString()
        val existing = albumDao.observeAll().first().firstOrNull { it.treeUri == uriString }
        if (existing != null) return@withContext existing.id

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        albumDao.upsert(
            AlbumEntity(
                id = id,
                name = resolveAlbumName(treeUri),
                treeUri = uriString,
                rootPath = null,
                coverPath = null,
                createdAt = now,
                lastAccessedAt = now
            )
        )
        id
    }

    suspend fun scanAlbum(albumId: String) {
        val album = albumDao.getById(albumId)
        if (album == null) {
            _scanState.value = ScanState.Failed(albumId, "专辑不存在")
            return
        }
        val treeUri = album.treeUri?.let { Uri.parse(it) }
        if (treeUri == null) {
            _scanState.value = ScanState.Failed(albumId, "目录未授权，请重新选择文件夹")
            return
        }

        cancelled = false
        _scanState.value = ScanState.Running(albumId, 0, 0)

        try {
            val scanned = scanner.scan(
                treeUri = treeUri,
                recursive = album.recursive,
                onProgress = { scannedCount, found ->
                    if (!cancelled) {
                        _scanState.value = ScanState.Running(albumId, scannedCount, found)
                    }
                },
                isCancelled = { cancelled }
            )

            if (cancelled) {
                _scanState.value = ScanState.Idle
                return
            }

            if (scanned.isEmpty()) {
                // 扫描不到任何视频时，绝不能继续往下走：
                // 后面的 markMissing 会把已有记录全部标记失效，等于清空专辑
                _scanState.value = ScanState.Failed(
                    albumId,
                    "没有扫描到视频，请检查目录权限或文件格式"
                )
                return
            }

            val now = System.currentTimeMillis()
            val entities = scanned.map { v ->
                VideoEntity(
                    id = VideoId.of(v.uri.toString(), v.size, v.lastModified),
                    albumId = albumId,
                    uri = v.uri.toString(),
                    path = null,
                    displayName = v.displayName,
                    size = v.size,
                    durationMs = v.durationMs,
                    width = v.width,
                    height = v.height,
                    container = VideoFormats.extensionOf(v.fileName),
                    vcodec = null,
                    acodec = null,
                    lastModified = v.lastModified,
                    thumbPath = null,
                    subtitleUri = v.subtitleUri?.toString(),
                    missing = false,
                    addedAt = now
                )
            }

            videoDao.upsertAll(entities)

            // 增量：本次没扫到的旧记录标为失效，不物理删除（保留其播放进度）
            val scannedUris = entities.mapTo(HashSet()) { it.uri }
            videoDao.listByAlbum(albumId).forEach { old ->
                if (old.uri !in scannedUris && !old.missing) {
                    videoDao.markMissing(old.id)
                }
            }

            // 专辑封面取第一个视频的缩略图
            entities.firstOrNull()?.let { first ->
                val file = thumbs.ensure(
                    uri = Uri.parse(first.uri),
                    videoId = first.id,
                    durationMs = first.durationMs
                )
                if (file != null) {
                    albumDao.upsert(album.copy(coverPath = file.absolutePath))
                }
            }

            albumDao.touch(albumId, now)
            _scanState.value = ScanState.Done(albumId, entities.size)
        } catch (e: Exception) {
            _scanState.value = ScanState.Failed(albumId, e.message ?: "扫描失败")
        }
    }

    /**
     * 删除专辑：先清缩略图文件，再删库。
     * videos 与 playback_state 由 Room 外键级联删除，不需要手工处理。
     */
    suspend fun deleteAlbum(album: AlbumEntity) = withContext(Dispatchers.IO) {
        videoDao.listByAlbum(album.id).forEach { video ->
            runCatching { thumbs.fileFor(video.id).delete() }
        }
        album.coverPath?.let { path -> runCatching { java.io.File(path).delete() } }
        albumDao.deleteById(album.id)
    }

    suspend fun renameAlbum(album: AlbumEntity, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        albumDao.upsert(album.copy(name = trimmed))
    }

    /**
     * 用用户选的图片替换专辑封面。
     * 复制进应用私有目录，避免 SAF 临时授权失效后封面变空白。
     */
    suspend fun setAlbumCover(album: AlbumEntity, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.filesDir, "covers").apply { mkdirs() }
            val target = java.io.File(dir, "${album.id}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            albumDao.upsert(album.copy(coverPath = target.absolutePath))
            true
        }.getOrDefault(false)
    }

    /** 清理本专辑内已被判定失效的记录 */
    suspend fun clearMissing(albumId: String) = withContext(Dispatchers.IO) {
        videoDao.deleteMissing(albumId)
    }

    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }

    /**
     * 从 tree uri 推导专辑名：documentId 形如 "primary:Movies/权力的游戏"。
     */
    private fun resolveAlbumName(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return "未命名专辑"
        val path = docId.substringAfter(':', docId)
        val last = path.trimEnd('/').substringAfterLast('/')
        return last.ifBlank { "未命名专辑" }
    }
}
