package com.localcinema.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = PlayerColors.Accent,
    onPrimary = Color.White,
    secondary = PlayerColors.Accent,
    background = PlayerColors.Bg,
    onBackground = PlayerColors.TextPrimary,
    surface = PlayerColors.Surface,
    onSurface = PlayerColors.TextPrimary,
    surfaceVariant = PlayerColors.SurfaceElevated,
    onSurfaceVariant = PlayerColors.TextSecondary,
    outline = PlayerColors.Line,
    outlineVariant = PlayerColors.Divider
)

/**
 * 本工程只提供深色主题（浅色为主题色替换实验，阶段六再做）。
 * 所有页面必须包在这个 Theme 里，否则颜色 token 不生效。
 */
@Composable
fun LocalCinemaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = PlayerTypography,
        content = content
    )
}
