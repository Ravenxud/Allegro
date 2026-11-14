package com.example.simplebrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.app.DownloadManager
import android.os.Bundle
import android.os.Parcel
import android.util.Base64
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.net.toUri
import com.example.simplebrowser.R
import com.example.simplebrowser.data.entity.ClosedTabEntity
import com.example.simplebrowser.adblock.AdBlockRepository
import com.example.simplebrowser.adblock.CosmeticInjector
import com.example.simplebrowser.reader.MhtmlArchive
import com.example.simplebrowser.reader.OfflinePageStore
import com.example.simplebrowser.reader.ReaderExtractor
import com.example.simplebrowser.adblock.FilterEngine
import com.example.simplebrowser.data.entity.DownloadEntity
import com.example.simplebrowser.data.entity.TabEntity
import com.example.simplebrowser.download.DownloadCenter
import com.example.simplebrowser.data.repo.HistoryRepository
import com.example.simplebrowser.data.repo.TabsRepository
import com.example.simplebrowser.data.site.SiteSettings
import com.example.simplebrowser.data.site.SiteSettingsRepository
import com.example.simplebrowser.js.InjectAt
import com.example.simplebrowser.js.JsContext
import com.example.simplebrowser.js.JsInjector
import com.example.simplebrowser.js.JsRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * 基于系统 WebView 的实现。
 *
 * 标签与 WebView 的映射由 [WebViewPool] 管理：
 * - 最多 4 个 WebView 常驻，超出后把最久未用的通过 [WebView.saveState] 冻结；
 * - 再次切回时用 restoreState 还原，所以切换标签不再重新加载。
 */
