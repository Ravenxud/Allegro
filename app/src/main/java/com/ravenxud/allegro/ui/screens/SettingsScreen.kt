package com.ravenxud.allegro.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.screen_settings),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_theme))
            PreferenceCard {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) ComponentDivider()
                    RadioButtonPreference(
                        title = stringResource(mode.labelRes()),
                        selected = theme == mode,
                        onClick = { settings.setThemeMode(mode) },
                    )
                }
            }


            SectionTitle(stringResource(R.string.home_section_bookmarks))
            PreferenceCard {
                SwitchPreference(
                    checked = showHomeBookmarks,
                    onCheckedChange = { settings.setHomeBookmarksEnabled(it) },
                    title = stringResource(R.string.settings_home_bookmarks),
                )
            }

            SectionTitle(stringResource(R.string.sync_section))
            PreferenceCard {
                ArrowPreference(
                    title = stringResource(R.string.sync_title),
                    summary = stringResource(R.string.sync_entry_summary),
                    onClick = onOpenSync,
                )
            }

            SectionTitle(stringResource(R.string.adblock_section))
            PreferenceCard {
                SwitchPreference(
                    checked = blockEnabled,
                    onCheckedChange = { adBlock.setEnabled(it) },
                    title = stringResource(R.string.adblock_title),
                    summary = stringResource(R.string.adblock_summary, blockedCount),
                )
                ComponentDivider()
                ArrowPreference(
                    title = stringResource(R.string.adblock_update),
                    summary = when {
                        updating -> stringResource(R.string.adblock_updating)
                        updateMessage.value != null -> updateMessage.value
                        else -> stringResource(R.string.adblock_never_updated)
                    },
                    onClick = {
                        if (updating) return@ArrowPreference
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
                ArrowPreference(
                    title = stringResource(R.string.adblock_reset_stats),
                    onClick = { adBlock.resetStats() },
                )
            }

            SectionTitle(stringResource(R.string.adblock_sources))
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

            SectionTitle(stringResource(R.string.settings_data))
            PreferenceCard {
                ArrowPreference(
                    title = stringResource(R.string.settings_clear),
                    summary = stringResource(R.string.settings_clear_summary),
                    onClick = onClearData,
                )
            }


            SectionTitle(stringResource(R.string.settings_kernel))
            PreferenceCard {
                BasicComponent(
                    title = stringResource(R.string.settings_kernel_version, kernel.first),
                    summary = stringResource(R.string.settings_kernel_provider, kernel.second),
                )
            }


            SectionTitle(stringResource(R.string.settings_update_section))
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
            PreferenceCard {
                ArrowPreference(
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

            SectionTitle(stringResource(R.string.settings_about))
            PreferenceCard {
                BasicComponent(
                    title = stringResource(R.string.settings_version, version),
                )
            }
            Column(Modifier.padding(bottom = 24.dp)) {}
        }
    }
}

@Composable
private fun PreferenceCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun ComponentDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.SYSTEM -> R.string.settings_theme_system
}
