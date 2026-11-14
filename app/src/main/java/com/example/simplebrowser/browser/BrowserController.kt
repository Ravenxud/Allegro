package com.example.simplebrowser.browser

import android.webkit.WebView
import kotlinx.coroutines.flow.StateFlow

/** 长按菜单请求。 */
data class MenuRequest(
    val tabId: String,
    val url: String,
    val kind: Kind,
    val pageUrl: String,
) {
    enum class Kind { LINK, IMAGE }
}

/**
 * 浏览器唯一入口。UI 只能调用这里的意图方法，
 * 不允许直接持有或操作 WebView。
 */
interface BrowserController {

    val state: StateFlow<BrowserState>
    val menuRequest: StateFlow<MenuRequest?>

    /** Compose 侧提供承载 WebView 的容器。 */
    fun attachHost(container: android.widget.FrameLayout)

    /** 为当前标签生成缩略图（打开标签面板前调用）。 */
    fun refreshPreview()

    /** 抓一张当前页面的位图，作为切换器动画的 Stub（异步，主线程回调）。 */
    fun captureStub(onCaptured: (android.graphics.Bitmap?) -> Unit)

    // 标签
    fun openTab(url: String, background: Boolean = false): String
    fun selectTab(tabId: String)

    /** 按顺序切换标签（+1 下一个 / -1 上一个，循环）。 */
    fun switchTab(offset: Int)
    fun closeTab(tabId: String)

    /** 当前站点是否已暂停广告过滤。 */
    fun isAdBlockPausedForCurrentSite(): Boolean

    /** 切换"本站暂停广告过滤"，返回切换后的状态（true = 已暂停）。 */
    fun toggleAdBlockForCurrentSite(): Boolean

    /** 调整标签顺序（拖拽排序）。 */
    fun moveTab(fromIndex: Int, toIndex: Int)

    /** 只保留指定标签，其余全部关闭。 */
    fun closeOtherTabs(keepTabId: String)
    fun restoreLastClosed(): Boolean

    // 导航
    fun load(url: String)
    fun reload()
    fun stopLoading()
    fun goBack()
    fun goForward()
    fun setDesktopMode(enabled: Boolean)
    fun togglePageDark()

    // 页面交互
    fun evaluate(script: String, onResult: ((String?) -> Unit)? = null)
    fun findInPage(query: String)

    /** 当前标签的页面是否已在顶部（用于决定下拉刷新是否接管手势）。 */
    fun isActiveTabAtTop(): Boolean

    /** 提取当前页正文并生成阅读页；失败回调 null。 */
    fun openReader(onReady: (html: String?) -> Unit)

    /** 读取当前页纯文本（用于朗读）。 */
    fun readPageText(onReady: (text: String) -> Unit)

    /** 保存当前页为离线页（内部先存 MHTML，再转成自包含 HTML）。 */
    fun saveOfflinePage()

    /** 离线页列表（阅读/离线页界面用）。 */
    fun offlineEntries(): List<com.example.simplebrowser.reader.OfflinePageStore.Entry>

    /** 打开离线页（传入 [offlineEntries] 里的 fileName）。 */
    fun openOfflinePage(fileName: String)

    fun deleteOfflineEntry(id: String)

    /** 元素选择器：进入拾取模式，用户点选元素后生成隐藏规则。 */
    fun startElementPicker()

    /** 分享当前页。 */
    fun sharePage()

    // 长按菜单
    fun dismissMenu()
    fun copyToClipboard(text: String)
    fun download(url: String)
    fun share(url: String)

    // 生命周期与持久化
    suspend fun recentClosed(limit: Int = 3): List<com.example.simplebrowser.data.entity.ClosedTabEntity>

    fun persistNow()
    fun restoreTabs()
    fun release()
}
