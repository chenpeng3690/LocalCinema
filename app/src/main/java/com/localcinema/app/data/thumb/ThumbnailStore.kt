package com.localcinema.app.data.thumb

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缩略图抽取与磁盘缓存。
 *
 * 取帧位置：优先第 120 秒（跳过片头黑屏与字幕卡），时长不足取 1/3 处，
 * 都失败再回退首帧。生成一次后落盘，二次进入直接读文件。
 */
@Singleton
class ThumbnailStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dir: File by lazy {
        File(context.filesDir, "thumbs").apply { if (!exists()) mkdirs() }
    }

    fun fileFor(videoId: String): File = File(dir, "$videoId.jpg")

    fun exists(videoId: String): Boolean {
        val f = fileFor(videoId)
        return f.exists() && f.length() > 0
    }

    suspend fun ensure(uri: Uri, videoId: String, durationMs: Long): File? =
        withContext(Dispatchers.IO) {
            val target = fileFor(videoId)
            if (target.exists() && target.length() > 0) return@withContext target

            val atMs = when {
                durationMs > 120_000L -> 120_000L
                durationMs > 0L -> durationMs / 3
                else -> 0L
            }

            val firstTry = extract(uri, atMs * 1000)
            val bitmap = firstTry ?: if (atMs > 0) extract(uri, 0) else null
            if (bitmap == null) return@withContext null

            val ok = try {
                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                true
            } catch (_: Exception) {
                false
            } finally {
                bitmap.recycle()
            }

            if (ok) target else null
        }

    private fun extract(uri: Uri, timeUs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
