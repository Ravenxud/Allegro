package com.ravenxud.allegro.browser

import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout

/**
 * 标签 ⇄ WebView 的映射。
 *
 * 最多 [maxLive] 个 WebView 常驻；超出时把最久未访问的通过 saveState 冻结，
 * 下次 `get()` 时用 restoreState 还原页面与历史。
 */
internal class WebViewPool(
    private val context: Context,
    private val callbacks: TabWebView.Callbacks,
    private val maxLive: Int = 4,
) {

    private val views = object : LinkedHashMap<String, TabWebView>(8, 0.75f, true) {}
    private val savedStates = mutableMapOf<String, Bundle>()

    fun peek(tabId: String): TabWebView? = views[tabId]

    fun get(tabId: String): TabWebView {
        views[tabId]?.let { return it }

        if (views.size >= maxLive) {
            val victim = views.entries.firstOrNull { it.key != tabId }
            if (victim != null) {
                val bundle = Bundle()
                victim.value.saveState(bundle)
                savedStates[victim.key] = bundle
                views.remove(victim.key)
                victim.value.destroy()
            }
        }

        val view = TabWebView(context).apply {
            this.tabId = tabId
            this.callbacks = this@WebViewPool.callbacks
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            init()
        }
        savedStates.remove(tabId)?.let { view.restoreState(it) }
        views[tabId] = view
        return view
    }

    fun remove(tabId: String) {
        views.remove(tabId)?.destroy()
        savedStates.remove(tabId)
    }

    fun clear() {
        views.values.forEach { it.destroy() }
        views.clear()
        savedStates.clear()
    }
}
