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
import com.example.simplebrowser.adblock.FilterEngine
import com.example.simplebrowser.data.entity.DownloadEntity
import com.example.simplebrowser.data.entity.TabEntity
import com.example.simplebrowser.download.DownloadCenter
import com.example.simplebrowser.data.repo.HistoryRepository
import com.example.simplebrowser.data.repo.TabsRepository
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
        updateTab(view.tabId) { it.copy(url = url, isLoading = true, progress = 0) }
        jsInjector.inject(view, InjectAt.DOC_START, JsContext(url, isDesktop(view.tabId)))
    }

    override fun onPageFinished(view: TabWebView, url: String) {
        updateTab(view.tabId) {
            it.copy(
                url = url,
                title = it.title.ifBlank { url },
                isLoading = false,
                progress = 100,
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward(),
                lastAccessedAt = System.currentTimeMillis(),
            )
        }
        jsInjector.inject(view, InjectAt.DOC_END, JsContext(url, isDesktop(view.tabId)))
        injectCosmetic(view, url)
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
        adBlock.onRequestChecked(blocked)
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
