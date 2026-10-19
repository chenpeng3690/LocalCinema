package com.localcinema.app.data.subtitle

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/** 带起止时间的字幕条目 */
data class TimedCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * 外挂字幕解析。
 *
 * 为什么不用 ExoPlayer 的 SubtitleConfiguration：
 * Media3 的 Cue 只描述「此刻要显示什么」，不含起止时间，
 * 因此无法做「字幕延迟」这种需要自己比对时间轴的功能。
 * 自己解析后按播放位置筛选，延迟/字号/位置才完全可控。
 */
@Singleton
class SubtitleRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun load(uri: Uri, fileName: String): List<TimedCue> = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext emptyList()

        val raw = decodeText(bytes)
        SubtitleParser.parse(raw, fileName).sortedBy { it.startMs }
    }

    /** 字幕文件编码混乱，先按 BOM 判断，再退回 UTF-8 / GBK */
    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }

        val utf8 = String(bytes, Charsets.UTF_8)
        val brokenChars = utf8.count { it == '\uFFFD' }
        if (brokenChars > 0 && brokenChars * 100 > utf8.length) {
            val gbk = runCatching { String(bytes, Charset.forName("GBK")) }.getOrNull()
            if (gbk != null && gbk.count { it == '\uFFFD' } < brokenChars) return gbk
        }
        return utf8
    }
}

object SubtitleParser {

    private val TIME_RANGE = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )
    private val HTML_TAG = Regex("""<[^>]+>""")
    private val ASS_TAG = Regex("""\{[^}]*}""")

    fun parse(raw: String, fileName: String): List<TimedCue> {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "ass", "ssa" -> parseAss(raw)
            else -> parseSrtOrVtt(raw)
        }
    }

    /** SRT 与 WebVTT 结构接近，用同一套块解析 */
    private fun parseSrtOrVtt(raw: String): List<TimedCue> {
        val out = ArrayList<TimedCue>()
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')

        normalized.split(Regex("\n\\s*\n")).forEach { block ->
            val lines = block.trim().lines()
            if (lines.isEmpty()) return@forEach

            val timeLineIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIndex < 0) return@forEach

            val match = TIME_RANGE.find(lines[timeLineIndex]) ?: return@forEach
            val g = match.groupValues
            val start = toMillis(g[1], g[2], g[3], g[4])
            val end = toMillis(g[5], g[6], g[7], g[8])
            if (end <= start) return@forEach

            val text = lines
                .drop(timeLineIndex + 1)
                .joinToString("\n")
                .trim()
                .let { it.replace(HTML_TAG, "") }
                .trim()

            if (text.isNotEmpty()) out += TimedCue(start, end, text)
        }
        return out
    }

    private fun parseAss(raw: String): List<TimedCue> {
        val out = ArrayList<TimedCue>()
        raw.replace("\r\n", "\n").lines().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) return@forEach

            // Dialogue: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
            val parts = trimmed.substringAfter(':').split(",", limit = 10)
            if (parts.size < 10) return@forEach

            val start = parseAssTime(parts[1].trim()) ?: return@forEach
            val end = parseAssTime(parts[2].trim()) ?: return@forEach
            if (end <= start) return@forEach

            val text = parts[9]
                .replace("\\N", "\n")
                .replace("\\n", "\n")
                .replace(ASS_TAG, "")
                .trim()

            if (text.isNotEmpty()) out += TimedCue(start, end, text)
        }
        return out
    }

    private fun toMillis(h: String, m: String, s: String, ms: String): Long {
        val millis = ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return (h.toLongOrNull() ?: 0L) * 3_600_000L +
            (m.toLongOrNull() ?: 0L) * 60_000L +
            (s.toLongOrNull() ?: 0L) * 1_000L +
            millis
    }

    /** ASS 时间格式：0:00:01.50 */
    private fun parseAssTime(value: String): Long? {
        val parts = value.split(":")
        if (parts.size < 3) return null
        val h = parts[0].trim().toLongOrNull() ?: return null
        val m = parts[1].trim().toLongOrNull() ?: return null
        val seconds = parts[2].trim().toDoubleOrNull() ?: return null
        return h * 3_600_000L + m * 60_000L + (seconds * 1000).toLong()
    }

}
