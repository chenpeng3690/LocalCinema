package com.localcinema.app.data.prefs

import org.json.JSONObject

/**
 * 播放页快退/快进悬浮按钮的配置。左右各一份，互不影响。
 *
 * 位置用屏幕比例（0..1）而非 dp，换设备、换分辨率都不会跑到屏幕外。
 * xRatio / yRatio 表示按钮**中心点**在屏幕上的相对位置。
 */
data class JumpButtonConfig(
    val enabled: Boolean = true,
    /** 每次点击跳转的秒数，1–120 */
    val seconds: Int = 10,
    /** 按钮直径，36–96 dp */
    val sizeDp: Int = 56,
    /** 不透明度，0.1–1.0 */
    val opacity: Float = 0.6f,
    /** 中心点水平位置，0..1 */
    val xRatio: Float = 0.10f,
    /** 中心点垂直位置，0..1 */
    val yRatio: Float = 0.60f,
    /** 常驻：控件层自动隐藏后，该按钮仍然保留 */
    val constantVisible: Boolean = false,
    /** 按住持续触发 */
    val longPressRepeat: Boolean = true
) {

    fun normalized(): JumpButtonConfig = copy(
        seconds = seconds.coerceIn(1, 120),
        sizeDp = sizeDp.coerceIn(36, 96),
        opacity = opacity.coerceIn(0.1f, 1f),
        xRatio = xRatio.coerceIn(0.04f, 0.96f),
        yRatio = yRatio.coerceIn(0.08f, 0.92f)
    )

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled)
        put("seconds", seconds)
        put("sizeDp", sizeDp)
        put("opacity", opacity.toDouble())
        put("xRatio", xRatio.toDouble())
        put("yRatio", yRatio.toDouble())
        put("constantVisible", constantVisible)
        put("longPressRepeat", longPressRepeat)
    }.toString()

    companion object {
        /** 左侧默认：回退 10 秒，靠左 */
        val DEFAULT_LEFT = JumpButtonConfig(
            seconds = 10,
            xRatio = 0.10f,
            yRatio = 0.60f
        )

        /** 右侧默认：快进 30 秒，靠右。快退少、快进多符合实际观影习惯 */
        val DEFAULT_RIGHT = JumpButtonConfig(
            seconds = 30,
            xRatio = 0.90f,
            yRatio = 0.60f
        )

        fun fromJson(json: String?, fallback: JumpButtonConfig): JumpButtonConfig {
            if (json.isNullOrBlank()) return fallback
            return runCatching {
                val obj = JSONObject(json)
                JumpButtonConfig(
                    enabled = obj.optBoolean("enabled", fallback.enabled),
                    seconds = obj.optInt("seconds", fallback.seconds),
                    sizeDp = obj.optInt("sizeDp", fallback.sizeDp),
                    opacity = obj.optDouble("opacity", fallback.opacity.toDouble()).toFloat(),
                    xRatio = obj.optDouble("xRatio", fallback.xRatio.toDouble()).toFloat(),
                    yRatio = obj.optDouble("yRatio", fallback.yRatio.toDouble()).toFloat(),
                    constantVisible = obj.optBoolean("constantVisible", fallback.constantVisible),
                    longPressRepeat = obj.optBoolean("longPressRepeat", fallback.longPressRepeat)
                ).normalized()
            }.getOrDefault(fallback)
        }
    }
}

/** 九宫格预设位置 */
object JumpPresets {
    val xRatios = listOf(0.12f, 0.50f, 0.88f)
    val yRatios = listOf(0.30f, 0.55f, 0.78f)

    fun cellOf(config: JumpButtonConfig): Int {
        val col = xRatios.indexOfFirst { kotlin.math.abs(it - config.xRatio) < 0.12f }
        val row = yRatios.indexOfFirst { kotlin.math.abs(it - config.yRatio) < 0.10f }
        if (col < 0 || row < 0) return -1
        return row * 3 + col
    }
}
