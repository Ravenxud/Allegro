package com.ravenxud.allegro.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

/**
 * 全站唯一材质入口（U2）。
 *
 * 契约：
 * 1. 内部自带 clip 与描边，调用方**不要**再叠 `background` / `clip`；
 * 2. **禁止**用在 WebView 及其祖先链上（离屏合成会坏视频与输入法）；
 * 3. `backdrop == null`（没有捕获层）时自动退化成半透明 + 描边，视觉不崩；
 * 4. 本文件是唯一允许 `import com.kyant.backdrop.*` 的地方（U3）。
 */
fun Modifier.appGlass(
    backdrop: Backdrop?,
    shape: Shape,
    spec: GlassSpec = GlassSpec(),
): Modifier {
    val quality = GlassQuality.effective(spec.quality, backdrop != null)
    if (quality == GlassQuality.Off || backdrop == null) {
        return this
            .clip(shape)
            .background(Color.White.copy(alpha = spec.surfaceAlpha))
            .border(spec.strokeWidth, Color.White.copy(alpha = spec.strokeAlpha), shape)
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(spec.blur.toPx())
        },
    )
}

/**
 * 玻璃表面叠色（tint + surface），供调用方在玻璃之上再画一层薄色。
 * 单独开一个函数，避免 call site 自己算 alpha。
 */
fun Modifier.glassSurface(shape: Shape, spec: GlassSpec = GlassSpec()): Modifier =
    this
        .clip(shape)
        .background(Color.White.copy(alpha = spec.surfaceAlpha))
        .border(spec.strokeWidth, Color.White.copy(alpha = spec.strokeAlpha), shape)
