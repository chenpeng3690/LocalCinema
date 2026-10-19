package com.localcinema.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字号阶梯（sp），与设计 token 一一对应。
 * 数字类文本（时长、集数）请配合 [TabularNumberStyle] 使用，避免进度跳动时宽度抖动。
 */
object PlayerType {
    /** 页面大标题：影音库、设置 */
    val PageTitle = TextStyle(
        fontSize = 25.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight(750),
        letterSpacing = (-0.6).sp
    )

    /** 顶栏标题：专辑名 */
    val TopBarTitle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight(650)
    )

    /** 区块标题：继续观看、我的专辑 */
    val SectionTitle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight(650)
    )

    /** 卡片标题：专辑名 */
    val CardTitle = TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight(600)
    )

    /** 列表标题：视频名 */
    val ListTitle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight(550)
    )

    /** 正文 / 设置项 */
    val Body = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight(400)
    )

    /** 辅助信息：时长、路径 */
    val Caption = TextStyle(
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight(400)
    )

    /** 角标：上次播放、1080P */
    val Badge = TextStyle(
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight(700),
        letterSpacing = 0.2.sp
    )
}

/** 等宽数字，用于时长、进度、集数 */
val TabularNumberStyle = TextStyle(
    fontFeatureSettings = "tnum",
    fontFamily = FontFamily.Default
)

val PlayerTypography = Typography(
    headlineLarge = PlayerType.PageTitle,
    titleMedium = PlayerType.TopBarTitle,
    bodyMedium = PlayerType.Body,
    bodySmall = PlayerType.Caption,
    labelSmall = PlayerType.Badge
)
