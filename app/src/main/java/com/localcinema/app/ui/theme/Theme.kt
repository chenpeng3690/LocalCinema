package com.localcinema.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** 主题模式。值是持久化到 DataStore 的字符串，改值等于破坏用户已有设置 */
object ThemeMode {
    const val DARK = "dark"
    const val LIGHT = "light"
    const val SYSTEM = "system"

    val ALL = listOf(DARK, LIGHT, SYSTEM)

    fun label(mode: String): String = when (mode) {
        LIGHT -> "浅色"
        SYSTEM -> "跟随系统"
        else -> "深色"
    }

    /** 解析成「是否深色」 */
    fun isDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        LIGHT -> false
        SYSTEM -> systemDark
        else -> true
    }
}

private fun schemeOf(p: Palette) = if (p.isDark) {
    darkColorScheme(
        primary = p.accent,
        onPrimary = Color.White,
        secondary = p.accent,
        onSecondary = Color.White,
        background = p.bg,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceElevated,
        onSurfaceVariant = p.textSecondary,
        outline = p.line,
        outlineVariant = p.divider,
        error = p.accent
    )
} else {
    lightColorScheme(
        primary = p.accent,
        onPrimary = Color.White,
        secondary = p.accent,
        onSecondary = Color.White,
        background = p.bg,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceElevated,
        onSurfaceVariant = p.textSecondary,
        outline = p.line,
        outlineVariant = p.divider,
        error = p.accent
    )
}

/**
 * 全局主题。
 *
 * [mode] 是主题模式字符串（dark / light / system），由 MainActivity 从设置里读出来传入。
 * 所有页面必须包在这里，否则 `PlayerColors.xxx` 会拿不到当前调色板。
 */
@Composable
fun LocalCinemaTheme(
    mode: String = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val palette = if (ThemeMode.isDark(mode, isSystemInDarkTheme())) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeOf(palette),
            typography = PlayerTypography,
            content = content
        )
    }
}
