package com.localcinema.app.data.scanner

/** 视频扩展名白名单（大小写不敏感）。SAF 返回的 MIME 对 RMVB 等并不可靠，只能靠扩展名。 */
object VideoFormats {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "flv", "ts", "m2ts", "mts",
        "webm", "wmv", "rm", "rmvb", "3gp", "ogv", "mpg", "mpeg",
        "divx", "m4v", "f4v", "asf", "vob", "mp2", "m2v"
    )

    /** 扫描时直接跳过的目录名 */
    private val IGNORED_DIRS = setOf(
        "@eaDir", "#recycle", "System Volume Information",
        "Android/data", "LOST.DIR", ".thumbnails"
    )

    /** 小于 1MB 的多为预告片、广告、损坏文件 */
    const val MIN_SIZE_BYTES = 128L * 1024

    private val NAME_BLACKLIST = listOf("sample", "预告", "广告", "trailer")

    fun isVideo(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    /** 外挂字幕扩展名 */
    private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub", "smi", "ttml")

    fun isSubtitle(name: String): Boolean = extensionOf(name) in SUBTITLE_EXTENSIONS

    /** ExoPlayer 需要的字幕 MIME 类型 */
    fun subtitleMimeType(name: String): String = when (extensionOf(name)) {
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        "ttml" -> "application/ttml+xml"
        else -> "application/x-subrip"
    }

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    fun shouldIgnoreDir(name: String): Boolean {
        if (name.startsWith('.')) return true
        return IGNORED_DIRS.any { it.equals(name, ignoreCase = true) }
    }

    fun shouldIgnoreFile(name: String, size: Long): Boolean {
        if (name.startsWith('.')) return true
        // size = 0 表示 provider 没有返回大小，不能据此判定"过小"，否则整目录都会被误杀
        if (size > 0 && size < MIN_SIZE_BYTES) return true
        return NAME_BLACKLIST.any { name.contains(it, ignoreCase = true) }
    }

    /** 去掉扩展名，用于列表展示 */
    fun displayNameOf(fileName: String): String {
        val idx = fileName.lastIndexOf('.')
        return if (idx > 0) fileName.substring(0, idx) else fileName
    }
}

/** 时长格式化：mm:ss 或 h:mm:ss */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

/** 分辨率标签：1080P / 720P / 4K */
fun resolutionLabel(width: Int, height: Int): String {
    if (height <= 0) return ""
    return when {
        height >= 2000 -> "4K"
        height >= 1000 -> "1080P"
        height >= 700 -> "720P"
        height >= 500 -> "480P"
        else -> "${height}P"
    }
}
