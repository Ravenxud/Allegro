package com.ravenxud.allegro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.adblock.AdBlockRepository
import com.ravenxud.allegro.ui.components.EmptyState
import com.ravenxud.allegro.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 隐私报告：本机拦截总数 + 按域名 Top 榜 + 本地隐藏规则管理。
 *
 * 数据全部落 SharedPreferences，不联网；清空统计与删除规则都是即时生效的。
 */
@Composable
fun PrivacyReportScreen(
    adBlock: AdBlockRepository,
    onBack: () -> Unit,
) {
    // 本地规则与域名榜不是 Flow，用版本号触发重新读取
    var version by remember { mutableIntStateOf(0) }
    val totalRaw by adBlock.blocked.collectAsState()
    // 数字滚动：避免数值跳变（B9）
    val total by androidx.compose.animation.core.animateIntAsState(
        targetValue = totalRaw,
        animationSpec = androidx.compose.animation.core.tween(
            com.ravenxud.allegro.ui.theme.Motion.Duration.Standard,
        ),
        label = "blockedTotal",
    )
    val stats = remember(version) { adBlock.stats(limit = 20) }
    val hostCount = remember(version) { adBlock.trackedHostCount() }
    val rules = remember(version) { adBlock.localCosmeticRules() }

    ScreenScaffold(
        title = stringResource(R.string.privacy_report_title),
        onBack = onBack,
        actions = {
            if (total > 0 || stats.isNotEmpty()) {
                IconButton(onClick = {
                    adBlock.resetStats()
                    version++
                }) {
                    Icon(MiuixIcons.Delete, stringResource(R.string.privacy_clear_stats))
                }
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.l)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                stringResource(R.string.privacy_report_total, total),
                                fontSize = 26.sp,
                            )
                        }
                        if (hostCount > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.privacy_report_hosts, hostCount),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.privacy_report_hint),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            if (stats.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.privacy_report_top),
                        modifier = Modifier.padding(top = Spacing.m, start = Spacing.xs),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                items(stats, key = { it.first }) { (host, count) ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                host,
                                Modifier.weight(1f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("$count", fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.m, start = Spacing.xs, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.picker_rules_title),
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (rules.isNotEmpty()) {
                        Text(
                            stringResource(R.string.picker_clear_rules),
                            modifier = Modifier.clickable {
                                adBlock.clearLocalCosmeticRules()
                                version++
                            },
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    EmptyState(
                        icon = MiuixIcons.Blocklist,
                        title = stringResource(R.string.picker_rules_empty),
                        subtitle = stringResource(R.string.picker_rules_empty_hint),
                    )
                }
            } else {
                items(rules, key = { it }) { rule ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(
                                start = Spacing.l,
                                end = Spacing.xs,
                                top = Spacing.xs,
                                bottom = Spacing.xs,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                rule,
                                Modifier.weight(1f),
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = {
                                adBlock.removeLocalCosmeticRule(rule)
                                version++
                            }) {
                                Icon(MiuixIcons.Close, stringResource(R.string.picker_remove_rule))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.l)) }
        }
    }
}
