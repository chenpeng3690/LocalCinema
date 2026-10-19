package com.localcinema.app.util

import java.security.MessageDigest

/**
 * 视频唯一标识：sha1(规范化标识 + 文件大小 + 最后修改时间)。
 *
 * 为什么不用纯路径做主键：手机上文件经常被重命名或移动目录，
 * 用路径当主键会导致用户整理一次文件、几十集进度全部丢失。
 */
object VideoId {

    fun of(uri: String, size: Long, lastModified: Long): String {
        val raw = "$uri|$size|$lastModified"
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
