package com.ravenxud.allegro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ravenxud.allegro.ui.components.GroupedCard
import com.ravenxud.allegro.ui.components.SectionLabel
import com.ravenxud.allegro.ui.components.SettingRow
import com.ravenxud.allegro.ui.theme.Dimens
import com.ravenxud.allegro.ui.theme.TypeScale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 试点路由：只用类型区分，不承载数据。 */
private data object PilotRoot : NavKey

private data object PilotDetail : NavKey

/**
 * miuix-nav 试点页（只用于验证，不参与生产导航）。
 *
 * 正确的公共 API 是 **DSL 形式**：
 * ```
 * NavDisplay(backStack = ..., onBack = ...) {
 *     entry<Route> { route -> ... }
 * }
 * ```
 * `entryProvider(...)` 是内部辅助函数，外部不可用（0.63.1 曾误用它导致判定"API 未公开"）。
 */
@Composable
fun NavPilotScreen(onBack: () -> Unit) {
    val backStack = remember { androidx.compose.runtime.mutableStateListOf<NavKey>(PilotRoot) }
    val controller = remember(backStack) { NavController(backStack) }

    ScreenScaffold(title = "转场实验", onBack = onBack) { inner ->
    NavDisplay(
        backStack = backStack,
        modifier = inner,
        onBack = { if (!controller.pop()) onBack() },
    ) {
        entry<PilotRoot> {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.PagePadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.GroupGap),
            ) {
                Text(
                    text = "验证 NavDisplay 的转场、系统返回与边缘手势；不参与正式导航。",
                    modifier = Modifier.padding(start = 6.dp),
                    fontSize = TypeScale.Secondary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                SectionLabel("栈状态")
                GroupedCard {
                    SettingRow(
                        title = "当前栈深：${backStack.size}",
                        summary = "栈顶：${backStack.last()}（栈底恒为 Root）",
                    ) { }
                }
                SectionLabel("操作")
                GroupedCard {
                    SettingRow(
                        title = "push 一个详情页",
                        summary = "观察转场动画",
                        onClick = { controller.push(PilotDetail) },
                    )
                    SettingRow(
                        title = "replace 成详情页",
                        summary = "验证二级页之间跳转不叠栈",
                        onClick = {
                            if (backStack.isEmpty()) backStack.add(PilotDetail)
                            else backStack[backStack.lastIndex] = PilotDetail
                        },
                    )
                    SettingRow(
                        title = "弹回栈底（保留 Root）",
                        summary = "NavDisplay 不允许空栈：栈底必须是 Root",
                        onClick = {
                            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        },
                    )
                    SettingRow(
                        title = "退出试点",
                        summary = "返回设置",
                        onClick = onBack,
                    )
                }
            }
        }
        entry<PilotDetail> {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.PagePadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.GroupGap),
            ) {
                Text(
                    text = "详情页",
                    modifier = Modifier.padding(top = 12.dp, start = 6.dp),
                    fontSize = TypeScale.Title,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "按系统返回键，或从屏幕左缘右滑，观察是否由 NavDisplay 接管。",
                    modifier = Modifier.padding(start = 6.dp),
                    fontSize = TypeScale.Secondary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                GroupedCard {
                    SettingRow(
                        title = "返回上一页",
                        summary = "controller.pop()",
                        onClick = { controller.pop() },
                    )
                }
                Text(
                    text = "",
                    modifier = Modifier.padding(bottom = 48.dp),
                )
            }
        }
    }
    }
}
