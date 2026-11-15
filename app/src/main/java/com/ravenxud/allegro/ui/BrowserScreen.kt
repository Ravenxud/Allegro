package com.ravenxud.allegro.ui
import com.ravenxud.allegro.ui.nav.BrowserNavHost
import com.ravenxud.allegro.ui.nav.SecondaryRoute
import com.ravenxud.allegro.ui.nav.currentSecondary
import com.ravenxud.allegro.ui.nav.popSecondary
import com.ravenxud.allegro.ui.nav.pushSecondary
import com.ravenxud.allegro.ui.nav.rememberBrowserStack

import com.ravenxud.allegro.ui.theme.AllegroIcons
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.State
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.ravenxud.allegro.ui.screens.SiteRulesIo
import com.ravenxud.allegro.ui.screens.ShaderSmokeScreen
import com.ravenxud.allegro.ui.screens.TabGroupsScreen
import com.ravenxud.allegro.ui.screens.SiteRulesScreen
import com.ravenxud.allegro.ui.screens.SiteSettingsScreen
import com.ravenxud.allegro.ui.screens.WebDavScreen
import com.ravenxud.allegro.ui.screens.ReaderScreen
import com.ravenxud.allegro.ui.screens.SettingsScreen
import com.ravenxud.allegro.ui.screens.UserScriptsScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.ravenxud.allegro.ui.theme.Motion
import com.ravenxud.allegro.ui.theme.resolveDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Folder
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
    // 最近关闭：打开标签面板时读取一次
    // C6：页内提示（替代系统 Toast）
    var pageToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pageToast) {
        if (pageToast != null) {
            kotlinx.coroutines.delay(2000)
            pageToast = null
        }
    }

    var recentClosed by remember {
        mutableStateOf<List<com.ravenxud.allegro.data.entity.ClosedTabEntity>>(emptyList())
    }
    var tabsStub by remember { mutableStateOf<Bitmap?>(null) }
    var tabsThumb by remember { mutableStateOf<Bitmap?>(null) }
    var showAppMenu by remember { mutableStateOf(false) }
    // 2b2：**栈是唯一事实源**。screen 由栈顶派生，不再单独持有可写状态。
    // 这条规则来自两次回归（0.68.0 闪退、0.68.2 顶栏透明占位）：
    // 只要存在两个可独立写入的状态，就一定会有一条路径漏同步。
    val navStack = rememberBrowserStack()
    val screen: SecondaryScreen = navStack.currentSecondary() ?: SecondaryScreen.BROWSER

    /** 进二级页：push（返回回上一页）。 */
    fun pushSecondary(screen: SecondaryScreen) {
        if (screen != SecondaryScreen.BROWSER) navStack.pushSecondary(screen)
    }

    /** 返回一层；栈里只剩 Root 时什么也不做（由 Activity 处理退出）。 */
    val backOnce: () -> Unit = { navStack.popSecondary() }

    /** 回到浏览器主界面：清掉所有二级页。 */
    fun backToBrowser() {
        while (navStack.popSecondary()) { }
    }
    var lastMenu by remember { mutableStateOf<MenuRequest?>(null) }
    var moveTabId by remember { mutableStateOf<String?>(null) }
    // 网页可见时，菜单的玻璃底来自一张 PixelCopy 快照（WebView 抓不到实时内容）
    // 注意：必须在面板打开前抓，否则 WebView 已 GONE，只能抓到空
    val sheetSnapshot by controller.sheetSnapshot.collectAsState()
    val sheetScrollDelta by controller.sheetScrollDelta.collectAsState()

    // 首页是纯 Compose，可以被真正捕获并模糊；网页是 WebView，抓不到 → 只在首页给菜单玻璃
    val homeSurface = MiuixTheme.colorScheme.surface
    val homeLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val homeBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(homeLayer) {
        drawRect(homeSurface)
        drawContent()
    }
    var readerHtml by remember { mutableStateOf<String?>(null) }
    var readerTitle by remember { mutableStateOf("") }
    val readAloud = remember { com.ravenxud.allegro.reader.ReadAloudController(context) }
    var lastScreen by remember { mutableStateOf(SecondaryScreen.BROWSER) }
    LaunchedEffect(screen) { if (screen != SecondaryScreen.BROWSER) lastScreen = screen }
    LaunchedEffect(menu) { if (menu != null) lastMenu = menu }

    // 关闭时清掉快照，避免下次误用旧画面
    LaunchedEffect(showAppMenu, menu) {
        if (!showAppMenu && menu == null) controller.clearSheetSnapshot()
    }

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

    val rulesImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                val count = text?.let {
                    SiteRulesIo.importText(
                        it,
                        com.ravenxud.allegro.di.ServiceLocator.siteSettingsRepository,
                        adBlock,
                    )
                }
                val message = if (count == null) {
                    resources.getString(R.string.rules_import_failed)
                } else {
                    resources.getString(R.string.rules_import_done, count)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
    // A1/A2：二级页进度（0→1）。只在 graphicsLayer / drawBehind 里读，不参与组合期计算
    val secondaryTransition = updateTransition(secondaryVisible, label = "secondary")
    val secondaryProgress: State<Float> = secondaryTransition.animateFloat(
        transitionSpec = {
            spring(dampingRatio = 0.9f, stiffness = 320f, visibilityThreshold = 0.001f)
        },
        label = "secondaryProgress",
    ) { if (it) 1f else 0f }
    // 顶/底栏不再直接移除（会造成布局瞬间跳动），改为淡出 + 轻微位移
    val chromeAlpha by animateFloatAsState(
        targetValue = if (secondaryVisible) 0f else 1f,
        animationSpec = Motion.color(),
        label = "chromeAlpha",
    )

    val showHomeBookmarks by settings.homeBookmarksEnabled.collectAsState()
    val homeWallpaper by settings.wallpaperUri.collectAsState()
    val homeWallpaperSource by settings.wallpaperSource.collectAsState()
    val blockedCount by adBlock.blocked.collectAsState()

    val tabsTransition = updateTransition(targetState = showTabs, label = "tabsTransition")
    val reduceMotion = com.ravenxud.allegro.ui.theme.Motion.reduceMotion(context)
    val tabsProgress by tabsTransition.animateFloat(
        transitionSpec = {
            if (reduceMotion) tween(0) else spring(dampingRatio = 1f, stiffness = 115f)
        },
        label = "tabsProgress",
    ) { if (it) 1f else 0f }

    // 单变量实验（0.37.2）：菜单打开时**不再隐藏 WebView**。
    // 之前的 GONE 会让"透出网页 / 模糊网页"在物理上不可能成立。
    // 标签面板 / 菜单都不再隐藏 WebView：面板有自己的背景与飞行缩略，隐藏只会白闪
    // 二级页也不隐藏 WebView：二级页自身不透明，隐藏只会让背景动画"硬切"
    val overlayActive = isHome

    // 面板可见时，网页滚动会节流重抓玻璃底图（否则模糊不跟随滚动）
    LaunchedEffect(showTabs) {
        if (showTabs) {
            recentClosed = runCatching { controller.recentClosed(10) }
                .getOrDefault(emptyList())
                .filter { it.url != "about:blank" }
                .take(5)
        }
    }

    // 启动时把已保存的缩放套到所有 WebView
    LaunchedEffect(Unit) {
        controller.applyTextZoom(settings.textZoom.value)
    }

    LaunchedEffect(showAppMenu, menu != null) {
        controller.setSheetVisible(showAppMenu || menu != null)
    }

    BackHandler(enabled = screen != SecondaryScreen.BROWSER) { backOnce() }
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
            onNavigate = { pushSecondary(it) },
            onToggleGroup = { id, collapsed ->
                scope.launch { controller.setTabGroupCollapsed(id, collapsed) }
            },
        )
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



            // C6 宿主：整页浮层，必须在根 Box 内、且不在任何 Column 里

            // 2b1：二级页渲染提取为局部函数（捕获所需状态，逻辑未改），供 NavDisplay 调用
            @Composable
            fun RenderSecondary(routeScreen: SecondaryScreen) {
                val back: () -> Unit = { backOnce() }
                val openAndBack: (String) -> Unit = {
                    while (navStack.popSecondary()) { }
                    controller.openTab(it)
                }
                when (routeScreen) {
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
                    SecondaryScreen.SHADER_SMOKE -> ShaderSmokeScreen(onBack = back)
                    SecondaryScreen.LICENSES -> com.ravenxud.allegro.ui.screens.LicensesScreen(onBack = back)
                    SecondaryScreen.NAV_PILOT -> com.ravenxud.allegro.ui.screens.NavPilotScreen(onBack = back)
                    SecondaryScreen.GROUPS -> TabGroupsScreen(
                        controller = controller,
                        onBack = back,
                    )
                    SecondaryScreen.SITE_RULES -> SiteRulesScreen(
                        controller = controller,
                        siteSettings = com.ravenxud.allegro.di.ServiceLocator.siteSettingsRepository,
                        adBlock = adBlock,
                        onEdit = { pushSecondary(SecondaryScreen.SITE) },
                        onBack = back,
                        onExport = {
                            val text = SiteRulesIo.exportText(
                                com.ravenxud.allegro.di.ServiceLocator.siteSettingsRepository,
                                adBlock,
                            )
                            val file = java.io.File(context.cacheDir, "site-rules.json")
                            runCatching { file.writeText(text) }
                            runCatching {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(intent, resources.getString(R.string.rules_export)),
                                )
                            }.onFailure {
                                Toast.makeText(context, R.string.bookmark_export_failed, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onImport = { rulesImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
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
                        onOpenSync = { pushSecondary(SecondaryScreen.SYNC) },
                        onOpenSiteRules = { pushSecondary(SecondaryScreen.SITE_RULES) },
                        onOpenShaderSmoke = { pushSecondary(SecondaryScreen.SHADER_SMOKE) },
                    onOpenLicenses = { pushSecondary(SecondaryScreen.LICENSES) },
                    onOpenNavPilot = { pushSecondary(SecondaryScreen.NAV_PILOT) },
                    onTextZoomChange = { pct ->
                        settings.setTextZoom(pct)
                        controller.applyTextZoom(pct)
                    },
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


    /** 2b3：浏览器 UI 现在是 NavDisplay 的 Root entry。 */
    @Composable
    fun BrowserRootView() {
            Scaffold(
                modifier = Modifier
                    .padding(start = railWidth.dp)
                    .graphicsLayer {
                    val p = backProgress.coerceIn(0f, 1f)
                    scaleX = 1f - 0.06f * p
                    scaleY = 1f - 0.06f * p
                    alpha = 1f - 0.25f * p
                },
                bottomBar = {
                    val barColor by animateColorAsState(
                        targetValue = MiuixTheme.colorScheme.surface,
                        animationSpec = Motion.color(),
                        label = "bottomBar",
                    )
                    var barBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                    var frost by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    // 进度条：数据 → 单点动画（Luna 也是状态驱动，范围外不做动画）
                    val progressColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.95f)
                    // 键盘真的在的时候才算「展开」：系统返回键收键盘后，焦点可能还留在输入框上
                    val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime
                        .getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
                    LaunchedEffect(imeVisible) {
                        if (!imeVisible && focused) focusManager.clearFocus()
                    }
                    val expand by animateFloatAsState(
                        targetValue = if (focused && imeVisible) 1f else 0f,
                        animationSpec = spring(stiffness = 520f, dampingRatio = 0.9f),
                        label = "barExpand",
                    )
                    val btnWidth = (40.dp * (1f - expand)).coerceAtLeast(0.dp)
                    val btnAlpha = (1f - expand).coerceAtLeast(0f)
                    val progressTarget = (tab?.progress ?: 0) / 100f
                    val progressAnim = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(progressTarget, tab?.isLoading) {
                        if (progressTarget >= 1f) {
                            progressAnim.animateTo(1f, androidx.compose.animation.core.tween(220))
                            kotlinx.coroutines.delay(380)
                            progressAnim.animateTo(0f, androidx.compose.animation.core.tween(260))
                        } else {
                            progressAnim.animateTo(
                                progressTarget,
                                androidx.compose.animation.core.tween(
                                    durationMillis = 240,
                                    easing = androidx.compose.animation.core.LinearOutSlowInEasing,
                                ),
                            )
                        }
                    }
                    // WebView 现在铺到底栏下面：抓「底栏正上方一条」预模糊，当磨砂底
                    // 只在「需要变」的时候抓：打开/切换标签、加载进度变化。
                    // 之前是 260ms 死循环做 PixelCopy（GPU 回读），静置时也在烧 —— 全局卡顿的主要来源之一。
                    LaunchedEffect(tab?.id, tab?.url, chromeAlpha, overlayActive, tab?.isLoading) {
                        val b = barBounds
                        val usable = !overlayActive && chromeAlpha > 0.05f && b != null &&
                            b.height > 1f && tab?.url != "about:blank"
                        if (usable && b != null) {
                            val strip = android.graphics.Rect(
                                b.left.toInt(),
                                (b.top - b.height).toInt().coerceAtLeast(0),
                                b.right.toInt(),
                                b.top.toInt(),
                            )
                            controller.captureBarFrost(strip) { bitmap ->
                                frost = bitmap?.asImageBitmap()
                            }
                        } else if (frost != null) {
                            frost = null
                        }
                    }
                    // 加载中再低频补两次，让磨砂跟着页面变化
                    LaunchedEffect(tab?.isLoading, tab?.id) {
                        if (tab?.isLoading != true) return@LaunchedEffect
                        repeat(2) {
                            kotlinx.coroutines.delay(900)
                            val b = barBounds ?: return@LaunchedEffect
                            if (overlayActive || chromeAlpha <= 0.05f || b.height <= 1f) return@LaunchedEffect
                            val strip = android.graphics.Rect(
                                b.left.toInt(), (b.top - b.height).toInt().coerceAtLeast(0),
                                b.right.toInt(), b.top.toInt(),
                            )
                            controller.captureBarFrost(strip) { bitmap -> frost = bitmap?.asImageBitmap() }
                        }
                    }
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.graphicsLayer {
                            alpha = chromeAlpha * (1f - tabsProgress)
                            translationY = 32.dp.toPx() * (tabsProgress + (1f - chromeAlpha))
                        },
                    ) {
                        androidx.compose.foundation.layout.Box {
                            frost?.let { bmp ->
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    // 位图在控制器里已经模糊过；再叠一层 RenderEffect 只会白烧 GPU
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { barBounds = it.boundsInWindow() }
                                    // 磨砂：半透明渐变压在网页快照上，底部几乎不透明保证可读
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            0f to barColor.copy(alpha = 0.62f),
                                            0.45f to barColor.copy(alpha = 0.84f),
                                            1f to barColor.copy(alpha = 0.96f),
                                        ),
                                    )
                                    .imePadding()
                                    .navigationBarsPadding(),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
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
                                        }
                                        .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                                            .drawWithContent {
                                                drawContent()
                                                val p = progressAnim.value
                                                if (p > 0.002f) {
                                                    // 沿胶囊内圈描边：进度 = 圆角矩形周长的一段
                                                    val stroke = 2.5.dp.toPx()
                                                    val inset = 1.dp.toPx() + stroke / 2f
                                                    val path = androidx.compose.ui.graphics.Path().apply {
                                                        addRoundRect(
                                                            androidx.compose.ui.geometry.RoundRect(
                                                                left = inset,
                                                                top = inset,
                                                                right = size.width - inset,
                                                                bottom = size.height - inset,
                                                                // 胶囊圆角是 22dp；按"高度的一半"算会变成跑道形，和栏对不上
                                                                radiusX = 22.dp.toPx() - inset,
                                                                radiusY = 22.dp.toPx() - inset,
                                                            ),
                                                        )
                                                    }
                                                    val measure = androidx.compose.ui.graphics.PathMeasure().apply {
                                                        setPath(path, false)
                                                    }
                                                    val total = measure.length
                                                    if (total > 0f) {
                                                        val start = total * 0.62f // 从左下角附近起画，顺时针
                                                        val end = start + total * p
                                                        val slice = androidx.compose.ui.graphics.Path()
                                                        if (end <= total) {
                                                            measure.getSegment(start, end, slice, true)
                                                        } else {
                                                            measure.getSegment(start, total, slice, true)
                                                            measure.getSegment(0f, end - total, slice, true)
                                                        }
                                                        drawPath(
                                                            path = slice,
                                                            color = progressColor,
                                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                                width = stroke,
                                                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                            ),
                                                        )
                                                    }
                                                }
                                            },
                                    ) {
                                        TextField(
                                            value = field,
                                            onValueChange = { field = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { focused = it.isFocused },
                                            singleLine = true,
                                            label = stringResource(R.string.address_hint),
                                            useLabelAsPlaceholder = true,
                                            cornerRadius = 22.dp,
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
                                                    Icon(AllegroIcons.Paste, stringResource(R.string.cd_paste_search))
                                                }
                                            },
                                        )
                                    }
                                    if (isHome) {
                                        // 首页没有「后退」语义，这个位置给书签
                                        IconButton(
                                            onClick = { pushSecondary(SecondaryScreen.BOOKMARKS) },
                                            modifier = Modifier.height(40.dp).width(btnWidth).graphicsLayer { alpha = btnAlpha },
                                        ) {
                                            Icon(AllegroIcons.Favorites, stringResource(R.string.home_section_bookmarks))
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                if (tab?.canGoBack == true) {
                                                    controller.goBack()
                                                } else {
                                                    pageToast = "已经是第一页"
                                                }
                                            },
                                            modifier = Modifier.height(40.dp).width(btnWidth).graphicsLayer { alpha = btnAlpha },
                                        ) {
                                            Icon(AllegroIcons.Back, stringResource(R.string.cd_back))
                                        }
                                    }
                                    if (!layout.isLarge) {
                                        TabCountButton(
                                            count = state.tabs.size,
                                            modifier = Modifier.height(40.dp).width(btnWidth).graphicsLayer { alpha = btnAlpha },
                                        ) {
                                            controller.refreshPreviews()
                                            if (lowRam) {
                                                tabsStub = null
                                                tabsMounted = true
                                                showTabs = true
                                            } else {
                                                controller.captureStub { bitmap ->
                                                    tabsStub = bitmap
                                                    tabsMounted = true
                                                    showTabs = true
                                                }
                                                controller.captureThumb { bitmap -> tabsThumb = bitmap }
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (tab == null || tab.url == "about:blank") {
                                                showAppMenu = true
                                            } else {
                                                controller.captureForSheet { showAppMenu = true }
                                            }
                                        },
                                        modifier = Modifier.height(40.dp).width(btnWidth).graphicsLayer { alpha = btnAlpha },
                                    ) {
                                        Icon(AllegroIcons.More, stringResource(R.string.app_menu))
                                    }
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Box(Modifier.fillMaxSize()) {
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
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .graphicsLayer {
                                // A1/A2：二级页打开时背景轻微后缩（1 → 0.94），返回时回弹
                                val p = secondaryProgress.value
                                val s2 = 1f - 0.06f * p
                                scaleX = s2
                                scaleY = s2
                            },
                    )
        
                    // 编辑中：点页面空白处先收键盘（WebView 是真实 View，收不到 Compose 的点击）
                    if (focused) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { focusManager.clearFocus() },
                        )
                    }

                    // A1/A2：随二级页进度压暗背景（8%）；alpha 在 drawBehind 内读，避免每帧重组
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val p = secondaryProgress.value
                                if (p > 0.001f) drawRect(Color.Black.copy(alpha = 0.08f * p))
                            },
                    )
        
                    LaunchedEffect(tab?.isLoading) {
                        if (tab?.isLoading == false) swipe.value?.isRefreshing = false
                    }
        
                    // 注意：二级页期间**不能**把首页移出组合 —— 首页的 backdrop 图层是靠绘制录制的，
                    // 不组合就等于图层失效，返回时玻璃会采到空/旧内容（0.80.6 的 bug）。
                    // 正确做法：保持组合，只跳过绘制。
                    AnimatedVisibility(
                        visible = isHome,
                        enter = fadeIn(Motion.enter()) + slideInVertically(Motion.slide()) { it / 24 },
                        exit = fadeOut(Motion.exit()),
                    ) {
                        // 二级页期间保持「组合但跳过绘制」：图层不被重录（省掉转场每帧的首页重绘），
                        // 又不销毁首页状态，返回时玻璃能正常采样。
                        val homeDraw = remember { mutableStateOf(true) }
                        LaunchedEffect(secondaryVisible) { homeDraw.value = !secondaryVisible }
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .fillMaxSize()
                                .drawWithContent { if (homeDraw.value) drawContent() }
                                .layerBackdrop(homeBackdrop),
                        ) {
                            HomeScreen(
                                controller = controller,
                                bookmarks = bookmarks,
                                history = history,
                                blockedCount = blockedCount,
                                wallpaperSource = homeWallpaperSource,
                                wallpaperUri = homeWallpaper,
                                showBookmarks = showHomeBookmarks,
                                onOpenPrivacy = { pushSecondary(SecondaryScreen.PRIVACY) },
                                onOpenHistory = { pushSecondary(SecondaryScreen.HISTORY) },
                                onOpenBookmarks = { pushSecondary(SecondaryScreen.BOOKMARKS) },
                            )
                        }
                    }
        
                    AnimatedVisibility(
                        visibleState = contextMenuState,
                        enter = fadeIn(Motion.enter(Motion.Duration.Quick)) + slideInVertically(Motion.slide()) { it / 4 },
                        exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.exit()) { it / 4 },
                    ) {
                        lastMenu?.let { request ->
                            ContextMenu(
                                visibleState = contextMenuState,
                                request = request,
                                controller = controller,
                                backdrop = if (isHome) homeBackdrop else null,
                                snapshot = if (isHome) null else sheetSnapshot,
                                onDismiss = { controller.dismissMenu() },
                            )
                        }
                    }
        
                    com.ravenxud.allegro.ui.components.MiuixSheet(
                        visible = moveTabId != null,
                        onDismiss = { moveTabId = null },
                        title = resources.getString(R.string.move_to_group),
                    ) {
                        val target = moveTabId
                        if (target != null) {
                            state.groups.forEach { group ->
                                com.ravenxud.allegro.ui.components.SheetRow(
                                    icon = AllegroIcons.Folder,
                                    title = group.name,
                                ) {
                                    scope.launch { controller.moveTabToGroup(target, group.id) }
                                    moveTabId = null
                                }
                            }
                            com.ravenxud.allegro.ui.components.SheetRow(
                                icon = AllegroIcons.Close,
                                title = resources.getString(R.string.move_to_group_none),
                            ) {
                                scope.launch { controller.moveTabToGroup(target, null) }
                                moveTabId = null
                            }
                        }
                    }
        
                    AnimatedVisibility(
                        visibleState = appMenuState,
                        enter = fadeIn(Motion.enter(Motion.Duration.Quick)) + slideInVertically(Motion.slide()) { it / 4 },
                        exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.exit()) { it / 4 },
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
                                pushSecondary(target)
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
                                pushSecondary(SecondaryScreen.OFFLINE)
                            },
                            onUserScripts = {
                                showAppMenu = false
                                pushSecondary(SecondaryScreen.USERSCRIPTS)
                            },
                            onPrivacyReport = {
                                showAppMenu = false
                                pushSecondary(SecondaryScreen.PRIVACY)
                            },
                            onPickElement = {
                                showAppMenu = false
                                controller.startElementPicker()
                            },
                            onSiteSettings = {
                                showAppMenu = false
                                pushSecondary(SecondaryScreen.SITE)
                            },
                            onTabGroups = {
                                showAppMenu = false
                                pushSecondary(SecondaryScreen.GROUPS)
                            },
                            backdrop = if (isHome) homeBackdrop else null,
                            snapshot = if (isHome) null else sheetSnapshot,
                            useDialogWindow = false,
                            scrollOffsetPx = sheetScrollDelta,
                            desktopSite = controller.isDesktopForCurrentSite(),
                            onToggleDesktopSite = { controller.toggleDesktopForCurrentSite() },
                            adBlockPaused = controller.isAdBlockPausedForCurrentSite(),
                            onToggleAdBlockSite = { controller.toggleAdBlockForCurrentSite() },
                        )
                    }
        
                }
            }
    }

            com.ravenxud.allegro.ui.nav.BrowserNavHost(
                stack = navStack,
                // 栈即事实源：pop 后 screen 自动派生
                onBack = { navStack.popSecondary() },
                rootContent = { BrowserRootView() },
                secondaryContent = { RenderSecondary(it) },
            )

    TabSwitcherOverlay(
        visible = showTabs,
        stub = tabsStub,
        thumb = tabsThumb,
        progressProvider = { tabsProgress },
        state = state,
        onSelect = { controller.selectTab(it); showTabs = false },
        onClose = { controller.closeTab(it) },
        onNew = { controller.openTab("about:blank"); showTabs = false },
        onNewIncognito = { controller.openTab("about:blank", incognito = true); showTabs = false },
        onReopen = { controller.restoreLastClosed(); showTabs = false },
        recentClosed = recentClosed,
        onRestoreClosed = { entity ->
            com.ravenxud.allegro.util.DiagLog.log("restore-closed", entity.url)
            controller.openTab(entity.url)
            showTabs = false
        },
        onDismiss = { showTabs = false },
        onDismissed = {
            tabsMounted = false
            tabsStub = null
        },
        onCloseOthers = { controller.closeOtherTabs(it) },
        onMoveToGroup = { moveTabId = it },
        onMove = { from, to -> controller.moveTab(from, to) },
        onCopyLink = { controller.copyToClipboard(it) },
        simplify = lowRam,
    )
            com.ravenxud.allegro.ui.components.PageToastHost(message = pageToast)

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
private fun TabCountButton(
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = 1.5.dp,
                        color = MiuixTheme.colorScheme.onSurface,
                        shape = com.ravenxud.allegro.ui.theme.Shapes.Indicator,
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
