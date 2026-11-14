package com.ravenxud.allegro.ui.theme

import androidx.compose.ui.unit.dp

/** 全应用间距令牌，禁止再写裸 dp。 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** 圆角令牌：与 MIUIX 组件默认值对齐。 */
object Radius {
    // 0.44 起与 Shapes 对齐：首页搜索卡 24 / 卡片 24 / 面板 28 / 标签预览 20
    val field = 24.dp
    val card = 24.dp
    val sheet = 28.dp
    val chip = 12.dp
    val preview = 20.dp
}

/** 图标尺寸令牌。 */
object IconSize {
    val row = 24.dp
    val empty = 48.dp
    val handle = 36.dp
}
