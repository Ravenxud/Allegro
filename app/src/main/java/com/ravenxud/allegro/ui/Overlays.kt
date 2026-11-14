package com.ravenxud.allegro.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.action.ActionRegistry
import com.ravenxud.allegro.action.toActionContext
import com.ravenxud.allegro.action.toGroup
import com.ravenxud.allegro.browser.BrowserController
import com.ravenxud.allegro.browser.BrowserState
import com.ravenxud.allegro.browser.MenuRequest
import com.ravenxud.allegro.browser.TabState
import com.ravenxud.allegro.data.model.ThemeMode
import com.ravenxud.allegro.ui.components.MiuixSheet
import com.ravenxud.allegro.ui.components.SheetRow
import com.ravenxud.allegro.ui.components.SheetSection
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme

/** 长按链接/图片的菜单（与主菜单同一套 Sheet）。 */
@Composable
fun ContextMenu(
    visibleState: MutableTransitionState<Boolean>,
    request: MenuRequest,
    controller: BrowserController,
    onDismiss: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = null,
) {
    MiuixSheet(
        state = visibleState,
        onDismiss = onDismiss,
        title = request.url,
        backdrop = backdrop,
    ) {
        val context = request.toActionContext(controller)
        ActionRegistry.forGroup(request.toGroup(), context).forEach { action ->
            SheetRow(
                icon = action.icon,
                title = stringResource(action.titleRes),
                onClick = {
                    action.execute(context)
                    onDismiss()
                },
            )
        }
    }
}

/** 主菜单：分组 Sheet。 */
@Composable
fun AppMenu(
    visibleState: MutableTransitionState<Boolean>,
    themeMode: ThemeMode,
    pageDark: Boolean,
    onHome: () -> Unit,
    onBookmark: () -> Unit,
    onNavigate: (SecondaryScreen) -> Unit,
    onCycleTheme: () -> Unit,
    onTogglePageDark: () -> Unit,
    onReader: () -> Unit,
    onReadAloud: () -> Unit,
    onSaveOffline: () -> Unit,
    onSharePage: () -> Unit,
    onOfflinePages: () -> Unit,
    onUserScripts: () -> Unit,
    onPrivacyReport: () -> Unit,
    onPickElement: () -> Unit,
    onSiteSettings: () -> Unit,
    onTabGroups: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop? = null,
    desktopSite: Boolean,
    onToggleDesktopSite: () -> Unit,
    adBlockPaused: Boolean,
    onToggleAdBlockSite: () -> Unit,
    onDismiss: () -> Unit,
) {
    MiuixSheet(state = visibleState, onDismiss = onDismiss, backdrop = backdrop) {
        SheetSection(stringResource(R.string.sheet_section_page))
        SheetRow(MiuixIcons.Search, stringResource(R.string.menu_reader), onClick = onReader)
        SheetRow(MiuixIcons.Play, stringResource(R.string.menu_read_aloud), onClick = onReadAloud)
        SheetRow(MiuixIcons.Download, stringResource(R.string.menu_save_offline), onClick = onSaveOffline)
        SheetRow(MiuixIcons.Share, stringResource(R.string.menu_share_page), onClick = onSharePage)
        SheetRow(MiuixIcons.Folder, stringResource(R.string.menu_offline_pages), onClick = onOfflinePages)
        SheetRow(MiuixIcons.Edit, stringResource(R.string.userscript_title), onClick = onUserScripts)
        SheetRow(MiuixIcons.Settings, stringResource(R.string.menu_site_settings), onClick = onSiteSettings)
        SheetRow(MiuixIcons.Hide, stringResource(R.string.menu_pick_element), onClick = onPickElement)
        SheetRow(MiuixIcons.Blocklist, stringResource(R.string.menu_privacy_report), onClick = onPrivacyReport)

        SheetSection(stringResource(R.string.sheet_section_common))
        SheetRow(MiuixIcons.Home, stringResource(R.string.menu_home), onClick = onHome)
        SheetRow(MiuixIcons.Favorites, stringResource(R.string.app_menu_bookmark), onClick = onBookmark)
        SheetRow(MiuixIcons.Folder, stringResource(R.string.screen_bookmarks)) {
            onNavigate(SecondaryScreen.BOOKMARKS)
        }
        SheetRow(MiuixIcons.GridView, stringResource(R.string.menu_tab_groups), onClick = onTabGroups)
        SheetRow(MiuixIcons.Refresh, stringResource(R.string.screen_history)) {
            onNavigate(SecondaryScreen.HISTORY)
        }
        SheetRow(MiuixIcons.Download, stringResource(R.string.screen_downloads)) {
            onNavigate(SecondaryScreen.DOWNLOADS)
        }

        SheetSection(stringResource(R.string.sheet_section_appearance))
        SheetRow(
            icon = MiuixIcons.Theme,
            title = stringResource(R.string.menu_desktop_site),
            value = stringResource(if (desktopSite) R.string.sheet_on else R.string.sheet_off),
            onClick = onToggleDesktopSite,
        )
        SheetRow(
            icon = MiuixIcons.Blocklist,
            title = stringResource(R.string.menu_adblock_site),
            value = stringResource(if (adBlockPaused) R.string.sheet_on_short else R.string.sheet_off_short),
            onClick = onToggleAdBlockSite,
        )
        SheetRow(
            icon = MiuixIcons.Theme,
            title = stringResource(R.string.settings_theme),
            value = stringResource(themeLabelRes(themeMode)),
            onClick = onCycleTheme,
        )
        SheetRow(
            icon = MiuixIcons.Theme,
            title = stringResource(R.string.menu_page_dark),
            value = stringResource(
                if (pageDark) R.string.sheet_on else R.string.sheet_off
            ),
            onClick = onTogglePageDark,
        )

        SheetSection(stringResource(R.string.sheet_section_more))
        SheetRow(MiuixIcons.Settings, stringResource(R.string.screen_settings)) {
            onNavigate(SecondaryScreen.SETTINGS)
        }
        Spacer(Modifier.size(8.dp))
    }
}

private fun themeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.SYSTEM -> R.string.settings_theme_system
}
