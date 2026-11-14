package com.example.simplebrowser.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simplebrowser.R
import com.example.simplebrowser.data.entity.BookmarkEntity
import com.example.simplebrowser.data.entity.BookmarkFolderEntity
import com.example.simplebrowser.data.repo.BookmarkRepository
import com.example.simplebrowser.ui.components.EmptyState
import com.example.simplebrowser.ui.components.MiuixSheet
import com.example.simplebrowser.ui.components.SheetRow
import com.example.simplebrowser.ui.theme.Spacing
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 书签页：文件夹筛选 + 移动/删除 + 导入导出入口（由外层提供）。 */
@Composable
fun BookmarksScreen(
    repository: BookmarkRepository,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val items by repository.observeAll().collectAsState(initial = emptyList())
    val folders by repository.observeFolders().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var selectedFolderId by remember { mutableStateOf<String?>(ALL) }
    var createFolderDialog by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<BookmarkEntity?>(null) }

    val filtered = items.filter { bookmark ->
        when (selectedFolderId) {
            ALL -> true
            UNFILED -> bookmark.folderId == null
            else -> bookmark.folderId == selectedFolderId
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.screen_bookmarks),
        onBack = onBack,
        actions = {
            IconButton(onClick = onImport) {
                Icon(MiuixIcons.Add, stringResource(R.string.bookmark_import))
            }
            IconButton(onClick = onExport) {
                Icon(MiuixIcons.Folder, stringResource(R.string.bookmark_export))
            }
        },
    ) { modifier ->
        Column(modifier) {
            FolderChips(
                folders = folders,
                selected = selectedFolderId,
                onSelect = { selectedFolderId = it },
                onCreate = { createFolderDialog = true },
            )

            if (filtered.isEmpty()) {
                EmptyState(
                    icon = MiuixIcons.Folder,
                    title = stringResource(R.string.empty_bookmarks),
                    subtitle = stringResource(R.string.empty_bookmarks_hint),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(filtered, key = { it.url }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            folderName = folders.firstOrNull { it.id == bookmark.folderId }?.name,
                            onOpen = { onOpen(bookmark.url) },
                            onLongPress = { menuTarget = bookmark },
                        )
                    }
                    item { Spacer(Modifier.height(Spacing.l)) }
                }
            }
        }
    }

    if (createFolderDialog) {
        FolderNameDialog(
            onDismiss = { createFolderDialog = false },
            onConfirm = { name ->
                createFolderDialog = false
                scope.launch { repository.createFolder(name) }
            },
        )
    }

    val target = menuTarget
    MiuixSheet(
        visible = target != null,
        onDismiss = { menuTarget = null },
        title = target?.title,
    ) {
        if (target != null) {
            SheetRow(MiuixIcons.Folder, stringResource(R.string.bookmark_move_unfiled)) {
                scope.launch { repository.moveToFolder(target.url, null) }
                menuTarget = null
            }
            folders.forEach { folder ->
                SheetRow(MiuixIcons.Folder, folder.name) {
                    scope.launch { repository.moveToFolder(target.url, folder.id) }
                    menuTarget = null
                }
            }
            SheetRow(MiuixIcons.Delete, stringResource(R.string.bookmark_delete)) {
                scope.launch { repository.remove(target.url) }
                menuTarget = null
            }
        }
    }
}

private const val ALL = "__all__"
private const val UNFILED = "__unfiled__"

@Composable
private fun FolderChips(
    folders: List<BookmarkFolderEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(stringResource(R.string.bookmark_all), selected == ALL) { onSelect(ALL) }
        Chip(stringResource(R.string.bookmark_unfiled), selected == UNFILED) { onSelect(UNFILED) }
        folders.forEach { folder ->
            Chip(folder.name, selected == folder.id) { onSelect(folder.id) }
        }
        IconButton(onClick = onCreate) {
            Icon(MiuixIcons.Add, stringResource(R.string.bookmark_new_folder))
        }
    }
}

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        cornerRadius = 16.dp,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (active) MiuixTheme.colorScheme.onPrimary
            else MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    folderName: String?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        onClick = onOpen,
        onLongPress = onLongPress,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.m, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = bookmark.title.ifBlank { bookmark.url },
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(hostOf(bookmark.url))
                        if (!folderName.isNullOrBlank()) append(" · ").append(folderName)
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(MiuixIcons.Folder, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun FolderNameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    MiuixSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.bookmark_new_folder)) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = stringResource(R.string.bookmark_folder_name),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.m))
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.bookmark_create))
            }
        }
    }
}

private fun hostOf(url: String): String {
    val schemeEnd = url.indexOf("://")
    val rest = if (schemeEnd > 0) url.substring(schemeEnd + 3) else url
    val end = rest.indexOfFirst { it == '/' || it == '?' }
    return if (end >= 0) rest.substring(0, end) else rest
}
