package com.ravenxud.allegro.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 材质质量档（用户可切，默认全开）。
 *
 * 决策已改为"效果优先"：**不按设备自动降级**，低端机默认也是 High。
 * 这里只保留用户手动档位，以及"没有捕获层时"的技术性回退。
 */
enum class GlassQuality {
    /** 模糊 + 折射/色散全开。 */
    High,

    /** 只有模糊（当前先用库的 blur，IOR 在 P3 接入）。 */
    Medium,

    /** 纯半透明 + 描边，零离屏开销。 */
    Off,
    ;

    companion object {
        /** 没有捕获层时只能退化成半透明——这是技术约束，不是性能取舍。 */
        fun effective(quality: GlassQuality, hasBackdrop: Boolean): GlassQuality =
            if (!hasBackdrop) Off else quality
    }
}

/**
 * 一份玻璃描述。数值取方案 §8：模糊 24dp、表面 12%、描边 1dp 18%、tint 8%。
 *
 * 调用方原则上只传 `shape`，其余走默认值；要调就在 [GlassSpec] 里加具名字段，
 * 不要在 call site 写裸数值。
 */
@Immutable
data class GlassSpec(
    val blur: Dp = 24.dp,
    val tintAlpha: Float = 0.08f,
    val surfaceAlpha: Float = 0.12f,
    val strokeAlpha: Float = 0.18f,
    val strokeWidth: Dp = 1.dp,
    val quality: GlassQuality = GlassQuality.High,
)
