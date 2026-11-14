package com.ravenxud.allegro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.adblock.AdBlockRepository
import com.ravenxud.allegro.browser.BrowserController
import com.ravenxud.allegro.data.site.SiteSettings
import com.ravenxud.allegro.data.site.SiteSettingsRepository
import com.ravenxud.allegro.ui.components.EmptyState
import com.ravenxud.allegro.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 站点设置：针对**当前域名**的 JS / 图片 / 桌面版 UA / 广告过滤开关，
 * 外加「清除本站数据」和「恢复默认」，底部列出所有被改过的站点。
 *
 * 改动立即写盘；需要重载的项（JS、UA、图片）由 [onReload] 触发重载。
 */
@Composable
fun SiteSettingsScreen(
    host: String?,
    controller: BrowserController,
    siteSettings: SiteSettingsRepository,
    adBlock: AdBlockRepository,
    onBack: () -> Unit,
) {
    // 仓库是 StateFlow，但当前站点设置还叠加了父域继承与广告白名单，这里用版本号强制重算
    var version by remember { mutableIntStateOf(0) }
    val current = remember(version, host) { host?.let { controller.siteSettingsOf(it) } }
    val configured = remember(version) { controller.configuredSiteSettings() }
    val adBlockPaused = remember(version, host) { host != null && adBlock.isSiteAllowed(host) }

    fun update(block: (SiteSettings) -> SiteSettings) {
        val base = current ?: return
        controller.saveSiteSettings(block(base))
        version++
        // 从「站点规则」列表进来编辑别的站点时，不要重载当前页面
        if (host == controller.currentHost()) controller.reload()
    }

    ScreenScaffold(
        title = stringResource(R.string.site_title),
        onBack = onBack,
        actions = {
            if (host != null) {
                IconButton(onClick = {
                    controller.resetSiteSettings(host)
                    adBlock.setSiteAllowed(host, false)
                    version++
                    controller.reload()
                }) {
                    Icon(MiuixIcons.Close, stringResource(R.string.site_reset))
                }
            }
        },
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        ) {
            if (host == null || current == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.l)) {
                        Text(
                            stringResource(R.string.site_no_host),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.l)) {
                        Text(host, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.site_hint),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.m))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        checked = current.javaScript,
                        onCheckedChange = { value -> update { it.copy(javaScript = value) } },
                        title = stringResource(R.string.site_js),
                        summary = stringResource(R.string.site_js_summary),
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SwitchPreference(
                        checked = current.images,
                        onCheckedChange = { value -> update { it.copy(images = value) } },
                        title = stringResource(R.string.site_images),
                        summary = stringResource(R.string.site_images_summary),
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SwitchPreference(
                        checked = current.desktopUa,
                        onCheckedChange = { value -> update { it.copy(desktopUa = value) } },
                        title = stringResource(R.string.site_desktop),
                        summary = stringResource(R.string.site_desktop_summary),
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SwitchPreference(
                        checked = !adBlockPaused,
                        onCheckedChange = { value ->
                            adBlock.setSiteAllowed(host, !value)
                            version++
                            if (host == controller.currentHost()) controller.reload()
                        },
                        title = stringResource(R.string.site_adblock),
                        summary = stringResource(R.string.site_adblock_summary),
                    )
                }

                Spacer(Modifier.height(Spacing.m))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.site_clear_data),
                        summary = stringResource(R.string.site_clear_data_summary),
                        onClick = { controller.clearCurrentSiteData() },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.l))
            Text(
                stringResource(R.string.site_configured_title),
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            if (configured.isEmpty()) {
                EmptyState(
                    icon = MiuixIcons.Settings,
                    title = stringResource(R.string.site_configured_empty),
                    subtitle = stringResource(R.string.site_configured_empty_hint),
                )
            } else {
                configured.forEach { site ->
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
                                Text(site.host, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    site.deviations().joinToString(" · "),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            IconButton(onClick = {
                                controller.resetSiteSettings(site.host)
                                if (site.host == host) controller.reload()
                                version++
                            }) {
                                Icon(MiuixIcons.Close, stringResource(R.string.site_reset))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}
