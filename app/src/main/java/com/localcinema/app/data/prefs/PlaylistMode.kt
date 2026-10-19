package com.localcinema.app.data.prefs

/**
 * 播放列表模式。
 *
 * 与「自动连播」的区别：自动连播是「播完要不要跳下一集」，
 * 这里是「跳哪一集、跳完怎么办」，两者组合出四种常见行为。
 */
object PlaylistMode {
    /** 顺序：播完进下一集，最后一集停住 */
    const val SEQUENCE = "sequence"

    /** 单集循环：反复播当前这一集 */
    const val LOOP_ONE = "loop_one"

    /** 随机：随机挑一集，不会连续重复同一个 */
    const val SHUFFLE = "shuffle"

    /** 播完停止：只播当前这一集，结束就停在片尾 */
    const val STOP_AFTER_ONE = "stop_after_one"

    val ALL = listOf(SEQUENCE, LOOP_ONE, SHUFFLE, STOP_AFTER_ONE)

    fun label(mode: String): String = when (mode) {
        LOOP_ONE -> "单集循环"
        SHUFFLE -> "随机播放"
        STOP_AFTER_ONE -> "播完停止"
        else -> "顺序播放"
    }

    fun next(mode: String): String = when (mode) {
        SEQUENCE -> LOOP_ONE
        LOOP_ONE -> SHUFFLE
        SHUFFLE -> STOP_AFTER_ONE
        else -> SEQUENCE
    }
}
