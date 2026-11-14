package com.example.simplebrowser.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simplebrowser.R
import com.example.simplebrowser.sync.SyncRepository
import com.example.simplebrowser.ui.theme.Spacing
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WebDAV 同步页：填服务器/账号 → 测试连接 → 上传或下载。
 *
 * 整包覆盖，没有自动合并；两个方向都会覆盖对方，界面上把这点写清楚。
 */
@Composable
fun WebDavScreen(
    sync: SyncRepository,
    onBack: () -> Unit,
) {
    val config by sync.config.collectAsState()
    val lastSyncAt by sync.lastSyncAt.collectAsState()
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(config.baseUrl) }
    var user by remember { mutableStateOf(config.user) }
    var password by remember { mutableStateOf(config.password) }
    var dir by remember { mutableStateOf(config.directory) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun persist() {
        sync.saveConfig(
            config.copy(
                baseUrl = url.trim(),
                user = user.trim(),
                password = password,
                directory = dir.trim().ifBlank { "Allegro" },
            ),
        )
    }

    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    ScreenScaffold(title = stringResource(R.string.sync_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        ) {
            Text(
                stringResource(R.string.sync_intro),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Field(
                    title = stringResource(R.string.sync_url),
                    hint = "https://dav.example.com/remote.php/dav/files/me/",
                    value = url,
                    onValueChange = { url = it },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                Field(
                    title = stringResource(R.string.sync_user),
                    hint = stringResource(R.string.sync_user_hint),
                    value = user,
                    onValueChange = { user = it },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                Field(
                    title = stringResource(R.string.sync_password),
                    hint = stringResource(R.string.sync_password_hint),
                    value = password,
                    onValueChange = { password = it },
                    masked = true,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                Field(
                    title = stringResource(R.string.sync_dir),
                    hint = "Allegro",
                    value = dir,
                    onValueChange = { dir = it },
                )
            }

            Spacer(Modifier.height(Spacing.m))
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.sync_test),
                    summary = stringResource(R.string.sync_test_summary),
                    onClick = {
                        persist()
                        busy = true
                        status = null
                        scope.launch {
                            val result = sync.testConnection()
                            status = result.fold(
                                onSuccess = { "连接正常" },
                                onFailure = { it.message ?: "连接失败" },
                            )
                            busy = false
                        }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                ArrowPreference(
                    title = stringResource(R.string.sync_upload),
                    summary = stringResource(R.string.sync_upload_summary),
                    onClick = {
                        persist()
                        busy = true
                        status = null
                        scope.launch {
                            val result = sync.upload()
                            status = result.fold(
                                onSuccess = { "已上传 ${it.bookmarks} 条书签 / ${it.folders} 个文件夹 / ${it.sites} 个站点设置" },
                                onFailure = { "上传失败：${it.message}" },
                            )
                            busy = false
                        }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                ArrowPreference(
                    title = stringResource(R.string.sync_download),
                    summary = stringResource(R.string.sync_download_summary),
                    onClick = {
                        persist()
                        busy = true
                        status = null
                        scope.launch {
                            val result = sync.download()
                            status = result.fold(
                                onSuccess = { "已恢复 ${it.bookmarks} 条书签 / ${it.folders} 个文件夹 / ${it.sites} 个站点设置" },
                                onFailure = { "恢复失败：${it.message}" },
                            )
                            busy = false
                        }
                    },
                )
            }

            Spacer(Modifier.height(Spacing.m))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.sync_status),
                    summary = when {
                        busy -> stringResource(R.string.sync_working)
                        status != null -> status
                        lastSyncAt > 0 -> stringResource(R.string.sync_last, formatter.format(Date(lastSyncAt)))
                        else -> stringResource(R.string.sync_never)
                    },
                )
            }

            Text(
                stringResource(R.string.sync_note),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = Spacing.m, start = Spacing.xs),
            )
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

@Composable
private fun Field(
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    masked: Boolean = false,
) {
    BasicComponent(
        title = title,
        summary = if (masked && value.isNotEmpty()) "••••••" else hint,
        bottomAction = {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = hint,
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
