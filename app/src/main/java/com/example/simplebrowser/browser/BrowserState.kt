package com.example.simplebrowser.browser

import android.graphics.Bitmap

/**
 * 全局浏览器状态。UI 只订阅它，不直接操作 WebView。
 */
data class BrowserState(
    val tabs: List<TabState> = emptyList(),
    val activeTabId: String? = null,
    val isRestoring: Boolean = false,
) {

    val activeTab: TabState?
        get() = tabs.firstOrNull { it.id == activeTabId }
}

data class TabState(
    val id: String,
    val url: String = "about:blank",
    val title: String = "",
    val favicon: Bitmap? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktopMode: Boolean = false,
    val pageDarkEnabled: Boolean = false,
    /** 标签缩略图（内存态，不落盘）。 */
    val preview: Bitmap? = null,
    val lastAccessedAt: Long = 0L,
)
