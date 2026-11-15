package com.ravenxud.allegro.browser

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

    /**
     * 切换器用的**清晰**缩略图：降采样到 720 宽、不做模糊。
     * 预模糊的 Stub 只适合当背景，拿去当卡片会显糊。
     */
    fun captureThumb(onCaptured: (android.graphics.Bitmap?) -> Unit)

    /**
     * 面板玻璃底图：在打开面板**之前**调用（此时 WebView 仍可见）。
     * 传入 [onReady] 时，等快照落地后再执行（保证底图是当前页面）。
     */
    fun captureForSheet(onReady: ((android.graphics.Bitmap?) -> Unit)? = null)

    /**
     * 底栏磨砂底图：抓取窗口坐标 [rect]（底栏正上方一条），预模糊后回主线程。
     * 内部节流（约 11fps），重复调用不会堆积。
     */
    fun captureBarFrost(rect: android.graphics.Rect, onCaptured: (android.graphics.Bitmap?) -> Unit)

    /** 面板是否可见：滚动时据此节流刷新玻璃底图。 */
    fun setSheetVisible(visible: Boolean)

    /** 页面缩放（百分比）：立即应用到所有已存在的 WebView。 */
    fun applyTextZoom(percent: Int)

    /** 相对"最近一次抓图"的滚动位移（px）：面板据此平移玻璃，做到零延迟跟手。 */
    val sheetScrollDelta: kotlinx.coroutines.flow.StateFlow<Int>

    /** 面板玻璃底图（未就绪为 null）。 */
    val sheetSnapshot: kotlinx.coroutines.flow.StateFlow<android.graphics.Bitmap?>

    fun clearSheetSnapshot()

    // 标签
    fun openTab(url: String, background: Boolean = false, incognito: Boolean = false): String
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

    // 站点设置

    /** 当前标签页的域名；首页/本地页返回 null。 */
    fun currentHost(): String?

    /** 该域名生效的设置（含父域继承），没配置过时返回全默认值。 */
    fun siteSettingsOf(host: String): com.ravenxud.allegro.data.site.SiteSettings

    fun saveSiteSettings(settings: com.ravenxud.allegro.data.site.SiteSettings)

    fun resetSiteSettings(host: String)

    /** 用户显式配置过的站点（不含继承）。 */
    fun configuredSiteSettings(): List<com.ravenxud.allegro.data.site.SiteSettings>

    // 标签分组

    suspend fun createTabGroup(name: String): String

    suspend fun renameTabGroup(id: String, name: String)

    /** 删除分组，成员回到「未分组」。 */
    suspend fun deleteTabGroup(id: String)

    suspend fun setTabGroupCollapsed(id: String, collapsed: Boolean)

    /** groupId 为 null 表示移出分组。 */
    suspend fun moveTabToGroup(tabId: String, groupId: String?)

    /** 当前站点是否处于桌面版。 */
    fun isDesktopForCurrentSite(): Boolean

    /** 一键切换"本站桌面版"，返回切换后的状态。 */
    fun toggleDesktopForCurrentSite(): Boolean

    /** 清除当前站点 Cookie 与 WebStorage。 */
    fun clearCurrentSiteData()

    /** 保存当前页为离线页（内部先存 MHTML，再转成自包含 HTML）。 */
    fun saveOfflinePage()

    /** 离线页列表（阅读/离线页界面用）。 */
    fun offlineEntries(): List<com.ravenxud.allegro.reader.OfflinePageStore.Entry>

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
    suspend fun recentClosed(limit: Int = 3): List<com.ravenxud.allegro.data.entity.ClosedTabEntity>

    fun persistNow()
    fun restoreTabs()
    fun release()
}
