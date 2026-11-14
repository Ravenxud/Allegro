package com.ravenxud.allegro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.browser.BrowserState
import com.ravenxud.allegro.browser.TabState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 大屏侧栏：常驻的标签列表 + 快捷入口。
 *
 * 手机上没有这块（[LayoutWidth.COMPACT]），平板/折叠屏展开时取代"标签面板"这一层。
 * 只做展示与转发，不持有任何状态。
 */
@Composable
fun TabsRail(
    state: BrowserState,
    widthDp: Int,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit,
    onNavigate: (SecondaryScreen) -> Unit,
    onToggleGroup: (String, Boolean) -> Unit = { _, _ -> },
) {
    Surface(color = MiuixTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier
                .width(widthDp.dp)
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.rail_tabs),
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                IconButton(onClick = onNew) {
                    Icon(MiuixIcons.AddCircle, stringResource(R.string.rail_new_tab), Modifier.size(22.dp))
                }
            }

            val sections = TabGrouping.sections(state.groups, state.tabs)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sections.forEach { section ->
                    if (section.group != null) {
                        item(key = "header-${section.group.id}") {
                            GroupHeader(
                                name = section.title,
                                count = section.tabs.size,
                                collapsed = section.collapsed,
                                onClick = { onToggleGroup(section.group.id, !section.collapsed) },
                            )
                        }
                    }
                    if (!section.collapsed) {
                        items(section.tabs, key = { it.id }) { tab ->
                            TabRow(
                                tab = tab,
                                active = tab.id == state.activeTabId,
                                onClick = { onSelect(tab.id) },
                                onClose = { onClose(tab.id) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            RailEntry(MiuixIcons.Folder, stringResource(R.string.screen_bookmarks)) {
                onNavigate(SecondaryScreen.BOOKMARKS)
            }
            RailEntry(MiuixIcons.Refresh, stringResource(R.string.screen_history)) {
                onNavigate(SecondaryScreen.HISTORY)
            }
            RailEntry(MiuixIcons.Download, stringResource(R.string.screen_downloads)) {
                onNavigate(SecondaryScreen.DOWNLOADS)
            }
            RailEntry(MiuixIcons.Settings, stringResource(R.string.screen_settings)) {
                onNavigate(SecondaryScreen.SETTINGS)
            }
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    count: Int,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(com.ravenxud.allegro.ui.theme.Shapes.Tiny)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (if (collapsed) "▸ " else "▾ ") + name,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("$count", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun TabRow(
    tab: TabState,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val blank = tab.url == "about:blank"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(com.ravenxud.allegro.ui.theme.Shapes.Tiny)
            .background(
                if (active) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (blank) stringResource(R.string.tab_new_page) else tab.title.ifBlank { tab.url },
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (blank) "" else tab.url,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        IconButton(onClick = onClose) {
            Icon(MiuixIcons.Close, stringResource(R.string.cd_close_tab), Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RailEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(com.ravenxud.allegro.ui.theme.Shapes.Tiny)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
