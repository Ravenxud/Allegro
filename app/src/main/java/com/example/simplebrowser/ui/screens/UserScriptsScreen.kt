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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simplebrowser.R
import com.example.simplebrowser.data.UserScript
import com.example.simplebrowser.data.UserScriptStore
import com.example.simplebrowser.ui.components.EmptyState
import com.example.simplebrowser.ui.components.MiuixSheet
import com.example.simplebrowser.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.HttpURLConnection
import java.net.URL

/** 用户脚本管理：安装（URL / 粘贴）、启用开关、删除。 */
@Composable
fun UserScriptsScreen(
    store: UserScriptStore,
    onBack: () -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    val scripts = remember(version) { store.all() }
    var showInstall by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ScreenScaffold(
        title = stringResource(R.string.userscript_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showInstall = true }) {
                Icon(MiuixIcons.Add, stringResource(R.string.userscript_install))
            }
        },
    ) { modifier ->
        if (scripts.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Folder,
                title = stringResource(R.string.userscript_empty),
                subtitle = stringResource(R.string.userscript_empty_hint),
            )
        } else {
            LazyColumn(
                modifier = modifier.padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(scripts, key = { it.id }) { script ->
                    ScriptRow(
                        script = script,
                        onToggle = { enabled ->
                            store.setEnabled(script.id, enabled)
                            version++
                        },
                        onDelete = {
                            store.delete(script.id)
                            version++
                        },
                    )
                }
                item { Spacer(Modifier.height(Spacing.l)) }
            }
        }
    }

    if (showInstall) {
        InstallDialog(
            onDismiss = { showInstall = false },
            onInstallUrl = { raw ->
                scope.launch {
                    val text = withContext(Dispatchers.IO) { download(raw) }
                    val installed = text?.let { store.install(it) }
                    showInstall = false
                    version++
                    Toast.makeText(
                        context,
                        if (installed != null) context.getString(R.string.userscript_installed, installed.name)
                        else context.getString(R.string.userscript_install_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onInstallText = { code ->
                val installed = store.install(code)
                showInstall = false
                version++
                Toast.makeText(
                    context,
                    if (installed != null) context.getString(R.string.userscript_installed, installed.name)
                    else context.getString(R.string.userscript_install_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
}

@Composable
private fun ScriptRow(script: UserScript, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    script.name,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    script.matches.joinToString(" "),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Switch(checked = script.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(MiuixIcons.Delete, null)
            }
        }
    }
}

@Composable
private fun InstallDialog(
    onDismiss: () -> Unit,
    onInstallUrl: (String) -> Unit,
    onInstallText: (String) -> Unit,
) {
    var mode by remember { mutableStateOf(0) } // 0=URL, 1=粘贴
    var input by remember { mutableStateOf("") }

    MiuixSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.userscript_install)) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Button(onClick = { mode = 0 }, enabled = mode != 0) { Text("URL") }
                Button(onClick = { mode = 1 }, enabled = mode != 1) { Text(stringResource(R.string.userscript_paste)) }
            }
            TextField(
                value = input,
                onValueChange = { input = it },
                label = if (mode == 0) "https://…/script.user.js" else stringResource(R.string.userscript_paste_hint),
                useLabelAsPlaceholder = true,
                singleLine = mode == 0,
                maxLines = if (mode == 0) 1 else 10,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (input.isBlank()) return@Button
                    if (mode == 0) onInstallUrl(input.trim()) else onInstallText(input)
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.userscript_do_install))
            }
        }
    }
}

private fun download(url: String): String? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 15000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Allegro")
    }
    connection.inputStream.bufferedReader().use { it.readText() }
}.getOrNull()
