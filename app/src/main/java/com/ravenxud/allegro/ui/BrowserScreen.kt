package com.ravenxud.allegro.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ravenxud.allegro.R
import com.ravenxud.allegro.browser.BrowserController
import com.ravenxud.allegro.browser.MenuRequest
import com.ravenxud.allegro.browser.UrlParser
import com.ravenxud.allegro.adblock.AdBlockRepository
import com.ravenxud.allegro.update.UpdateRepository
import com.ravenxud.allegro.data.SettingsRepository
import com.ravenxud.allegro.data.model.SearchEngines
import com.ravenxud.allegro.data.model.ThemeMode
import com.ravenxud.allegro.data.repo.BookmarkRepository
import com.ravenxud.allegro.data.repo.DownloadRepository
import com.ravenxud.allegro.download.DownloadCenter
import com.ravenxud.allegro.data.repo.HistoryRepository
import com.ravenxud.allegro.ui.screens.BookmarksScreen
import com.ravenxud.allegro.ui.screens.DownloadsScreen
import com.ravenxud.allegro.ui.screens.HistoryScreen
import com.ravenxud.allegro.ui.screens.OfflinePagesScreen
import com.ravenxud.allegro.ui.screens.PrivacyReportScreen
import com.ravenxud.allegro.ui.screens.SiteSettingsScreen
import com.ravenxud.allegro.ui.screens.WebDavScreen
import com.ravenxud.allegro.ui.screens.ReaderScreen
import com.ravenxud.allegro.ui.screens.SettingsScreen
import com.ravenxud.allegro.ui.screens.UserScriptsScreen
import com.ravenxud.allegro.ui.theme.resolveDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主界面。要点：
 * 1. 任何 Compose 覆盖层打开时都把 WebView 宿主设为 GONE —— AndroidView 是真实 View，
 *    绘制层级高于 Compose 画布，不隐藏的话菜单会被网页盖住（表现为"点了没反应"）。
 * 2. 覆盖层用 MutableTransitionState 跟踪进出动画，避免退场时 WebView 突然出现。
 */
