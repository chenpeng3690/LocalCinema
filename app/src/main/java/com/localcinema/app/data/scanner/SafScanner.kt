package com.localcinema.app.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedVideo(
    val uri: Uri,
    val fileName: String,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    /** 同目录下同名的外挂字幕，扫描时自动配对 */
    val subtitleUri: Uri? = null,
    internal val parentDocId: String = ""
)

/**
 * SAF 目录扫描器。
 *
 * 关键性能约束：DocumentsContract 必须**批量查询**（一次 query 拿一个目录的所有子项），
 * 绝对不能对每个文件单独 query，否则 300 个文件会慢到分钟级。
 */
@Singleton
class SafScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    suspend fun scan(
        treeUri: Uri,
        recursive: Boolean,
        onProgress: (scanned: Int, found: Int) -> Unit,
        isCancelled: () -> Boolean
    ): List<ScannedVideo> = withContext(Dispatchers.IO) {
        val result = ArrayList<ScannedVideo>()
        /** key = "父目录docId|去扩展名的文件名"，用于视频与字幕配对 */
        val subtitleMap = HashMap<String, Uri>()
        var scanned = 0

        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext emptyList()

        // 迭代遍历，禁用递归函数，避免深层目录栈溢出
        val queue = ArrayDeque<String>()
        queue.add(rootDocId)

        while (queue.isNotEmpty()) {
            if (isCancelled()) break
            val currentDirId = queue.removeFirst()
            val children = listChildren(treeUri, currentDirId)

            for (child in children) {
                if (isCancelled()) break
                scanned++

                if (child.isDirectory) {
                    if (recursive && !VideoFormats.shouldIgnoreDir(child.name)) {
                        queue.add(child.docId)
                    }
                    continue
                }

                val childUri = runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, child.docId)
                }.getOrNull() ?: continue

                // 字幕先登记，稍后和视频配对
                if (VideoFormats.isSubtitle(child.name)) {
                    val key = "$currentDirId|${VideoFormats.displayNameOf(child.name)}"
                    subtitleMap.putIfAbsent(key, childUri)
                    continue
                }

                if (!VideoFormats.isVideo(child.name)) continue
                if (VideoFormats.shouldIgnoreFile(child.name, child.size)) continue

                val (duration, width, height) = readMetadata(childUri)

                result += ScannedVideo(
                    uri = childUri,
                    fileName = child.name,
                    displayName = VideoFormats.displayNameOf(child.name),
                    size = child.size,
                    lastModified = child.lastModified,
                    durationMs = duration,
                    width = width,
                    height = height,
                    parentDocId = currentDirId
                )

                if (result.size % 5 == 0) onProgress(scanned, result.size)
            }

            onProgress(scanned, result.size)
        }

        // 回填字幕
        result.map { video ->
            video.copy(
                subtitleUri = subtitleMap["${video.parentDocId}|${video.displayName}"]
            )
        }
    }

    private fun listChildren(treeUri: Uri, parentDocId: String): List<DocEntry> {
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        }.getOrNull() ?: return emptyList()

        val out = ArrayList<DocEntry>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val timeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    out += DocEntry(
                        docId = id,
                        name = cursor.getString(nameIdx) ?: continue,
                        mime = cursor.getString(mimeIdx),
                        size = if (cursor.isNull(sizeIdx)) 0L else cursor.getLong(sizeIdx),
                        lastModified = if (cursor.isNull(timeIdx)) 0L else cursor.getLong(timeIdx)
                    )
                }
            }
        }
        return out
    }

    private fun readMetadata(uri: Uri): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            Triple(duration, width, height)
        } catch (_: Exception) {
            Triple(0L, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class DocEntry(
        val docId: String,
        val name: String,
        val mime: String?,
        val size: Long,
        val lastModified: Long
    ) {
        val isDirectory: Boolean
            get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }
}
