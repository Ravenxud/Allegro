package com.ravenxud.allegro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.browser.BrowserController
import com.ravenxud.allegro.ui.components.EmptyState
import com.ravenxud.allegro.ui.theme.Spacing
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton

import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 标签分组管理。
 *
 * 数据模型刻意保持最简：分组只有名字 + 折叠状态，成员关系存在 `tabs.groupId`。
 * 删除分组不删标签，成员回到「未分组」——这是最不容易让用户丢数据的行为。
 */
@Composable
fun TabGroupsScreen(
    controller: BrowserController,
    onBack: () -> Unit,
) {
    val groupsState by controller.state.collectAsState()
    val groups = groupsState.groups
    val tabs = groupsState.tabs
    val scope = rememberCoroutineScope()
    val input = remember { mutableStateOf("") }

    ScreenScaffold(title = stringResource(R.string.groups_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        ) {
            Text(
                stringResource(R.string.groups_intro),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    TextField(
                        value = input.value,
                        onValueChange = { input.value = it },
                        singleLine = true,
                        label = stringResource(R.string.groups_name_hint),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.weight(1f),
                    )
                    ArrowPreference(
                        title = stringResource(R.string.groups_create),
                        onClick = {
                            val name = input.value.trim()
                            if (name.isNotEmpty()) {
                                scope.launch {
                                    controller.createTabGroup(name)
                                    input.value = ""
                                }
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.m))
            Text(
                stringResource(R.string.groups_existing),
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            if (groups.isEmpty()) {
                EmptyState(
                    icon = MiuixIcons.Folder,
                    title = stringResource(R.string.groups_empty),
                    subtitle = stringResource(R.string.groups_empty_hint),
                )
            } else {
                groups.forEach { group ->
                    val count = tabs.count { it.groupId == group.id }
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s)) {
                        Row(
                            modifier = Modifier.padding(
                                start = Spacing.l,
                                end = Spacing.xs,
                                top = Spacing.s,
                                bottom = Spacing.s,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(group.name, fontSize = 15.sp, maxLines = 1)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.groups_count, count),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (group.collapsed) R.string.groups_expand else R.string.groups_collapse,
                                ),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = Spacing.s).clickable {
                                    scope.launch { controller.setTabGroupCollapsed(group.id, !group.collapsed) }
                                },
                            )
                            IconButton(onClick = {
                                scope.launch { controller.deleteTabGroup(group.id) }
                            }) {
                                Icon(MiuixIcons.Close, stringResource(R.string.groups_delete))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}
