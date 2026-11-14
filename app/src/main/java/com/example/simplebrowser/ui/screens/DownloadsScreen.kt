package com.example.simplebrowser.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simplebrowser.R
import com.example.simplebrowser.data.entity.DownloadEntity
import com.example.simplebrowser.data.repo.DownloadRepository
import com.example.simplebrowser.download.DownloadCenter
import com.example.simplebrowser.download.DownloadProgress
import com.example.simplebrowser.download.DownloadStatus
import com.example.simplebrowser.ui.components.EmptyState
import com.example.simplebrowser.ui.theme.Spacing
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 下载列表：按「进行中 / 已完成」分组，单行只保留一个主操作，
 * 进度信息压在第二行右对齐，降低拥挤感。
 */
@Composable
fun DownloadsScreen(
    repository: DownloadRepository,
    center: DownloadCenter,
    onBack: () -> Unit,
) {
    val items by repository.observeAll().collectAsState(initial = emptyList())
    val progress by center.progress.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val active = items.filter { it.status in ACTIVE_STATUS }
    val finished = items.filter { it.status !in ACTIVE_STATUS }

    ScreenScaffold(title = stringResource(R.string.screen_downloads), onBack = onBack) { modifier ->
        if (items.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Download,
                title = stringResource(R.string.empty_downloads),
                subtitle = stringResource(R.string.empty_downloads_hint),
            )
        } else {
            LazyColumn(
                modifier = modifier.padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                if (active.isNotEmpty()) {
                    item(key = "header_active") {
                        GroupHeader(stringResource(R.string.download_group_active, active.size))
                    }
                    items(active, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            live = progress[item.id],
                            onPause = { center.pause(item.id) },
                            onResume = { center.resume(item.id) },
                            onRetry = { center.retry(item.id) },
                            onOpen = {
                                val intent = center.openIntent(item.id)
                                if (intent != null) {
                                    runCatching { context.startActivity(intent) }
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.download_open_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onDelete = {
                                if (item.status in ACTIVE_STATUS) {
                                    center.cancel(item.id)
                                } else {
                                    scope.launch { repository.delete(item.id) }
                                }
                            },
                        )
                    }
                }

                if (finished.isNotEmpty()) {
                    item(key = "header_done") {
                        GroupHeader(stringResource(R.string.download_group_finished, finished.size))
                    }
                    items(finished, key = { it.id }) { item ->
                        DownloadCard(
                            item = item,
                            live = progress[item.id],
                            onPause = { center.pause(item.id) },
                            onResume = { center.resume(item.id) },
                            onRetry = { center.retry(item.id) },
                            onOpen = {
                                val intent = center.openIntent(item.id)
                                if (intent != null) {
                                    runCatching { context.startActivity(intent) }
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.download_open_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onDelete = { scope.launch { repository.delete(item.id) } },
                        )
                    }
                }

                item(key = "bottom_space") { Spacer(Modifier.height(Spacing.l)) }
            }
        }
    }
}

private val ACTIVE_STATUS = setOf(DownloadStatus.RUNNING, DownloadStatus.PENDING, DownloadStatus.PAUSED)

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s, start = Spacing.xs),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun DownloadCard(
    item: DownloadEntity,
    live: DownloadProgress?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val running = item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.PENDING
    val percent = live?.percent ?: storedPercent(item)
    val showBar = item.status in ACTIVE_STATUS && item.status != DownloadStatus.PAUSED || item.status == DownloadStatus.PAUSED

    Card(
        onClick = { if (item.status == DownloadStatus.DONE) onOpen() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusLine(item, percent),
                        fontSize = 12.sp,
                        color = statusColor(item.status),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(0.dp))
                when {
                    running -> {
                        IconButton(onClick = onPause) {
                            Icon(MiuixIcons.Pause, stringResource(R.string.download_pause))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(MiuixIcons.Close, stringResource(R.string.download_cancel))
                        }
                    }
                    item.status == DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(MiuixIcons.Play, stringResource(R.string.download_resume))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(MiuixIcons.Close, stringResource(R.string.download_cancel))
                        }
                    }
                    item.status == DownloadStatus.FAILED -> {
                        IconButton(onClick = onRetry) {
                            Icon(MiuixIcons.Refresh, stringResource(R.string.download_retry))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(MiuixIcons.Close, null)
                        }
                    }
                    else -> IconButton(onClick = onDelete) {
                        Icon(MiuixIcons.Close, null)
                    }
                }
            }

            if (showBar) {
                Spacer(Modifier.height(Spacing.s))
                LinearProgressIndicator(
                    progress = percent / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$percent%",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.weight(1f))
                    val extra = buildString {
                        if (live != null && live.speedBytesPerSec > 0) append(humanSpeed(live.speedBytesPerSec))
                        if (live != null && live.etaSeconds > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("剩余 ").append(humanEta(live.etaSeconds))
                        }
                    }
                    if (extra.isNotEmpty()) {
                        Text(
                            text = extra,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLine(item: DownloadEntity, percent: Int): String = when (item.status) {
    DownloadStatus.DONE -> stringResource(R.string.download_status_done)
    DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
    DownloadStatus.CANCELED -> stringResource(R.string.download_status_canceled)
    DownloadStatus.PAUSED -> stringResource(R.string.download_status_paused, percent)
    else -> stringResource(R.string.download_status_running, percent)
}

@Composable
private fun statusColor(status: Int) = when (status) {
    DownloadStatus.FAILED -> MiuixTheme.colorScheme.error
    DownloadStatus.DONE -> MiuixTheme.colorScheme.primary
    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

private fun storedPercent(item: DownloadEntity): Int =
    if (item.totalBytes > 0) ((item.bytesSoFar * 100) / item.totalBytes).toInt().coerceIn(0, 100) else 0

private fun humanSpeed(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0
    return if (kb >= 1024) String.format("%.1f MB/s", kb / 1024) else String.format("%.0f KB/s", kb)
}

private fun humanEta(seconds: Long): String =
    if (seconds >= 60) "${seconds / 60} 分 ${seconds % 60} 秒" else "${seconds} 秒"
