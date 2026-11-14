package com.ravenxud.allegro.ui.screens

import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.adblock.AdBlockRepository
import com.ravenxud.allegro.browser.BrowserController
import com.ravenxud.allegro.data.site.SiteRulesBundle
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 站点规则总览：把散落在各页的「按站点设置」汇总成一张表。
 *
 * - 一行一个站点，摘要显示改过什么（JS 关 / 图片关 / 桌面版 / 过滤暂停）
 * - 点行 → 进入该站点的详细设置
 * - 底部：导出 / 导入规则（JSON，可跨设备搬，也会随 WebDAV 同步）
 */
@Composable
fun SiteRulesScreen(
    controller: BrowserController,
    siteSettings: SiteSettingsRepository,
    adBlock: AdBlockRepository,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current

    val configured = remember(version) { controller.configuredSiteSettings() }
    val whitelist = remember(version) { adBlock.allowedSites().sorted() }

    fun summary(host: String): String {
        val site = configured.firstOrNull { it.host == host }
        val parts = ArrayList<String>()
        site?.deviations()?.let { parts += it }
        if (whitelist.contains(host)) parts += resources.getString(R.string.rules_adblock_off)
        return if (parts.isEmpty()) resources.getString(R.string.rules_default) else parts.joinToString(" · ")
    }

    ScreenScaffold(title = stringResource(R.string.rules_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        ) {
            Text(
                stringResource(R.string.rules_intro),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.rules_export),
                    summary = stringResource(R.string.rules_export_summary),
                    onClick = onExport,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                ArrowPreference(
                    title = stringResource(R.string.rules_import),
                    summary = stringResource(R.string.rules_import_summary),
                    onClick = onImport,
                )
            }

            Spacer(Modifier.height(Spacing.m))
            Text(
                stringResource(R.string.rules_sites),
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            if (configured.isEmpty()) {
                EmptyState(
                    icon = MiuixIcons.Settings,
                    title = stringResource(R.string.rules_empty),
                    subtitle = stringResource(R.string.rules_empty_hint),
                )
            } else {
                configured.forEach { site ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.s)
                            .clickable { onEdit(site.host) },
                    ) {
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
                                    summary(site.host),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                controller.resetSiteSettings(site.host)
                                adBlock.setSiteAllowed(site.host, false)
                                version++
                            }) {
                                Icon(MiuixIcons.Close, stringResource(R.string.site_reset))
                            }
                        }
                    }
                }
            }

            if (whitelist.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(R.string.rules_whitelist),
                    modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                whitelist.forEach { host ->
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
                            Text(host, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
                            IconButton(onClick = {
                                adBlock.setSiteAllowed(host, false)
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

/** 导入/导出共用的文件读写小工具（JSON 文本）。 */
object SiteRulesIo {

    fun exportText(siteSettings: SiteSettingsRepository, adBlock: AdBlockRepository): String =
        SiteRulesBundle(
            exportedAt = System.currentTimeMillis(),
            sites = siteSettings.configured(),
            adBlockWhitelist = adBlock.allowedSites().sorted(),
        ).encode()

    /** @return 导入的站点规则条数；解析失败返回 null。 */
    fun importText(
        text: String,
        siteSettings: SiteSettingsRepository,
        adBlock: AdBlockRepository,
    ): Int? {
        val bundle = SiteRulesBundle.decode(text) ?: return null
        bundle.sites.forEach { siteSettings.save(it) }
        bundle.adBlockWhitelist.forEach { adBlock.setSiteAllowed(it, true) }
        return bundle.sites.size
    }
}

/** 供界面调用的 Toast 文案助手（避免在 composable 里堆逻辑）。 */
fun siteRulesToast(context: android.content.Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
