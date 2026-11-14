package com.ravenxud.allegro.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 尺寸与字号令牌（对齐 Luna 观感）。
 *
 * 取自 Luna 真机截图的近似值（截图 705px 宽 ≈ 400dp，1dp ≈ 1.76px）：
 * 页面边距 12、分组间距 12、行高 60、圆形按钮 44、胶囊高 40。
 */
object Dimens {
    /** 页面左右边距。 */
    val PagePadding = 12.dp

    /** 分组卡片之间的间距。 */
    val GroupGap = 12.dp

    /** 设置类行的最小高度（Luna 约 60dp）。 */
    val RowHeight = 60.dp

    /** 圆形图标按钮直径。 */
    val CircleButton = 44.dp

    /** 胶囊按钮高度。 */
    val PillHeight = 40.dp

    /** 卡片内的行首缩进。 */
    val RowStart = 18.dp

    /** 卡片内的行尾缩进（让 chevron 贴边）。 */
    val RowEnd = 14.dp

    /** 图标标准尺寸。 */
    val Icon = 24.dp

    /** 触摸目标下限。 */
    val TouchMin = 48.dp
}

/** 字号档位（Luna 为 20 / 17 / 14 / 12 四档）。 */
object TypeScale {
    /** 页面标题。 */
    val Title = 20.sp

    /** 行主文案。 */
    val Row = 16.sp

    /** 行副文案 / 说明。 */
    val Secondary = 13.sp

    /** 网格标签、极小说明。 */
    val Caption = 12.sp
}