class WebViewBrowserController(
    private val context: Context,
    private val tabsRepository: TabsRepository,
    private val historyRepository: HistoryRepository,
    private val downloadCenter: DownloadCenter,
    private val adBlock: AdBlockRepository,
    private val siteSettings: SiteSettingsRepository,
) : BrowserController, TabWebView.Callbacks {

    companion object {
        const val HOME_URL = "https://www.baidu.com"
        private const val MAX_LIVE_WEBVIEWS = 4
        private const val MAX_RESTORED_TABS = 8
        private const val TAG = "StubCapture"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(BrowserState())
    override val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _menu = MutableStateFlow<MenuRequest?>(null)
    override val menuRequest: StateFlow<MenuRequest?> = _menu.asStateFlow()

    private var host: FrameLayout? = null

    private val pool = WebViewPool(context, this)
    private val jsInjector = JsInjector(JsRegistry.default())
    private val menuActions = MenuActions(context)
    private val offlineStore = OfflinePageStore(context)
    private var readerCallback: ((String?) -> Unit)? = null
    private var lastClosed: ClosedTabEntity? = null

    /** 从数据库读出、尚未挂到 WebView 上的 saveState 字节。 */
    private val pendingStates = mutableMapOf<String, ByteArray>()

    // ------------------------------------------------------------- 宿主

    override fun attachHost(container: FrameLayout) {
        host = container
        _state.value.activeTabId?.let { showTab(it) }
    }

    /** 取 WebView；若是首次创建且有存档，则跨进程还原页面。 */
    private fun ensureView(tabId: String): TabWebView {
        val view = pool.get(tabId)
        pendingStates.remove(tabId)?.let { bytes ->
            unmarshall(bytes)?.let { bundle -> runCatching { view.restoreState(bundle) } }
        }
        return view
    }

    private fun showTab(tabId: String) {
        val container = host ?: return
        val view = ensureView(tabId)
        container.removeAllViews()
        (view.parent as? FrameLayout)?.removeView(view)
        container.addView(view)
    }

    private fun activeView(): TabWebView? {
        val id = _state.value.activeTabId ?: return null
        return pool.peek(id)
    }

    // ------------------------------------------------------------- 标签

    override fun openTab(url: String, background: Boolean): String {
        val id = UUID.randomUUID().toString()
        val tab = TabState(
            id = id,
            url = url,
            title = url,
            lastAccessedAt = System.currentTimeMillis(),
        )
        _state.update { it.copy(tabs = it.tabs + tab) }
        if (!background) {
            capturePreview(_state.value.activeTabId)
            _state.update { it.copy(activeTabId = id) }
            showTab(id)
            ensureView(id).loadUrl(url)
        }
        return id
    }

    override fun selectTab(tabId: String) {
        val current = _state.value
        if (current.activeTabId == tabId) return
        val tab = current.tabs.firstOrNull { it.id == tabId } ?: return

        capturePreview(current.activeTabId)
        _state.update { it.copy(activeTabId = tabId) }
        showTab(tabId)
        pool.peek(tabId)?.let { view ->
            if (view.url == null && tab.url != "about:blank") view.loadUrl(tab.url)
        }
    }

    override fun switchTab(offset: Int) {
        val current = _state.value
        if (current.tabs.size < 2) return
        val index = current.tabs.indexOfFirst { it.id == current.activeTabId }
        if (index < 0) return
        val target = ((index + offset) % current.tabs.size + current.tabs.size) % current.tabs.size
        selectTab(current.tabs[target].id)
    }

    override fun closeTab(tabId: String) {
        val current = _state.value
        val index = current.tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return

        val closing = current.tabs[index]
        pool.peek(tabId)?.let { view -> (view.parent as? FrameLayout)?.removeView(view) }
        pool.remove(tabId)
        if (_menu.value?.tabId == tabId) _menu.value = null

        lastClosed = ClosedTabEntity(
            url = closing.url,
            title = closing.title,
            closedAt = System.currentTimeMillis(),
        )
        persistClosedTab(lastClosed!!)

        val remaining = current.tabs.toMutableList().apply { removeAt(index) }
        val nextActiveId = when {
            remaining.isEmpty() -> null
            current.activeTabId == tabId -> remaining[minOf(index, remaining.lastIndex)].id
            else -> current.activeTabId
        }
        _state.update { it.copy(tabs = remaining, activeTabId = nextActiveId) }

        if (remaining.isEmpty()) {
            host?.removeAllViews()
        } else if (nextActiveId != current.activeTabId) {
            showTab(nextActiveId!!)
            val next = remaining.first { it.id == nextActiveId }
            pool.peek(nextActiveId)?.let { if (it.url == null) it.loadUrl(next.url) }
        }
    }

    override fun isAdBlockPausedForCurrentSite(): Boolean {
        val host = FilterEngine.hostOf(_state.value.activeTab?.url.orEmpty()) ?: return false
        return adBlock.isSiteAllowed(host)
    }

    override fun toggleAdBlockForCurrentSite(): Boolean {
        val host = FilterEngine.hostOf(_state.value.activeTab?.url.orEmpty()) ?: return false
        val paused = !adBlock.isSiteAllowed(host)
        adBlock.setSiteAllowed(host, paused)
        // 立即重载，让规则生效
        activeView()?.reload()
        return paused
    }

    override fun moveTab(fromIndex: Int, toIndex: Int) {
        val current = _state.value
        if (fromIndex == toIndex) return
        if (fromIndex !in current.tabs.indices) return
        val target = toIndex.coerceIn(0, current.tabs.lastIndex)
        if (target == fromIndex) return
        val list = current.tabs.toMutableList()
        list.add(target, list.removeAt(fromIndex))
        _state.update { it.copy(tabs = list) }
    }

    override fun closeOtherTabs(keepTabId: String) {
        val current = _state.value
        val keep = current.tabs.firstOrNull { it.id == keepTabId } ?: return
        current.tabs.filter { it.id != keepTabId }.forEach { pool.remove(it.id) }
        _state.update { it.copy(tabs = listOf(keep), activeTabId = keepTabId) }
        showTab(keepTabId)
        pool.peek(keepTabId)?.let { if (it.url == null) it.loadUrl(keep.url) }
    }

    override fun restoreLastClosed(): Boolean {
        val closed = lastClosed ?: return false
        lastClosed = null
        val id = openTab(closed.url)
        _state.update { s ->
            s.copy(tabs = s.tabs.map { if (it.id == id) it.copy(title = closed.title) else it })
        }
        return true
    }

    // ------------------------------------------------------------- 导航

    override fun load(url: String) {
        val activeId = _state.value.activeTabId
        if (activeId == null) {
            openTab(url)
            return
        }
        _state.update { s ->
            s.copy(tabs = s.tabs.map {
                if (it.id == activeId) it.copy(url = url, isLoading = true, progress = 0) else it
            })
        }
        ensureView(activeId).loadUrl(url)
    }

    override fun reload() {
        activeView()?.reload()
    }

    override fun stopLoading() {
        activeView()?.stopLoading()
    }

    override fun goBack() {
        activeView()?.let { if (it.canGoBack()) it.goBack() }
    }

    override fun goForward() {
        activeView()?.let { if (it.canGoForward()) it.goForward() }
    }

    override fun setDesktopMode(enabled: Boolean) {
        val activeId = _state.value.activeTabId ?: return
        _state.update { s ->
            s.copy(tabs = s.tabs.map {
                if (it.id == activeId) it.copy(desktopMode = enabled) else it
            })
        }
        val view = activeView() ?: return
        view.settings.userAgentString = if (enabled) DESKTOP_UA else view.mobileUserAgent
        view.reload()
    }

    override fun togglePageDark() {
        val activeId = _state.value.activeTabId ?: return
        val enabled = !(_state.value.tabs.firstOrNull { it.id == activeId }?.pageDarkEnabled ?: false)
        updateTab(activeId) { it.copy(pageDarkEnabled = enabled) }
        activeView()?.evaluateJavascript("window.__sbDarkPage && window.__sbDarkPage($enabled)", null)
    }

    override suspend fun recentClosed(limit: Int): List<ClosedTabEntity> =
        tabsRepository.recentClosed(limit)

    override fun evaluate(script: String, onResult: ((String?) -> Unit)?) {
        activeView()?.evaluateJavascript(script) { value -> onResult?.invoke(value) }
    }

    override fun findInPage(query: String) {
        activeView()?.findAllAsync(query)
    }

    // --------------------------------------------------------- 长按菜单

    override fun dismissMenu() {
        _menu.value = null
    }

    override fun copyToClipboard(text: String) {
        menuActions.copy(text)
        _menu.value = null
    }

    override fun download(url: String) {
        downloadCenter.enqueue(url)
        _menu.value = null
    }

    override fun share(url: String) {
        menuActions.share(url)
        _menu.value = null
    }

    // --------------------------------------------------------- 持久化

    override fun persistNow() {
        val tabs = _state.value.tabs
        val snapshot = tabs.mapIndexed { index, tab ->
            TabEntity(
                id = tab.id,
                url = tab.url,
                title = tab.title,
                favicon = tab.favicon?.toPngBytes(),
                position = index,
                pinned = false,
                state = pool.peek(tab.id)?.let { view ->
                    val bundle = Bundle()
                    runCatching { view.saveState(bundle) }
                    marshall(bundle)
                },
                lastAccessedAt = tab.lastAccessedAt,
            )
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { tabsRepository.saveTabs(snapshot) }
        }
    }

    override fun restoreTabs() {
        scope.launch {
            _state.update { it.copy(isRestoring = true) }
            val saved = runCatching { tabsRepository.loadTabs() }.getOrDefault(emptyList())
            _state.update { it.copy(isRestoring = false) }

            // 用户在恢复完成前已经开了标签（点了快捷方式/深链），放弃恢复，避免把新标签顶掉
            if (_state.value.tabs.isNotEmpty()) return@launch
            if (saved.isEmpty()) return@launch

            saved.forEach { entity ->
                entity.state?.let { pendingStates[entity.id] = it }
            }

            val restored = saved.take(MAX_RESTORED_TABS).map {
                TabState(
                    id = it.id,
                    url = it.url,
                    title = it.title.ifBlank { it.url },
                    lastAccessedAt = it.lastAccessedAt,
                )
            }
            // 冷启动一律停在首页：恢复出来的标签放在列表里，但不激活、不加载
            _state.update { it.copy(tabs = restored, activeTabId = null) }
        }
    }


    override fun release() {
        persistNow()
        pool.clear()
        host = null
        scope.cancel()
    }

    // --------------------------------------------------- WebView 回调

    override fun onPageStarted(view: TabWebView, url: String) {
        updateTab(view.tabId) { it.copy(url = displayUrl(it, url), isLoading = true, progress = 0) }
        val site = applySiteSettings(view, url)
        if (site?.javaScript == false) return
        jsInjector.inject(view, InjectAt.DOC_START, JsContext(url, isDesktop(view.tabId)))
    }

    /**
     * 把「站点设置」落到这台 WebView 上（WebView 的设置是全局单例，所以每次导航都要重设一遍）。
     *
     * 返回该站点生效的设置，调用方据此决定要不要注入 JS。
     */
    private fun applySiteSettings(view: TabWebView, url: String): SiteSettings? {
        val site = siteSettings.forHost(FilterEngine.hostOf(url))
        // 站点设置里的「桌面版」优先，但不清掉工具栏上临时切过来的桌面模式
        val desktop = site?.desktopUa == true || isDesktop(view.tabId)
        runCatching {
            view.settings.javaScriptEnabled = site?.javaScript ?: true
            view.settings.blockNetworkImage = site?.images == false
            view.settings.userAgentString = if (desktop) DESKTOP_UA else view.mobileUserAgent
            view.settings.loadsImagesAutomatically = site?.images ?: true
        }
        return site
    }

    /**
     * 离线页实际加载的是 `file://…/offline/x.html`，但地址栏应继续显示原网址。
     */
    private fun displayUrl(tab: TabState, actual: String): String =
        if (actual.startsWith("file://") && actual.contains("/offline/") &&
            tab.url.isNotBlank() && !tab.url.startsWith("file://")
        ) {
            tab.url
        } else {
            actual
        }

    override fun onPageFinished(view: TabWebView, url: String) {
        updateTab(view.tabId) {
            it.copy(
                url = displayUrl(it, url),
                title = it.title.ifBlank { url },
                isLoading = false,
                progress = 100,
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward(),
                lastAccessedAt = System.currentTimeMillis(),
            )
        }
        val site = siteSettings.forHost(FilterEngine.hostOf(url))
        if (site?.javaScript != false) {
            jsInjector.inject(view, InjectAt.DOC_END, JsContext(url, isDesktop(view.tabId)))
            injectCosmetic(view, url)
        }
        scheduleScrollFallback(view)
        if (_state.value.tabs.firstOrNull { it.id == view.tabId }?.pageDarkEnabled == true) {
            view.evaluateJavascript("window.__sbDarkPage && window.__sbDarkPage(true)", null)
        }
        recordHistory(view.tabId, url)
    }

    override fun onProgressChanged(view: TabWebView, progress: Int) {
        updateTab(view.tabId) { it.copy(progress = progress, isLoading = progress < 100) }
    }

    override fun onTitleChanged(view: TabWebView, title: String) {
        updateTab(view.tabId) { it.copy(title = title) }
    }

    override fun onFaviconReceived(view: TabWebView, icon: Bitmap?) {
        updateTab(view.tabId) { it.copy(favicon = icon) }
    }

    override fun onUrlChanged(view: TabWebView, url: String) {
        updateTab(view.tabId) { it.copy(url = url) }
    }

    override fun onMainFrameError(
        view: TabWebView,
        errorCode: Int,
        description: String,
        failingUrl: String?,
    ) {
        val url = failingUrl ?: return
        updateTab(view.tabId) { it.copy(isLoading = false, progress = 100) }
        view.loadDataWithBaseURL(null, ErrorPage.build(context, url, description), "text/html", "utf-8", null)
    }

    override fun onDownloadRequested(view: TabWebView, request: TabWebView.DownloadRequest) {
        downloadCenter.enqueue(
            url = request.url,
            userAgent = request.userAgent,
            mimeType = request.mimeType,
            contentDisposition = request.contentDisposition,
        )
    }

    override fun onBlobDownload(view: TabWebView, fileName: String, mimeType: String, base64: String) {
        scope.launch(Dispatchers.IO) {
            val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@launch
            dir.mkdirs()
            val file = File(dir, safeName)
            runCatching {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                file.writeBytes(bytes)
            }.onSuccess {
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.download_started, safeName),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    override fun onBlobTooLarge(view: TabWebView, fileName: String) {
        Toast.makeText(context, R.string.download_too_large, Toast.LENGTH_SHORT).show()
    }

    override fun shouldBlockRequest(url: String, isForMainFrame: Boolean): Boolean {
        if (isForMainFrame) return false
        if (!adBlock.enabled.value) return false

        // 同站请求只允许域名类规则命中：路径片段规则（如 /analytics.js）
        // 很容易误伤网站自己的静态资源，导致样式/js 缺失
        val pageHost = FilterEngine.hostOf(state.value.activeTab?.url.orEmpty())
        // 站点白名单：整站放行
        if (adBlock.isSiteAllowed(pageHost)) return false
        val requestHost = FilterEngine.hostOf(url)
        val sameSite = pageHost != null && requestHost != null && pageHost == requestHost

        val blocked = adBlock.engine.value.shouldBlock(
            url = url,
            includePathFragments = !sameSite,
        )
        adBlock.onRequestChecked(blocked, url)
        return blocked
    }

    /** tabId -> 页面是否在顶部（默认 true，避免首帧误判）。 */
    private val atTopMap = mutableMapOf<String, Boolean>()

    /** tabId -> 该页面是否允许下拉刷新（虚拟滚动页面会禁止）。 */
    private val pullAllowedMap = mutableMapOf<String, Boolean>()

    override fun isActiveTabAtTop(): Boolean {
        val id = _state.value.activeTabId ?: return true
        val allowed = pullAllowedMap[id] ?: true
        return allowed && (atTopMap[id] ?: true)
    }

    override fun onScrollState(view: TabWebView, atTop: Boolean, allowPull: Boolean, scrollY: Int) {
        atTopMap[view.tabId] = atTop
        pullAllowedMap[view.tabId] = allowPull
        scrollReported.add(view.tabId)
    }

    /** 已成功收到滚动上报的标签；用于"上报超时 → 禁用下拉"的兜底。 */
    private val scrollReported = mutableSetOf<String>()

    override fun openReader(onReady: (String?) -> Unit) {
        val view = activeView()
        if (view == null) { onReady(null); return }
        readerCallback = onReady
        // 先注入 Readability 本体（90KB），再注入提取逻辑，避免单条脚本过大
        val library = ReaderExtractor.readabilityScript()
        if (library.isBlank()) { onReady(null); return }
        val extractor = ReaderExtractor.script()
        view.evaluateJavascript(library, null)
        view.postDelayed({ view.evaluateJavascript(extractor, null) }, 60)
        scope.launch {
            kotlinx.coroutines.delay(12000)
            // 超时兜底：避免一直等回调（Readability 90KB 注入在慢页面可能超过 4 秒）
            readerCallback?.let { cb -> readerCallback = null; cb(null) }
        }
    }

    override fun readPageText(onReady: (text: String) -> Unit) {
        val view = activeView() ?: return onReady("")
        val script = """
            (function(){
              if (window.top !== window) return '';
              var main = document.querySelector('article,main,[role=main]') || document.body;
              var text = main ? main.innerText : '';
              text = text.replace(/\s+
/g, '
').trim();
              if (text.length > 20000) text = text.slice(0, 20000);
              if (window.__sbPageText) window.__sbPageText(window.__sbBridgeSecret || '', text);
            })();
        """.trimIndent()
        pageTextCallback = onReady
        view.evaluateJavascript(script, null)
    }

    override fun saveOfflinePage() {
        val view = activeView() ?: return
        val tab = _state.value.activeTab ?: return
        val id = UUID.randomUUID().toString()
        val temp = File(context.cacheDir, "offline-$id.mhtml")
        val archived = runCatching { view.saveWebArchive(temp.absolutePath) }.isSuccess &&
            temp.exists() && temp.length() > 0
        if (!archived) {
            runCatching { temp.delete() }
            Toast.makeText(context, R.string.offline_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, R.string.offline_saving, Toast.LENGTH_SHORT).show()
        // WebView 存出来的是 MHTML，WebView 自己打不开，必须转成自包含 HTML
        CoroutineScope(Dispatchers.IO).launch {
            val html = runCatching { MhtmlArchive.toHtml(temp.readBytes()) }.getOrNull()
            if (html == null) {
                runCatching { temp.delete() }
                scope.launch {
                    Toast.makeText(context, R.string.offline_save_failed, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val target = offlineStore.htmlFileFor(id)
            runCatching { target.writeText(html.html) }
            runCatching { temp.delete() }
            offlineStore.save(tab.title, tab.url, target)
            scope.launch {
                Toast.makeText(context, R.string.offline_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------------------ 站点设置

    override fun currentHost(): String? =
        FilterEngine.hostOf(_state.value.activeTab?.url.orEmpty())

    override fun siteSettingsOf(host: String): SiteSettings =
        siteSettings.forHost(host) ?: SiteSettings(host)

    override fun saveSiteSettings(settings: SiteSettings) {
        siteSettings.save(settings)
    }

    override fun resetSiteSettings(host: String) {
        siteSettings.reset(host)
    }

    override fun configuredSiteSettings(): List<SiteSettings> = siteSettings.configured()

    override fun clearCurrentSiteData() {
        val host = currentHost() ?: return
        clearSiteData(host)
    }

    /** 清 Cookie 与（API 29+）该源的 WebStorage；其它数据受 WebView API 限制只能全局清。 */
    private fun clearSiteData(host: String) {
        runCatching {
            val cookieManager = android.webkit.CookieManager.getInstance()
            val origin = "https://$host"
            cookieManager.getCookie(origin)?.split(';')?.forEach { pair ->
                val name = pair.substringBefore('=').trim()
                if (name.isNotEmpty()) {
                    cookieManager.setCookie("$origin/", "$name=; Max-Age=0; Path=/")
                }
            }
            cookieManager.flush()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.webkit.WebStorage.getInstance().deleteOrigin(origin)
            }
        }
        Toast.makeText(context, R.string.site_data_cleared, Toast.LENGTH_SHORT).show()
    }

    override fun offlineEntries() = offlineStore.load()

    override fun openOfflinePage(fileName: String) {
        val entry = offlineStore.load().firstOrNull { it.fileName == fileName }
        if (entry == null) {
            Toast.makeText(context, R.string.offline_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val html = offlineStore.htmlFileFor(entry.id)
        if (html.exists() && html.length() > 0) {
            openOfflineHtml(entry, html)
            return
        }
        // 旧版本存的 .mhtml 在首次打开时惰性转换
        val source = offlineStore.fileFor(entry)
        if (!source.exists()) {
            Toast.makeText(context, R.string.offline_missing, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, R.string.offline_opening, Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            val converted = runCatching {
                MhtmlArchive.toHtml(source.readBytes(), maxInlineBytes = 16 * 1024 * 1024)
            }.getOrNull()
            if (converted == null) {
                scope.launch {
                    Toast.makeText(context, R.string.offline_open_failed, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            runCatching { html.writeText(converted.html) }
            if (html.exists()) {
                offlineStore.replaceFile(entry, html.name, html.length())
                runCatching { source.delete() }
                scope.launch { openOfflineHtml(entry, html) }
            }
        }
    }

    private fun openOfflineHtml(entry: OfflinePageStore.Entry, file: File) {
        val tabId = openTab("file://${file.absolutePath}")
        updateTab(tabId) { it.copy(title = entry.title.ifBlank { entry.url }, url = entry.url) }
    }

    override fun deleteOfflineEntry(id: String) {
        offlineStore.load().firstOrNull { it.id == id }?.let { offlineStore.delete(it) }
    }

    override fun startElementPicker() {
        val view = activeView() ?: return
        val secret = com.example.simplebrowser.js.BridgeAuth.current()
        view.evaluateJavascript(pickerScript(secret), null)
        Toast.makeText(context, R.string.picker_pick_hint, Toast.LENGTH_SHORT).show()
    }

    override fun onElementPicked(view: TabWebView, selector: String) {
        if (selector.isBlank()) return
        adBlock.addLocalCosmeticRule(selector)
        Toast.makeText(context, R.string.picker_saved, Toast.LENGTH_SHORT).show()
        view.reload()
    }

    private fun pickerScript(secret: String): String = """
        (function(){
          var SECRET = '$secret';
          if (window.__sbPicker) { window.__sbPicker.stop(); return; }
          if (!window.__sbHandlePicked && window.SimpleBrowserBridge) {
            window.__sbHandlePicked = function(sel){
              try { SimpleBrowserBridge.onElementPicked(SECRET, sel); } catch (e) {}
            };
          }
          var hover = null;
          var unsafe = /[\s\x00-\x1f]/;

          function esc(v){
            return String(v).replace(/[^a-zA-Z0-9_-]/g, function(c){ return '\\' + c; });
          }
          function classesOf(el){
            var raw = (el.className || '').toString().trim().split(/\s+/).filter(Boolean);
            var out = [];
            for (var i = 0; i < raw.length && out.length < 2; i++) {
              var c = raw[i];
              if (c.length > 36) continue;
              if (/\d{3,}/.test(c)) continue;                       // 带哈希/序号，不稳
              if (/^(active|hover|focus|show|shown|open|opened|selected|current|visible)$/i.test(c)) continue;
              if (unsafe.test(c)) continue;
              out.push(c);
            }
            return out;
          }
          function typeIndex(el){
            var n = 0;
            var p = el.parentElement;
            if (!p) return 1;
            for (var i = 0; i < p.children.length; i++) {
              if (p.children[i].tagName === el.tagName) n++;
              if (p.children[i] === el) return n;
            }
            return 1;
          }
          function build(el){
            if (!el || el.nodeType !== 1) return '';
            if (el.id && !/\d{4,}/.test(el.id) && !unsafe.test(el.id)) return '#' + esc(el.id);
            var parts = [];
            var cur = el;
            for (var depth = 0; cur && cur.nodeType === 1 && depth < 4; depth++) {
              var tag = cur.tagName.toLowerCase();
              if (tag === 'body' || tag === 'html') break;
              var cls = classesOf(cur);
              var piece = tag + cls.map(function(c){ return '.' + esc(c); }).join('');
              var parent = cur.parentElement;
              if (parent && !cur.id && parent.children.length > 1) {
                var same = 0;
                for (var i = 0; i < parent.children.length; i++) {
                  var sib = parent.children[i];
                  if (sib.tagName === cur.tagName && classesOf(sib).join('|') === cls.join('|')) same++;
                }
                if (same > 1) piece += ':nth-of-type(' + typeIndex(cur) + ')';
              }
              parts.unshift(piece);
              if (cls.length >= 1 && depth >= 1) break;             // 类名够特征就不再往上爬
              cur = parent;
            }
            return parts.join(' > ');
          }
          function clearHover(){
            if (hover && hover.style) hover.style.outline = '';
          }
          function onMove(e){
            var el = e.target;
            if (el === hover) return;
            clearHover();
            if (!el || el.nodeType !== 1) return;
            hover = el;
            el.style.outline = '2px solid #3D7EFF';
          }
          function onClick(e){
            e.preventDefault();
            e.stopPropagation();
            var selector = build(e.target);
            clearHover();
            stop();
            if (selector) window.__sbHandlePicked(selector);
          }
          function onKey(e){
            if (e.key === 'Escape') { clearHover(); stop(); }
          }
          function stop(){
            document.removeEventListener('mousemove', onMove, true);
            document.removeEventListener('click', onClick, true);
            document.removeEventListener('keydown', onKey, true);
            clearHover();
            hover = null;
            window.__sbPicker = null;
          }
          window.__sbPicker = { stop: stop };
          document.addEventListener('mousemove', onMove, true);
          document.addEventListener('click', onClick, true);
          document.addEventListener('keydown', onKey, true);
        })();
    """.trimIndent()

    override fun sharePage() {
        val tab = _state.value.activeTab ?: return
        share(tab.url)
    }

    private var pageTextCallback: ((String) -> Unit)? = null

    override fun onPageText(view: TabWebView, text: String) {
        val cb = pageTextCallback ?: return
        pageTextCallback = null
        cb(text)
    }

    override fun onReaderExtracted(view: TabWebView, json: String) {
        val cb = readerCallback ?: return
        readerCallback = null
        if (json.isBlank()) cb(null) else cb(ReaderExtractor.readerHtml(json))
    }

    override fun onLongPress(view: TabWebView, hit: WebView.HitTestResult) {
        val extra = hit.extra ?: return
        val kind = when (hit.type) {
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> MenuRequest.Kind.IMAGE
            else -> MenuRequest.Kind.LINK
        }
        _menu.value = MenuRequest(
            tabId = view.tabId,
            url = extra,
            kind = kind,
            pageUrl = view.url.orEmpty(),
        )
    }

    // ------------------------------------------------------------- 内部

    private fun isDesktop(tabId: String): Boolean =
        _state.value.tabs.firstOrNull { it.id == tabId }?.desktopMode == true

    override fun captureStub(onCaptured: (Bitmap?) -> Unit) {
        val activity = context as? android.app.Activity ?: return onCaptured(null)
        val window = activity.window ?: return onCaptured(null)
        val decor = window.decorView
        if (decor.width <= 0 || decor.height <= 0) return onCaptured(null)
        if (activeView()?.visibility != android.view.View.VISIBLE) {
            android.util.Log.w(TAG, "stub skip: webview not visible")
            return onCaptured(null)
        }

        // 整窗截图：地址栏与底栏会一起进入 stub，缩进卡片时控件随之收缩（Luna 同款）
        val windowRect = android.graphics.Rect(0, 0, decor.width, decor.height)
        requestCopy(window, windowRect) { bitmap ->
            if (bitmap != null) {
                onCaptured(scaleDown(bitmap))
            } else {
                android.util.Log.e(TAG, "window copy failed")
                onCaptured(null)
            }
        }
    }

    private fun requestCopy(
        window: android.view.Window,
        rect: android.graphics.Rect,
        onResult: (Bitmap?) -> Unit,
    ) {
        if (rect.width() <= 0 || rect.height() <= 0) return onResult(null)
        val bitmap = runCatching {
            Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return onResult(null)

        runCatching {
            android.view.PixelCopy.request(
                window,
                rect,
                bitmap,
                { result ->
                    if (result == android.view.PixelCopy.SUCCESS) {
                        onResult(bitmap)
                    } else {
                        android.util.Log.w(TAG, "PixelCopy failed: $result rect=$rect")
                        bitmap.recycle()
                        onResult(null)
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper()),
            )
        }.onFailure {
            android.util.Log.w(TAG, "PixelCopy threw", it)
            bitmap.recycle()
            onResult(null)
        }
    }

    private fun scaleDown(source: Bitmap): Bitmap? {
        val targetWidth = minOf(source.width, 720)
        if (targetWidth <= 0) return null
        val targetHeight = (source.height.toFloat() / source.width * targetWidth)
            .toInt().coerceAtLeast(1)
        val scaled = runCatching {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        }.getOrNull()
        if (scaled !== source) source.recycle()
        return scaled
    }

    override fun refreshPreview() {
        capturePreview(_state.value.activeTabId)
    }

    private fun capturePreview(tabId: String?) {
        val id = tabId ?: return
        val view = pool.peek(id) ?: return
        if (view.width <= 0 || view.height <= 0) return
        val bitmap = runCatching {
            val targetWidth = 360
            val targetHeight = (view.height.toFloat() / view.width * targetWidth)
                .toInt().coerceIn(1, 720)
            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val scale = targetWidth.toFloat() / view.width
            canvas.scale(scale, scale)
            view.draw(canvas)
            bmp
        }.getOrNull() ?: return
        updateTab(id) { it.copy(preview = bitmap) }
    }

    private fun marshall(bundle: Bundle): ByteArray? = runCatching {
        val parcel = Parcel.obtain()
        try {
            bundle.writeToParcel(parcel, 0)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }.getOrNull()

    private fun unmarshall(bytes: ByteArray): Bundle? = runCatching {
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            Bundle.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }.getOrNull()

    private fun updateTab(tabId: String, block: (TabState) -> TabState) {
        _state.update { s ->
            s.copy(tabs = s.tabs.map { if (it.id == tabId) block(it) else it })
        }
    }

    /**
     * 兜底：页面加载完成后 2.5 秒内若没收到任何滚动上报，
     * 说明无法判定该页面的滚动位置（虚拟滚动 / 脚本手势），直接禁用下拉刷新。
     */
    private fun scheduleScrollFallback(view: TabWebView) {
        val tabId = view.tabId
        scrollReported.remove(tabId)
        scope.launch {
            kotlinx.coroutines.delay(2500)
            if (tabId !in scrollReported) {
                pullAllowedMap[tabId] = false
                atTopMap[tabId] = false
            }
        }
    }

    private fun injectCosmetic(view: TabWebView, url: String) {
        if (!adBlock.enabled.value) return
        if (siteSettings.forHost(FilterEngine.hostOf(url))?.javaScript == false) return
        val selectors = adBlock.engine.value.cosmeticSelectors(url)
        if (selectors.isEmpty()) return
        view.evaluateJavascript(CosmeticInjector.script(selectors), null)
    }

    private fun recordHistory(tabId: String, url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val title = _state.value.tabs.firstOrNull { it.id == tabId }?.title.orEmpty()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { historyRepository.record(url, title) }
        }
    }

    private fun persistClosedTab(entity: ClosedTabEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { tabsRepository.addClosedTab(entity) }
        }
    }

    private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}
