package com.ravenxud.allegro.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.squircleClip

/**
 * 超椭圆（squircle）圆角。
 *
 * 来源：`miuix-squircle`（Apache-2.0，miuix 的模块之一）。
 * 与普通 `RoundedCornerShape` 的区别是角部曲率连续，观感更接近 iOS / Luna 的"软圆角"。
 *
 * 只在**大块面**上用（卡片、标签卡、面板）；按钮、胶囊继续用普通圆角，
 * 因为小尺寸上两者肉眼无差，反而增加布局成本。
 */
@Composable
fun Modifier.squircle(radius: Dp, smoothing: Float = 0.8f): Modifier =
    this.squircleClip(radius, smoothing)

/** 常用档位：与 [Shapes] 的数值一一对应，便于替换时对照。 */
object SquircleRadii {
    val Card = 24.dp
    val Tile = 20.dp
    val Panel = 28.dp
}