@Composable
fun BrowserScreen(
    controller: BrowserController,
    settings: SettingsRepository,
    adBlock: AdBlockRepository,
    update: UpdateRepository,
    bookmarks: BookmarkRepository,
    history: HistoryRepository,
    downloads: DownloadRepository,
    downloadCenter: DownloadCenter,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    backProgress: Float = 0f,
) {
    val state by controller.state.collectAsState()
    val menu by controller.menuRequest.collectAsState()
    val tab = state.activeTab
    val dark = themeMode.resolveDark()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    // 配置变化时 context 里的 resources 可能是旧的，用 Compose 的 LocalResources 才符合 lint 要求
    val resources = androidx.compose.ui.platform.LocalResources.current
    val lowRam = remember {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }
    val scope = rememberCoroutineScope()
    val layout = rememberLayoutWidth()
    val railWidth = if (layout.isLarge) layout.panelWidthDp else 0

    var field by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var tabsMounted by remember { mutableStateOf(false) }
    var tabsStub by remember { mutableStateOf<Bitmap?>(null) }
    var showAppMenu by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(SecondaryScreen.BROWSER) }
    var lastMenu by remember { mutableStateOf<MenuRequest?>(null) }
    var readerHtml by remember { mutableStateOf<String?>(null) }
    var readerTitle by remember { mutableStateOf("") }
    val readAloud = remember { com.ravenxud.allegro.reader.ReadAloudController(context) }
    var lastScreen by remember { mutableStateOf(SecondaryScreen.BROWSER) }
    LaunchedEffect(screen) { if (screen != SecondaryScreen.BROWSER) lastScreen = screen }
    LaunchedEffect(menu) { if (menu != null) lastMenu = menu }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/html"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val folders = bookmarks.folders()
                val all = bookmarks.all()
                val html = com.ravenxud.allegro.data.model.BookmarksHtml.export(folders, all)
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
                }.isSuccess
                Toast.makeText(
                    context,
                    if (ok) resources.getString(R.string.bookmark_export_done, uri.lastPathSegment ?: "bookmarks.html")
                    else resources.getString(R.string.bookmark_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                val parsed = text?.let { com.ravenxud.allegro.data.model.BookmarksHtml.parse(it) }
                if (parsed == null) {
                    Toast.makeText(context, R.string.bookmark_import_failed, Toast.LENGTH_SHORT).show()
                } else {
                    bookmarks.importAll(parsed.folders, parsed.bookmarks)
                    Toast.makeText(
                        context,
                        resources.getString(R.string.bookmark_import_done, parsed.bookmarks.size),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    val engineId by settings.searchEngineId.collectAsState()
    val searchEngine = remember(engineId) {
        SearchEngines.builtIn.firstOrNull { it.id == engineId } ?: SearchEngines.default
    }

    val appMenuState = remember { MutableTransitionState(false) }
    appMenuState.targetState = showAppMenu
    val contextMenuState = remember { MutableTransitionState(false) }
    contextMenuState.targetState = menu != null

    val isHome = tab == null || tab.url == "about:blank"
    val secondaryVisible = screen != SecondaryScreen.BROWSER
    val secondaryState = remember { MutableTransitionState(false) }
    secondaryState.targetState = secondaryVisible
    // 顶/底栏不再直接移除（会造成布局瞬间跳动），改为淡出 + 轻微位移
    val chromeAlpha by animateFloatAsState(
        targetValue = if (secondaryVisible) 0f else 1f,
        animationSpec = tween(200),
        label = "chromeAlpha",
    )

    val showHomeBookmarks by settings.homeBookmarksEnabled.collectAsState()

    val tabsTransition = updateTransition(targetState = showTabs, label = "tabsTransition")
    val tabsProgress by tabsTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.85f, stiffness = 380f) },
        label = "tabsProgress",
    ) { if (it) 1f else 0f }

    val overlayActive = isHome || screen != SecondaryScreen.BROWSER || tabsMounted ||
        appMenuState.currentState || appMenuState.targetState ||
        contextMenuState.currentState || contextMenuState.targetState

    BackHandler(enabled = screen != SecondaryScreen.BROWSER) { screen = SecondaryScreen.BROWSER }
    BackHandler(enabled = menu != null) { controller.dismissMenu() }
    BackHandler(enabled = showTabs) { showTabs = false }
    BackHandler(enabled = showAppMenu) { showAppMenu = false }

    LaunchedEffect(tab?.url, focused) {
        if (!focused) {
            // 新标签页是真正的空白页，不要把 about:blank 显示给用户
            val url = tab?.url.orEmpty().takeIf { it.isNotBlank() && it != BLANK_PAGE }.orEmpty()
            if (field.text != url) field = TextFieldValue(url, TextRange(url.length))
        }
    }

    fun submit() {
        controller.load(UrlParser.toUrl(field.text, searchEngine.queryUrl))
        focused = false
        focusManager.clearFocus()
    }

    Box(Modifier.fillMaxSize()) {
    // 大屏：常驻标签侧栏，取代手机上的标签面板
    if (layout.isLarge) {
        TabsRail(
            state = state,
            widthDp = railWidth,
            onSelect = { controller.selectTab(it) },
            onClose = { controller.closeTab(it) },
            onNew = { controller.openTab("about:blank") },
            onNavigate = { screen = it },
        )
    }
    Scaffold(
        modifier = Modifier
            .padding(start = railWidth.dp)
            .graphicsLayer {
            val p = backProgress.coerceIn(0f, 1f)
            scaleX = 1f - 0.06f * p
            scaleY = 1f - 0.06f * p
            alpha = 1f - 0.25f * p
        },
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .graphicsLayer {
                        alpha = chromeAlpha * (1f - tabsProgress)
                        translationY = -32.dp.toPx() * (tabsProgress + (1f - chromeAlpha))
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = field,
                        onValueChange = { field = it },
                        modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                        singleLine = true,
                        label = stringResource(R.string.address_hint),
                        useLabelAsPlaceholder = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        trailingIcon = {
                            IconButton(onClick = {
                                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager).primaryClip
                                val text = clip?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.text?.toString().orEmpty()
                                if (text.isBlank()) {
                                    Toast.makeText(context, R.string.paste_empty, Toast.LENGTH_SHORT).show()
                                } else {
                                    field = TextFieldValue(text, TextRange(text.length))
                                    submit()
                                }
                            }) {
                                Icon(MiuixIcons.Paste, stringResource(R.string.cd_paste_search))
                            }
                        },
                    )
                    // 之前加载中会变成 ✕，容易被当成"关闭标签页"；统一用刷新图标，
                    // 加载中点击=停止加载（行为不变，语义变清楚）
                    IconButton(onClick = {
                        if (tab?.isLoading == true) controller.stopLoading() else controller.reload()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = stringResource(
                                if (tab?.isLoading == true) R.string.cd_stop_loading else R.string.cd_refresh,
                            ),
                        )
                    }
                }
                if (tab?.isLoading == true) {
                    LinearProgressIndicator(
                        progress = (tab?.progress ?: 0) / 100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        bottomBar = {
            val barColor by animateColorAsState(
                targetValue = MiuixTheme.colorScheme.surface,
                animationSpec = tween(200),
                label = "bottomBar",
            )
            Surface(
                color = barColor,
                modifier = Modifier.graphicsLayer {
                    alpha = chromeAlpha * (1f - tabsProgress)
                    translationY = 32.dp.toPx() * (tabsProgress + (1f - chromeAlpha))
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(56.dp)
                        .pointerInput(Unit) {
                            var accumulated = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { accumulated = 0f },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    accumulated += amount
                                    val threshold = 72.dp.toPx()
                                    if (kotlin.math.abs(accumulated) > threshold) {
                                        controller.switchTab(if (accumulated > 0) -1 else 1)
                                        accumulated = 0f
                                    }
                                },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolbarButton(MiuixIcons.Back, stringResource(R.string.cd_back)) {
                        if (tab?.canGoBack == true) {
                            controller.goBack()
                        } else {
                            Toast.makeText(context, R.string.nav_no_back, Toast.LENGTH_SHORT).show()
                        }
                    }
                    ToolbarButton(MiuixIcons.Forward, stringResource(R.string.cd_forward)) {
                        if (tab?.canGoForward == true) {
                            controller.goForward()
                        } else {
                            Toast.makeText(context, R.string.nav_no_forward, Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (!layout.isLarge) TabCountButton(count = state.tabs.size) {
                        controller.refreshPreview()
                        if (lowRam) {
                            // 低端机不做截图，直接用占位块 + 短动画
                            tabsStub = null
                            tabsMounted = true
                            showTabs = true
                        } else {
                            // 先截图再开面板：PixelCopy 异步返回后再上动画
                            controller.captureStub { bitmap ->
                                tabsStub = bitmap
                                tabsMounted = true
                                showTabs = true
                            }
                        }
                    }
                    ToolbarButton(MiuixIcons.More, stringResource(R.string.app_menu)) {
                        showAppMenu = true
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            val swipe = remember { mutableStateOf<PullRefreshLayout?>(null) }
            AndroidView(
                factory = { ctx ->
                    val container = FrameLayout(ctx)
                    PullRefreshLayout(ctx).apply {
                        addView(
                            container,
                            android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        onRefresh = { controller.reload() }
                        atTopProvider = { controller.isActiveTabAtTop() }
                        swipe.value = this
                        controller.attachHost(container)
                    }
                },
                update = { view -> view.visibility = if (overlayActive) View.GONE else View.VISIBLE },
                modifier = Modifier.fillMaxSize(),
            )

            LaunchedEffect(tab?.isLoading) {
                if (tab?.isLoading == false) swipe.value?.isRefreshing = false
            }

            AnimatedVisibility(
                visible = isHome,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 24 },
                exit = fadeOut(tween(140)),
            ) {
                HomeScreen(
                    controller = controller,
                    searchEngine = searchEngine,
                    bookmarks = bookmarks,
                    showBookmarks = showHomeBookmarks,
                )
            }

            AnimatedVisibility(
                visibleState = contextMenuState,
                enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 4 },
            ) {
                lastMenu?.let { request ->
                    ContextMenu(
                        visibleState = contextMenuState,
                        request = request,
                        controller = controller,
                        onDismiss = { controller.dismissMenu() },
                    )
                }
            }

            AnimatedVisibility(
                visibleState = appMenuState,
                enter = fadeIn(tween(150)) + slideInVertically(tween(220)) { it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 4 },
            ) {
                AppMenu(
                    visibleState = appMenuState,
                    themeMode = themeMode,
                    pageDark = tab?.pageDarkEnabled == true,
                    onDismiss = { showAppMenu = false },
                    onHome = {
                        controller.openTab("about:blank")
                        showAppMenu = false
                    },
                    onNavigate = { target ->
                        showAppMenu = false
                        screen = target
                    },
                    onBookmark = {
                        val current = controller.state.value.activeTab
                        if (current != null) {
                            scope.launch { bookmarks.add(current.title, current.url) }
                            Toast.makeText(context, R.string.app_menu_bookmark_added, Toast.LENGTH_SHORT).show()
                        }
                        showAppMenu = false
                    },
                    onCycleTheme = onCycleTheme,
                    onTogglePageDark = { controller.togglePageDark() },
                    onReader = {
                        showAppMenu = false
                        controller.openReader { html ->
                            if (html == null) {
                                Toast.makeText(context, R.string.reader_not_article, Toast.LENGTH_SHORT).show()
                            } else {
                                readerTitle = controller.state.value.activeTab?.title.orEmpty()
                                readerHtml = html
                            }
                        }
                    },
                    onReadAloud = {
                        showAppMenu = false
                        controller.readPageText { text ->
                            if (text.isBlank()) {
                                Toast.makeText(context, R.string.reader_not_article, Toast.LENGTH_SHORT).show()
                            } else {
                                readAloud.init()
                                readAloud.speak(text)
                            }
                        }
                    },
                    onSaveOffline = {
                        showAppMenu = false
                        controller.saveOfflinePage()
                    },
                    onSharePage = {
                        showAppMenu = false
                        controller.sharePage()
                    },
                    onOfflinePages = {
                        showAppMenu = false
                        screen = SecondaryScreen.OFFLINE
                    },
                    onUserScripts = {
                        showAppMenu = false
                        screen = SecondaryScreen.USERSCRIPTS
                    },
                    onPrivacyReport = {
                        showAppMenu = false
                        screen = SecondaryScreen.PRIVACY
                    },
                    onPickElement = {
                        showAppMenu = false
                        controller.startElementPicker()
                    },
                    onSiteSettings = {
                        showAppMenu = false
                        screen = SecondaryScreen.SITE
                    },
                    adBlockPaused = controller.isAdBlockPausedForCurrentSite(),
                    onToggleAdBlockSite = { controller.toggleAdBlockForCurrentSite() },
                )
            }

        }
    }

    val html = readerHtml
    if (html != null) {
        ReaderScreen(
            html = html,
            title = readerTitle,
            onBack = { readerHtml = null },
            onSpeak = {
                controller.readPageText { text -> readAloud.speak(text) }
            },
            onStopSpeak = { readAloud.stop() },
        )
        return
    }

    TabSwitcherOverlay(
        visible = showTabs,
        stub = tabsStub,
        progress = tabsProgress,
        state = state,
        onSelect = { controller.selectTab(it); showTabs = false },
        onClose = { controller.closeTab(it) },
        onNew = { controller.openTab("about:blank"); showTabs = false },
        onReopen = { controller.restoreLastClosed(); showTabs = false },
        onDismiss = { showTabs = false },
        onDismissed = {
            tabsMounted = false
            tabsStub = null
        },
        onCloseOthers = { controller.closeOtherTabs(it) },
        onMove = { from, to -> controller.moveTab(from, to) },
        onCopyLink = { controller.copyToClipboard(it) },
        simplify = lowRam,
    )

            AnimatedVisibility(
                visibleState = secondaryState,
                enter = fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 7 },
                exit = fadeOut(tween(180)) + slideOutHorizontally(tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 9 },
            ) {
                val back: () -> Unit = { screen = SecondaryScreen.BROWSER }
                val openAndBack: (String) -> Unit = {
                    screen = SecondaryScreen.BROWSER
                    controller.openTab(it)
                }
                when (lastScreen) {
                    SecondaryScreen.BOOKMARKS -> BookmarksScreen(
                        repository = bookmarks,
                        onOpen = openAndBack,
                        onBack = back,
                        onExport = { exportLauncher.launch("bookmarks.html") },
                        onImport = { importLauncher.launch(arrayOf("text/html", "text/plain", "*/*")) },
                    )
                    SecondaryScreen.HISTORY -> HistoryScreen(history, openAndBack, back)
                    SecondaryScreen.DOWNLOADS -> DownloadsScreen(downloads, downloadCenter, back)
                    SecondaryScreen.SYNC -> WebDavScreen(
                        sync = com.ravenxud.allegro.di.ServiceLocator.syncRepository,
                        onBack = back,
                    )
                    SecondaryScreen.SITE -> SiteSettingsScreen(
                        host = controller.currentHost(),
                        controller = controller,
                        siteSettings = com.ravenxud.allegro.di.ServiceLocator.siteSettingsRepository,
                        adBlock = adBlock,
                        onBack = back,
                    )
                    SecondaryScreen.PRIVACY -> PrivacyReportScreen(
                        adBlock = adBlock,
                        onBack = back,
                    )
                    SecondaryScreen.USERSCRIPTS -> UserScriptsScreen(
                        store = com.ravenxud.allegro.di.ServiceLocator.userScriptStore,
                        onBack = back,
                    )
                    SecondaryScreen.OFFLINE -> OfflinePagesScreen(
                        load = { controller.offlineEntries() },
                        onOpen = { name ->
                            controller.openOfflinePage(name)
                            back()
                        },
                        onDelete = { controller.deleteOfflineEntry(it) },
                        onBack = back,
                    )
                    SecondaryScreen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        adBlock = adBlock,
                        update = update,
                        onBack = back,
                        onOpenSync = { screen = SecondaryScreen.SYNC },
                        onClearData = {
                            scope.launch {
                                history.clear()
                                bookmarks.clear()
                                downloads.clear()
                                Toast.makeText(context, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    else -> Unit
                }
            }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) MiuixTheme.colorScheme.onSurface
            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 标签入口：圆角方框 + 数字，比一个列表图标更直观。 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.TabCountButton(
    count: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = 1.5.dp,
                        color = MiuixTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private const val BLANK_PAGE = "about:blank"
