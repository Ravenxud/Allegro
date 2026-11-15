package com.ravenxud.allegro.ui.screens
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import com.ravenxud.allegro.ui.design.AllegroCard
import com.ravenxud.allegro.ui.design.AllegroRow
import com.ravenxud.allegro.ui.design.AllegroTopBar
import com.ravenxud.allegro.ui.theme.Dimens
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.ravenxud.allegro.R
import com.ravenxud.allegro.adblock.AdBlockRepository
import com.ravenxud.allegro.adblock.FilterSources
import com.ravenxud.allegro.data.SettingsRepository
import com.ravenxud.allegro.update.UpdateRepository
import com.ravenxud.allegro.update.UpdateState
import com.ravenxud.allegro.data.model.SearchEngines
import com.ravenxud.allegro.data.model.ThemeMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.ravenxud.allegro.ui.components.GroupedCard
import com.ravenxud.allegro.ui.components.SectionLabel
import com.ravenxud.allegro.ui.components.SettingRow

/**
 * 设置页：SmallTopAppBar + Card(BasicComponent) 组件组，
 * 主题/搜索引擎用 RadioButtonPreference，数据清理用 ArrowPreference。
 */
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    adBlock: AdBlockRepository,
    update: UpdateRepository,
    onBack: () -> Unit,
    onClearData: () -> Unit,
    onOpenSync: () -> Unit = {},
    onOpenSiteRules: () -> Unit = {},
    onOpenShaderSmoke: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    onOpenNavPilot: () -> Unit = {},
    onTextZoomChange: (Int) -> Unit = {},
) {
    val theme by settings.themeMode.collectAsState()
    val engineId by settings.searchEngineId.collectAsState()
    val context = LocalContext.current
    // 配置变化时 context 里的 resources 可能是旧的，用 Compose 的 LocalResources 才符合 lint 要求
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val blockEnabled by adBlock.enabled.collectAsState()
    val blockedCount by adBlock.blocked.collectAsState()
    val showHomeBookmarks by settings.homeBookmarksEnabled.collectAsState()
    val textZoom by settings.textZoom.collectAsState()
    val reduceMotion by settings.reduceMotion.collectAsState()
    var showZoomDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val alwaysDesktop by settings.alwaysDesktop.collectAsState()
    val desktopUa by settings.desktopUa.collectAsState()
    val updateState by update.state.collectAsState()
    val repoInput = remember { androidx.compose.runtime.mutableStateOf(settings.updateRepo()) }

    // 内核信息（系统 WebView 版本/提供方）
    val kernel = remember {
        runCatching {
            val info = android.webkit.WebView.getCurrentWebViewPackage()
            (info?.versionName ?: "未知") to (info?.packageName ?: "未知")
        }.getOrDefault("未知" to "未知")
    }
    val updating by adBlock.updating.collectAsState()
    var updateMessage = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    // 方案 B：内容铺满整屏并从顶栏下穿过，顶栏浮在上面（AllegroTopBar 自带发丝线）
    // 顶栏玻璃需要一份被记录的页面内容层，故背景色 + 滚动内容一起进 layerBackdrop。
    val wallpaper by settings.wallpaperUri.collectAsState()
    val wallpaperSource by settings.wallpaperSource.collectAsState()
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            settings.setWallpaperUri(uri.toString())
        }
    }

    val topBarBackdrop = rememberLayerBackdrop()
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                // 顺序要紧：layerBackdrop 在前，背景色才被录进层里；反过来玻璃采样到透明，会渲染成灰板
                .layerBackdrop(topBarBackdrop)
                .background(MiuixTheme.colorScheme.background),
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = Dimens.TopBar),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.PagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.GroupGap),
        ) {
            item {
                SectionTitle(stringResource(R.string.settings_theme))
            }
            item {
                PreferenceCard {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) ComponentDivider()
                        RadioButtonPreference(
                            title = stringResource(mode.labelRes()),
                            selected = theme == mode,
                            onClick = { settings.setThemeMode(mode) },
                        )
                    }
                    ComponentDivider()
                    com.ravenxud.allegro.data.model.WallpaperSource.entries.forEachIndexed { index, source ->
                        if (index > 0) ComponentDivider()
                        RadioButtonPreference(
                            title = stringResource(
                                when (source) {
                                    com.ravenxud.allegro.data.model.WallpaperSource.DEFAULT -> R.string.settings_wallpaper_default
                                    com.ravenxud.allegro.data.model.WallpaperSource.BING -> R.string.settings_wallpaper_bing
                                    com.ravenxud.allegro.data.model.WallpaperSource.CUSTOM -> R.string.settings_wallpaper_custom
                                },
                            ),
                            selected = wallpaperSource == source,
                            onClick = { settings.setWallpaperSource(source) },
                        )
                    }
                    if (wallpaperSource == com.ravenxud.allegro.data.model.WallpaperSource.CUSTOM) {
                        ComponentDivider()
                        BasicComponent(
                            title = stringResource(R.string.settings_wallpaper_pick),
                            summary = if (wallpaper == null) stringResource(R.string.settings_wallpaper_summary) else stringResource(R.string.settings_wallpaper_on),
                            onClick = { pickWallpaper.launch(arrayOf("image/*")) },
                        )
                        if (wallpaper != null) {
                            ComponentDivider()
                            BasicComponent(
                                title = stringResource(R.string.settings_wallpaper_reset),
                                onClick = { settings.setWallpaperUri(null) },
                            )
                        }
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.settings_search_engine))
            }
            item {
                PreferenceCard {
                    // 0.28 改名时这段被误删，导致设置里没有搜索引擎可切
                    SearchEngines.builtIn.forEachIndexed { index, engine ->
                        if (index > 0) ComponentDivider()
                        RadioButtonPreference(
                            title = engine.name,
                            selected = engineId == engine.id,
                            onClick = { settings.setSearchEngine(engine.id) },
                        )
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.settings_browsing))
            }
            item {
                PreferenceCard {
                    AllegroRow(
                        title = "页面缩放",
                        summary = "$textZoom%",
                        onClick = { showZoomDialog = true },
                    )
                    ComponentDivider()
                    SwitchPreference(
                        checked = alwaysDesktop,
                        onCheckedChange = { settings.setAlwaysDesktop(it) },
                        title = stringResource(R.string.settings_always_desktop),
                        summary = stringResource(R.string.settings_always_desktop_summary),
                    )
                    ComponentDivider()
                    com.ravenxud.allegro.data.model.DesktopUa.entries.forEachIndexed { index, option ->
                        if (index > 0) ComponentDivider()
                        RadioButtonPreference(
                            title = stringResource(R.string.settings_desktop_ua) + " · " + option.label,
                            selected = desktopUa == option,
                            onClick = { settings.setDesktopUa(option) },
                        )
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.home_section_bookmarks))
            }
            item {
                PreferenceCard {
                    SwitchPreference(
                        checked = showHomeBookmarks,
                        onCheckedChange = { settings.setHomeBookmarksEnabled(it) },
                        title = stringResource(R.string.settings_home_bookmarks),
                    )
                    ComponentDivider()
                    SwitchPreference(
                        checked = reduceMotion,
                        onCheckedChange = { settings.setReduceMotion(it) },
                        title = "减少动画",
                        summary = "关闭位移类动效，只保留淡入淡出",
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.sync_section))
            }
            item {
                PreferenceCard {
                    AllegroRow(
                        title = stringResource(R.string.sync_title),
                        summary = stringResource(R.string.sync_entry_summary),
                        onClick = onOpenSync,
                    )
                    ComponentDivider()
                    AllegroRow(
                        title = stringResource(R.string.rules_title),
                        summary = stringResource(R.string.rules_settings_summary),
                        onClick = onOpenSiteRules,
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.adblock_section))
            }
            item {
                PreferenceCard {
                    SwitchPreference(
                        checked = blockEnabled,
                        onCheckedChange = { adBlock.setEnabled(it) },
                        title = stringResource(R.string.adblock_title),
                        summary = stringResource(R.string.adblock_summary, blockedCount),
                    )
                    ComponentDivider()
                    AllegroRow(
                        title = stringResource(R.string.adblock_update),
                        summary = when {
                            updating -> stringResource(R.string.adblock_updating)
                            updateMessage.value != null -> updateMessage.value
                            else -> stringResource(R.string.adblock_never_updated)
                        },
                        onClick = {
                            if (updating) return@AllegroRow
                            scope.launch {
                                val count = adBlock.updateAll()
                                updateMessage.value = resources.getString(
                                    if (count > 0) R.string.adblock_update_done
                                    else R.string.adblock_update_failed,
                                    count,
                                )
                            }
                        },
                    )
                    ComponentDivider()
                    AllegroRow(
                        title = stringResource(R.string.adblock_reset_stats),
                        onClick = { adBlock.resetStats() },
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.adblock_sources))
            }
            item {
                PreferenceCard {
                    FilterSources.all.forEachIndexed { index, source ->
                        if (index > 0) ComponentDivider()
                        SwitchPreference(
                            checked = adBlock.sourceEnabled(source.id),
                            onCheckedChange = { adBlock.setSourceEnabled(source.id, it) },
                            title = source.title,
                        )
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.settings_data))
            }
            item {
                PreferenceCard {
                    AllegroRow(
                        title = stringResource(R.string.settings_clear),
                        summary = stringResource(R.string.settings_clear_summary),
                        onClick = onClearData,
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.settings_kernel))
            }
            item {
                PreferenceCard {
                    BasicComponent(
                        title = stringResource(R.string.settings_kernel_version, kernel.first),
                        summary = stringResource(R.string.settings_kernel_provider, kernel.second),
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.settings_update_section))
            }
            item {
                PreferenceCard {
                    BasicComponent(
                        title = stringResource(R.string.settings_update_repo),
                        summary = stringResource(R.string.settings_update_repo_hint),
                        bottomAction = {
                            top.yukonga.miuix.kmp.basic.TextField(
                                value = repoInput.value,
                                onValueChange = {
                                    repoInput.value = it
                                    settings.setUpdateRepo(it)
                                    com.ravenxud.allegro.di.ServiceLocator.rebindUpdateRepo(it)
                                },
                                singleLine = true,
                                label = "owner/repo",
                                useLabelAsPlaceholder = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }
            }
            item {
                PreferenceCard {
                    AllegroRow(
                        title = stringResource(R.string.settings_update_check),
                        summary = when (val s2 = updateState) {
                            is UpdateState.Checking -> stringResource(R.string.update_checking)
                            is UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                            is UpdateState.Available -> stringResource(R.string.update_available, s2.info.versionName)
                            is UpdateState.Downloading -> stringResource(R.string.update_downloading, s2.percent)
                            is UpdateState.Ready -> stringResource(R.string.update_ready)
                            is UpdateState.Failed -> stringResource(R.string.update_failed, s2.message)
                            else -> null
                        },
                        onClick = {
                            scope.launch {
                                when (val s3 = update.check()) {
                                    is UpdateState.Available -> {
                                        val file = update.download(s3.info)
                                        if (file != null) update.install(file)
                                    }
                                    else -> Unit
                                }
                            }
                        },
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.settings_diag_section))
            }
            item {
                PreferenceCard {
                    AllegroRow(
                        title = stringResource(R.string.shader_smoke_title),
                        summary = stringResource(R.string.shader_smoke_entry),
                        onClick = onOpenShaderSmoke,
                    )
                    ComponentDivider()
                    AllegroRow(
                        title = "分享日志文件",
                        summary = "发送到其他应用（系统分享面板）",
                        onClick = { shareLog(context, resources, fileExists = true) },
                    )
                    ComponentDivider()
                    AllegroRow(
                        title = "复制日志到剪贴板",
                        summary = "分享面板没有可用目标时用这条",
                        onClick = {
                            if (com.ravenxud.allegro.util.LogExporter.copyToClipboard(context)) {
                                Toast.makeText(context, "已复制日志", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, R.string.settings_diag_empty, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    ComponentDivider()
                    AllegroRow(
                        title = "保存到「下载」目录",
                        summary = "生成 allegro-diag-*.log 文件",
                        onClick = {
                            val name = com.ravenxud.allegro.util.LogExporter.saveToDownloads(context)
                            Toast.makeText(
                                context,
                                if (name != null) "已保存到下载：$name" else resources.getString(R.string.settings_diag_empty),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                    ComponentDivider()
                    AllegroRow(
                        title = "转场实验（miuix-nav）",
                        summary = "验证 NavDisplay 的转场与返回",
                        onClick = onOpenNavPilot,
                    )
                    ComponentDivider()
                    AllegroRow(
                        title = "清空日志",
                        onClick = {
                            com.ravenxud.allegro.util.LogExporter.clear()
                            Toast.makeText(context, "已清空诊断日志", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            item {
                SectionTitle(stringResource(R.string.settings_about))
            }
            item {
                PreferenceCard {
                    AllegroRow(
                        title = "开源许可",
                        summary = "第三方组件与致谢",
                        onClick = onOpenLicenses,
                    )
                    ComponentDivider()
                    BasicComponent(
                        title = stringResource(R.string.settings_version, version),
                    )
                }
            }
            item {
                Column(Modifier.padding(bottom = 24.dp)) {}
            }
        }
        }

        com.ravenxud.allegro.ui.design.AllegroTopBar(
            title = stringResource(R.string.screen_settings),
            onBack = onBack,
            backdrop = topBarBackdrop,
        )

        com.ravenxud.allegro.ui.components.ZoomDialog(
            show = showZoomDialog,
            current = textZoom,
            onDismiss = { showZoomDialog = false },
            onConfirm = { onTextZoomChange(it) },
        )
    }
}

@Composable
private fun PreferenceCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    AllegroCard { content() }
}

@Composable
private fun ComponentDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun SectionTitle(text: String) {
    SectionLabel(text)
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.SYSTEM -> R.string.settings_theme_system
}

/**
 * 通过系统分享面板发送 diag.log。
 * 若没有可用接收方（精简 ROM 常见），退化为"复制到剪贴板"，保证日志一定拿得出来。
 */
private fun shareLog(
    context: android.content.Context,
    resources: android.content.res.Resources,
    fileExists: Boolean,
) {
    val file = com.ravenxud.allegro.util.DiagLog.currentFile()
    if (file == null || !fileExists) {
        com.ravenxud.allegro.util.LogExporter.copyToClipboard(context)
        Toast.makeText(context, resources.getString(R.string.settings_diag_empty), Toast.LENGTH_SHORT).show()
        return
    }
    val ok = runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Allegro 诊断日志")
            clipData = android.content.ClipData.newRawUri("diag.log", uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, resources.getString(R.string.settings_diag_log)),
        )
        true
    }.getOrDefault(false)

    if (!ok) {
        com.ravenxud.allegro.util.LogExporter.copyToClipboard(context)
        Toast.makeText(context, "无法调起分享，已复制日志到剪贴板", Toast.LENGTH_LONG).show()
    }
}
