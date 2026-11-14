package com.ravenxud.allegro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 形状令牌（对齐 Luna 观感）。
 *
 * 规矩：界面里不要再写裸 `RoundedCornerShape(…dp)`，除本文件外只允许引用这里。
 * 数值取自 Luna 真机截图：设置卡 24dp / 标签卡 20dp / 浮层 28dp。
 */
object Shapes {
    /** 分组卡片（设置、书签、历史…）。 */
    val Card = RoundedCornerShape(24.dp)

    /** 标签页预览卡、封面类小卡。 */
    val Tile = RoundedCornerShape(20.dp)

    /** 底部面板。 */
    val Panel = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** 四角都圆的浮层（Luna 的标签菜单）。 */
    val FloatingPanel = RoundedCornerShape(28.dp)

    /** 列表项按压高亮。 */
    val RowHighlight = RoundedCornerShape(16.dp)

    /** 小圆角：指示条、迷你标签。 */
    val Tiny = RoundedCornerShape(8.dp)

    /** 进度条/细指示器。 */
    val Indicator = RoundedCornerShape(6.dp)

    /** 胶囊按钮。 */
    val Capsule = RoundedCornerShape(percent = 50)
}
