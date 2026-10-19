package com.localcinema.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 全站唯一的颜色来源。
 * 任何界面禁止直接写 Color(0xFF...)，必须引用这里的 token。
 */
object PlayerColors {
    /** 全局背景，禁止使用纯黑 #000000 */
    val Bg = Color(0xFF08080A)

    /** 卡片、搜索框、普通按钮底 */
    val Surface = Color(0xFF16161A)

    /** 二级面板、弹层 */
    val SurfaceElevated = Color(0xFF1F1F25)

    /** 分隔线、描边 */
    val Line = Color(0xFF2A2A32)

    val TextPrimary = Color(0xFFF0F0F4)
    val TextSecondary = Color(0xFF8A8A96)
    val TextTertiary = Color(0xFF6C6C78)

    /** 主题色：进度条、上次播放标签/描边、FAB、选中态 */
    val Accent = Color(0xFFFF6B45)

    /** 主题色 14% 透明底：上次播放项背景、tint 按钮 */
    val AccentSoft = Color(0x24FF6B45)

    val AccentGradientStart = Color(0xFFFF7B56)
    val AccentGradientEnd = Color(0xFFF04E27)

    /** 倍速角标：唯一允许的第二种强调色 */
    val RateBadge = Color(0xFFFFC24A)
    val RateBadgeInk = Color(0xFF0D0D10)

    /** 「未看完 / 已完成」状态角标 */
    val DoneGreen = Color(0xFF3E8E7E)

    /** 线性图标默认色 */
    val Icon = Color(0xFFB9B9C4)

    /** 自绘开关关闭态轨道 */
    val SwitchOff = Color(0xFF33333D)

    /** 设置分组标题 */
    val GroupLabel = Color(0xFF9A9AA6)

    /** 列表右侧箭头 */
    val Chevron = Color(0xFF5A5A66)

    /** 进度条轨道 12% */
    val TrackInactive = Color(0x1FFFFFFF)

    /** 列表分隔线 5% */
    val Divider = Color(0x0DFFFFFF)

    /** 浮层遮罩 72% */
    val Scrim = Color(0xB8000000)

    /** 底部导航背景（半透明，压住内容） */
    val BottomBarBg = Color(0xEB101014)
    val BottomBarDivider = Color(0x0FFFFFFF)

    /** 封面占位文字 */
    val CoverText = Color(0xE6FFFFFF)

    /** 封面右下角标底 */
    val BadgeScrim = Color(0x8C000000)

    /** 播放页控件层：上下渐变压暗 */
    val PlayerScrimTop = Color(0x99000000)
    val PlayerScrimBottom = Color(0xB3000000)

    /** 播放页快进/快退圆钮底（白 16%） */
    val PlayerJumpBg = Color(0x29FFFFFF)

    /** 播放页中央播放钮底（黑 42%） */
    val PlayerPlayBg = Color(0x6B000000)

    /** 播放页进度条未播段（白 22%） */
    val PlayerTrackInactive = Color(0x38FFFFFF)

    /** 播放页工具条图标与文字 */
    val PlayerToolTint = Color(0xFFAFAFBB)

    /** 倍速面板：步进圆钮底 */
    val StepperBg = Color(0xFF2C2C36)

    /** 倍速面板：快捷档位底 */
    val ChipBg = Color(0xFF26262E)

    /** 倍速面板：选中档位文字 */
    val ChipActiveInk = Color(0xFF18181C)

    /** 播放页时间文字 */
    val PlayerTimeText = Color(0xFFC6C6D0)

    /** Hero 卡副标题 */
    val HeroSubText = Color(0xFFB4B4C0)

    /** Hero 卡渐变遮罩 */
    val HeroScrimTop = Color(0x1A000000)
    val HeroScrimBottom = Color(0xD1000000)

    /**
     * 无封面时的占位渐变（深色四色板）。
     * 真实封面接入后仅在抽帧失败时使用。
     */
    val CoverPalettes = listOf(
        listOf(Color(0xFF3A4B60), Color(0xFF141D28)),
        listOf(Color(0xFF1F5F58), Color(0xFF0A211E)),
        listOf(Color(0xFF4B3459), Color(0xFF1B1224)),
        listOf(Color(0xFF5C4C2A), Color(0xFF221A0C))
    )
}
