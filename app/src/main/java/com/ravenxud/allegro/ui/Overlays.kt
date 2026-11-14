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
import com.ravenxud.allegro.ui.components.SheetCircleButton
import com.ravenxud.allegro.ui.components.SheetPagedGrid
import com.ravenxud.allegro.ui.components.SheetItem
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
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Background
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
    snapshot: android.graphics.Bitmap? = null,
) {
    MiuixSheet(
        state = visibleState,
        onDismiss = onDismiss,
        title = request.url,
        backdrop = backdrop,
        snapshot = snapshot,
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
    snapshot: android.graphics.Bitmap? = null,
    desktopSite: Boolean,
    onToggleDesktopSite: () -> Unit,
    adBlockPaused: Boolean,
    onToggleAdBlockSite: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Luna 形态：大圆角玻璃卡片 + 4 列圆形图标网格；关闭在左，动作胶囊在右
    MiuixSheet(
        state = visibleState,
        onDismiss = onDismiss,
        backdrop = backdrop,
        snapshot = snapshot,
        showClose = true,
        scrollable = false,
        actions = {
            SheetCircleButton(MiuixIcons.Settings) { onNavigate(SecondaryScreen.SETTINGS) }
        },
    ) {
        SheetPagedGrid(
            listOf(
                SheetItem(MiuixIcons.Search, stringResource(R.string.menu_reader), onClick = onReader),
                SheetItem(MiuixIcons.Play, stringResource(R.string.menu_read_aloud), onClick = onReadAloud),
                SheetItem(MiuixIcons.Download, stringResource(R.string.menu_save_offline), onClick = onSaveOffline),
                SheetItem(MiuixIcons.Share, stringResource(R.string.menu_share_page), onClick = onSharePage),
                SheetItem(MiuixIcons.Folder, stringResource(R.string.menu_offline_pages), onClick = onOfflinePages),
                SheetItem(MiuixIcons.Edit, stringResource(R.string.userscript_title), onClick = onUserScripts),
                SheetItem(MiuixIcons.Settings, stringResource(R.string.menu_site_settings), onClick = onSiteSettings),
                SheetItem(MiuixIcons.Hide, stringResource(R.string.menu_pick_element), onClick = onPickElement),
                SheetItem(MiuixIcons.Blocklist, stringResource(R.string.menu_privacy_report), onClick = onPrivacyReport),
                SheetItem(MiuixIcons.Home, stringResource(R.string.menu_home), onClick = onHome),
                SheetItem(MiuixIcons.Favorites, stringResource(R.string.app_menu_bookmark), onClick = onBookmark),
                SheetItem(MiuixIcons.ListView, stringResource(R.string.screen_bookmarks)) {
                    onNavigate(SecondaryScreen.BOOKMARKS)
                },
                SheetItem(MiuixIcons.GridView, stringResource(R.string.menu_tab_groups), onClick = onTabGroups),
                SheetItem(MiuixIcons.Recent, stringResource(R.string.screen_history)) {
                    onNavigate(SecondaryScreen.HISTORY)
                },
                SheetItem(MiuixIcons.Download, stringResource(R.string.screen_downloads)) {
                    onNavigate(SecondaryScreen.DOWNLOADS)
                },
                SheetItem(
                    MiuixIcons.Theme,
                    stringResource(R.string.menu_desktop_site),
                    active = desktopSite,
                    onClick = onToggleDesktopSite,
                ),
                SheetItem(
                    MiuixIcons.Blocklist,
                    stringResource(R.string.menu_adblock_site),
                    active = !adBlockPaused,
                    onClick = onToggleAdBlockSite,
                ),
                SheetItem(
                    MiuixIcons.Theme,
                    stringResource(R.string.settings_theme),
                    active = true,
                    onClick = onCycleTheme,
                ),
                SheetItem(
                    MiuixIcons.Background,
                    stringResource(R.string.menu_page_dark),
                    active = pageDark,
                    onClick = onTogglePageDark,
                ),
            ),
        )
        Spacer(Modifier.size(8.dp))
    }
}

private fun themeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.SYSTEM -> R.string.settings_theme_system
}
