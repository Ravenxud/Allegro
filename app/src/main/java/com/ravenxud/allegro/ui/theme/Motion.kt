package com.ravenxud.allegro.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing as ComposeEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 全站唯一动效来源（U1）。
 *
 * 规矩：`app/src/main` 里除本文件外，**不允许再出现 `tween(`**。
 * 需要动效就调这里的工厂方法，不要在调用点拼参数。
 *
 * 时长档位说明：现有界面里的历史取值是 120/140/150/160/180/200/220/250/260/320 十种，
 * 这里收敛成 5 档；个别位置最多有 ±20ms 的微调（肉眼不可辨），换取"参数只有一处"。
 * 若以后觉得某处手感变了，**给那一处加具名令牌**，不要退回去写裸数字。
 */
object Motion {

    /** 时长（毫秒）。 */
    object Duration {
        /** 按下反馈、图标高亮。 */
        const val Instant = 120

        /** 小面积进出：淡出、指示器。 */
        const val Quick = 160

        /** 标准进出：面板、菜单、颜色过渡。 */
        const val Standard = 200

        /** 较大位移：整页滑入、sheet 进出。 */
        const val Emphasized = 260

        /** 全屏级转场：二级页、首页入场。 */
        const val Long = 320
    }

    /** 缓动曲线。 */
    object Easing {
        /** 与 Compose 的 FastOutSlowInEasing 完全一致（0.4,0,0.2,1），保证历史观感不变。 */
        val Standard: ComposeEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

        /** 进场：先快后慢。 */
        val Decelerate: ComposeEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

        /** 出场：先慢后快。 */
        val Accelerate: ComposeEasing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

        /** 带轻微过冲，用于落位/收起。 */
        val Overshoot: ComposeEasing = CubicBezierEasing(0.64f, 1.56f, 0.34f, 1f)
    }

    /**
     * Luna 标签页弹簧（取自其 tab switcher 常量：spring(0.9, 320) / spring(1.0, 520)）。
     * Cards 用于卡片进场与落位，Snap 用于关闭/吸附这类"要更利落"的动作。
     */
    object Tabs {
        val Cards: FiniteAnimationSpec<Float> =
            spring(dampingRatio = 0.9f, stiffness = 320f)
        val Snap: FiniteAnimationSpec<Float> =
            spring(dampingRatio = 1f, stiffness = 520f)
    }

    /** 列表增删移位的弹簧（Luna 的 Cards 曲线）。 */
    object Lists {
        val Placement: FiniteAnimationSpec<Float> =
            spring(dampingRatio = 0.9f, stiffness = 320f)

        /** 增删淡入淡出时长。 */
        const val FADE_MS = 180
    }

    /** 按压反馈强度（0.96 缩小 + 0.9 透明）。 */
    object Press {
        const val SCALE = 0.96f
        const val ALPHA = 0.9f
        const val DURATION = Duration.Instant
    }

    /**
     * 系统是否关闭了动画（开发者选项 / 无障碍「移除动画」）。
     * 为 true 时所有位移类动效压到 Instant，只保留透明度变化。
     */
    fun reduceMotion(context: android.content.Context): Boolean =
        appReduceMotion(context) || systemReduceMotion(context) || isLowRam(context)

    /** 低端机：位移类动效同样压到 Instant（原计划里的「低端机降级」）。 */
    private fun isLowRam(context: android.content.Context): Boolean = runCatching {
        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as android.app.ActivityManager).isLowRamDevice
    }.getOrDefault(false)

    /** 应用内开关（设置页「减少动画」）。 */
    private fun appReduceMotion(context: android.content.Context): Boolean = runCatching {
        context.getSharedPreferences("simple_browser_settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("reduce_motion", false)
    }.getOrDefault(false)

    private fun systemReduceMotion(context: android.content.Context): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)

    /** 跟手类场景用弹簧（可打断）。 */
    object Springs {
        val Snappy: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 380f)
        val Gentle: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 260f)
        val Bouncy: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.7f, stiffness = 420f)
    }

    /** 预测性返回的插值参数（替代散落的 0.06f / 0.25f / 28dp）。 */
    object PredictiveBack {
        const val SCALE_DROP = 0.06f
        const val ALPHA_DROP = 0.25f
        const val CORNER_RADIUS_DP = 28
    }

    // ---------------------------------------------------------------- 工厂

    /** 通用补间。 */
    fun <T> spec(
        durationMs: Int = Duration.Standard,
        easing: ComposeEasing = Easing.Standard,
    ): FiniteAnimationSpec<T> = tween(durationMillis = durationMs, easing = easing)

    /** 进场（淡入）。 */
    fun <T> enter(durationMs: Int = Duration.Standard): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = Easing.Standard)

    /** 出场（淡出），默认比进场快一档。 */
    fun <T> exit(durationMs: Int = Duration.Instant): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = Easing.Standard)

    /** 位移/滑动。 */
    fun <T> slide(durationMs: Int = Duration.Standard): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = Easing.Standard)

    /** 颜色过渡。 */
    fun <T> color(durationMs: Int = Duration.Instant): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = Easing.Standard)
}
