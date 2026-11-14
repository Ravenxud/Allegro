package com.ravenxud.allegro.ui.screens

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.data.entity.HistoryEntity
import com.ravenxud.allegro.data.repo.HistoryRepository
import com.ravenxud.allegro.ui.components.EmptyState
import com.ravenxud.allegro.ui.theme.Spacing
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 历史记录：按「今天 / 昨天 / 更早」分组，每行显示标题、站点与时间。
 * 相比旧版：增大行内边距、单独时间列、组间留白。
 */
@Composable
fun HistoryScreen(
    repository: HistoryRepository,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val items by repository.observeRecent(300).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val grouped = androidx.compose.runtime.remember(items) { groupByDay(items) }

    ScreenScaffold(
        title = stringResource(R.string.screen_history),
        onBack = onBack,
        actions = {
            if (items.isNotEmpty()) {
                IconButton(onClick = { scope.launch { repository.clear() } }) {
                    Icon(MiuixIcons.Close, stringResource(R.string.clear_history))
                }
            }
        },
    ) { modifier ->
        if (items.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Refresh,
                title = stringResource(R.string.empty_history),
                subtitle = stringResource(R.string.empty_history_hint),
            )
        } else {
            LazyColumn(
                modifier = modifier.padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                grouped.forEach { (label, list) ->
                    item(key = "header_$label") {
                        Text(
                            text = label,
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = Spacing.m, bottom = Spacing.xs, start = Spacing.xs),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    items(list, key = { it.url }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onOpen = { onOpen(entry.url) },
                            onDelete = { scope.launch { repository.delete(entry.url) } },
                        )
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(Spacing.l)) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifBlank { entry.url },
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = hostOf(entry.url),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(0.dp))
            Text(
                text = timeOf(entry.lastVisitedAt),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = Spacing.s),
            )
            IconButton(onClick = onDelete) {
                Icon(MiuixIcons.Close, null)
            }
        }
    }
}

private fun groupByDay(items: List<HistoryEntity>): List<Pair<String, List<HistoryEntity>>> {
    if (items.isEmpty()) return emptyList()
    val today = startOfDay(0)
    val yesterday = startOfDay(-1)
    val buckets = linkedMapOf<String, MutableList<HistoryEntity>>()
    items.forEach { item ->
        val label = when {
            item.lastVisitedAt >= today -> "今天"
            item.lastVisitedAt >= yesterday -> "昨天"
            else -> "更早"
        }
        buckets.getOrPut(label) { mutableListOf() } += item
    }
    return buckets.map { it.key to it.value }
}

private fun startOfDay(offsetDays: Int): Long {
    val calendar = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun timeOf(timestamp: Long): String = timeFormat.format(Date(timestamp))

private fun hostOf(url: String): String {
    val schemeEnd = url.indexOf("://")
    val rest = if (schemeEnd > 0) url.substring(schemeEnd + 3) else url
    val end = rest.indexOfFirst { it == '/' || it == '?' }
    return if (end >= 0) rest.substring(0, end) else rest
}
