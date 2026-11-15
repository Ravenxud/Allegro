package com.ravenxud.allegro.ui.screens

import com.ravenxud.allegro.ui.theme.AllegroIcons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.reader.OfflinePageStore
import com.ravenxud.allegro.ui.components.EmptyState
import com.ravenxud.allegro.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 离线页面列表。 */
@Composable
fun OfflinePagesScreen(
    load: () -> List<OfflinePageStore.Entry>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var version by remember { mutableStateOf(0) }
    val items = remember(version) { load() }
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    ScreenScaffold(title = stringResource(R.string.offline_title), onBack = onBack) { modifier ->
        if (items.isEmpty()) {
            EmptyState(
                icon = AllegroIcons.Download,
                title = stringResource(R.string.offline_empty),
                subtitle = stringResource(R.string.offline_empty_hint),
            )
        } else {
            LazyColumn(
                modifier = modifier.padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(items, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry.fileName) },
                    ) {
                        Row(
                            modifier = Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title.ifBlank { entry.url },
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${formatter.format(Date(entry.savedAt))} · ${humanSize(entry.sizeBytes)}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            IconButton(onClick = {
                                onDelete(entry.id)
                                version++
                            }) {
                                Icon(AllegroIcons.Close, null)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.l)) }
            }
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
