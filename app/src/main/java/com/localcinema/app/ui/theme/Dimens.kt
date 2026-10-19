package com.localcinema.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全站唯一的尺寸来源（dp）。禁止在页面里写魔法数字。
 */
object Dimens {
    /** 页面左右边距，全站统一 */
    val pagePadding = 18.dp

    /** 专辑网格：2 列 */
    val gridGapH = 12.dp
    val gridGapV = 13.dp

    /** 专辑封面高度 */
    val albumCoverHeight = 96.dp

    /** 圆角 */
    val radiusCard = 13.dp      // 专辑封面 / 卡片
    val radiusHero = 15.dp      // 专辑详情 Hero 卡
    val radiusControl = 11.dp   // 搜索框、按钮
    val radiusThumb = 9.dp      // 视频缩略图
    val radiusBadge = 5.dp      // 角标
    val radiusFab = 18.dp       // FAB

    /** 进度条高度，全站统一 */
    val progressHeight = 3.dp

    /** 底部导航图标尺寸 */
    val iconSizeLarge = 18.dp

    /** 底部导航高度 */
    val bottomBarHeight = 56.dp

    /** FAB 尺寸 */
    val fabSize = 52.dp
}
