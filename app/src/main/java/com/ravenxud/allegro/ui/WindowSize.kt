package com.ravenxud.allegro.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 宽度断点。参考 Material 3 的窗口尺寸类别，但只取我们需要区分的三档。
 *
 * - COMPACT：手机竖屏（< 600dp），沿用现有底部工具栏 + 全屏标签面板
 * - MEDIUM：折叠屏展开态 / 小平板（600–839dp），显示窄侧栏（220dp）
 * - EXPANDED：平板横屏（>= 840dp），显示宽侧栏（280dp）
 */
enum class LayoutWidth {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    val isLarge: Boolean get() = this != COMPACT

    /** 侧栏宽度；COMPACT 时为 0（不显示）。 */
    val panelWidthDp: Int
        get() = when (this) {
            COMPACT -> 0
            MEDIUM -> 220
            EXPANDED -> 280
        }

    companion object {
        fun classify(widthDp: Int): LayoutWidth = when {
            widthDp >= 840 -> EXPANDED
            widthDp >= 600 -> MEDIUM
            else -> COMPACT
        }
    }
}

/**
 * 当前窗口宽度档位。
 *
 * 用 `LocalConfiguration.screenWidthDp`：Activity 已经声明 `configChanges=orientation|screenSize`，
 * 折叠屏展开/分屏拖动时 Configuration 会更新并触发重组，不需要重建 Activity。
 */
@Composable
fun rememberLayoutWidth(): LayoutWidth =
    LayoutWidth.classify(LocalConfiguration.current.screenWidthDp)
