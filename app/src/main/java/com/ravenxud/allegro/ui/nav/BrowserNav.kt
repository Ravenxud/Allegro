package com.ravenxud.allegro.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ravenxud.allegro.ui.SecondaryScreen
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

/**
 * 浏览器根路由。
 *
 * 为什么根页也要是路由：`NavDisplay` **不允许空栈**（ADR-17，真机抛
 * `IllegalArgumentException: NavDisplay back stack cannot be empty`）。
 * 所以栈结构恒为 `[BrowserRoot, (二级页…)]`。
 */
/**
 * 转场节奏：沿用 miuix 默认的**视觉**（形状不变），只把时长拉长。
 *
 * 默认时长偏快（进二级页几乎是"闪一下"），这里统一到 560ms 提交、320ms 取消。
 */
private val SoftMotion = NavMotion(
    commit = NavSettleSpec.Tween(560, FastOutSlowInEasing),
    cancel = NavSettleSpec.Tween(320, FastOutLinearInEasing),
    programmatic = NavSettleSpec.Tween(560, FastOutSlowInEasing),
)

private val SoftTransition: NavTransition =
    object : NavTransition by NavTransitions.MiuixDefault {
        override val motion: NavMotion get() = SoftMotion
    }

data object BrowserRoot : NavKey

/** 导航栈：唯一事实源。栈底恒为 [BrowserRoot]。 */
@Composable
fun rememberBrowserStack(): SnapshotStateList<NavKey> =
    remember { mutableStateListOf<NavKey>(BrowserRoot) }

/** 当前是否在某个二级页上（栈深 > 1）。 */
val SnapshotStateList<NavKey>.onSecondaryPage: Boolean
    get() = size > 1

/** 栈顶的二级页；在根页返回 null。 */
fun SnapshotStateList<NavKey>.currentSecondary(): SecondaryScreen? =
    (lastOrNull() as? SecondaryRoute)?.screen

/** 进入二级页：push（返回时回到上一页，符合"Settings → 许可证 → 返回回 Settings"）。 */
fun SnapshotStateList<NavKey>.pushSecondary(screen: SecondaryScreen) {
    if (screen == SecondaryScreen.BROWSER) return
    val route = SecondaryRoute(screen)
    if (lastOrNull() != route) add(route)
}

/** 返回一层：只在栈深 > 1 时 pop，避免把 Root 弹掉（库不允许空栈）。 */
fun SnapshotStateList<NavKey>.popSecondary(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}

/**
 * 根页 + 二级页的宿主。
 *
 * 结构：`NavDisplay` 的 Root entry 渲染 [rootContent]（顶栏 + WebView + 底栏），
 * 二级页作为 push 进来的 entry，转场 / 系统返回 / 边缘滑动 / 转场期输入拦截由库统一处理。
 *
 * 本文件只定义契约与宿主；`BrowserScreen` 的接入是下一步（一次性原子替换）。
 */
@Composable
fun BrowserNavHost(
    stack: SnapshotStateList<NavKey>,
    onBack: () -> Boolean,
    rootContent: @Composable () -> Unit,
    secondaryContent: @Composable (SecondaryScreen) -> Unit,
) {
    NavDisplay(
        backStack = stack,
        onBack = { onBack() },
        transition = SoftTransition,
    ) {
        entry<BrowserRoot> {
            rootContent()
        }
        entry<SecondaryRoute> { route ->
            secondaryContent(route.screen)
        }
    }
}
