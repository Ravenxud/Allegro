package com.ravenxud.allegro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.ui.components.GroupDivider
import com.ravenxud.allegro.ui.components.GroupedCard
import com.ravenxud.allegro.ui.components.SectionLabel
import com.ravenxud.allegro.ui.components.SettingRow
import com.ravenxud.allegro.ui.legal.EntryStatus
import com.ravenxud.allegro.ui.legal.ThirdPartyRegistry
import com.ravenxud.allegro.ui.theme.Dimens
import com.ravenxud.allegro.ui.theme.TypeScale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 开源许可与致谢。
 *
 * 数据来自 [ThirdPartyRegistry]：以后接入任何第三方，只在该表里登记一条即可，
 * 本页自动显示，不需要改这里。
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    ScreenScaffold(title = "开源许可", onBack = onBack) { inner ->
        Column(
            modifier = inner
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.PagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.GroupGap),
        ) {
            Text(
                text = ThirdPartyRegistry.SELF_SUMMARY,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                fontSize = TypeScale.Secondary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            // 复用源码的组件单独一组，观感上更醒目
            val vendored = ThirdPartyRegistry.vendoredEntries
            if (vendored.isNotEmpty()) {
                SectionLabel("复用源码")
                GroupedCard {
                    vendored.forEachIndexed { index, entry ->
                        if (index > 0) GroupDivider()
                        SettingRow(
                            title = entry.name,
                            summary = "${entry.license} · ${entry.author}\n${entry.usage}",
                        ) { openUrl(context, entry.url) }
                    }
                }
            }

            SectionLabel("参考致谢")
            GroupedCard {
                ThirdPartyRegistry.referenceEntries.forEachIndexed { index, entry ->
                    if (index > 0) GroupDivider()
                    SettingRow(
                        title = entry.name,
                        summary = "${entry.license} · ${entry.usage}",
                    ) { openUrl(context, entry.url) }
                }
            }

            SectionLabel("依赖库")
            GroupedCard {
                ThirdPartyRegistry.entries
                    .filter { it.status == EntryStatus.DEPENDENCY }
                    .forEachIndexed { index, entry ->
                        if (index > 0) GroupDivider()
                        SettingRow(
                            title = entry.name,
                            summary = "${entry.license} · ${entry.usage}",
                        ) { openUrl(context, entry.url) }
                    }
            }

            Text(
                text = "点击任一条目可在浏览器中打开发布方主页。",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 4.dp, bottom = 24.dp),
                fontSize = TypeScale.Caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
