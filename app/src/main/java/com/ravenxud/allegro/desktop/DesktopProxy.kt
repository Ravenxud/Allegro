package com.ravenxud.allegro.desktop

import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import com.ravenxud.allegro.util.DiagLog
import java.io.ByteArrayInputStream

/**
 * 桌面版的"单文档代理"：拦下主文档，用 [DesktopDocumentFetcher] 自己抓，
 * 再以 [WebResourceResponse] 交回 WebView。
 *
 * 只在「主文档 + GET + 桌面模式」时介入；任何异常都返回 null，让 WebView 走原路径，
 * 保证"桌面版失败 ≠ 页面打不开"。
 */
class DesktopProxy(
    private val fetcher: DesktopDocumentFetcher = DesktopDocumentFetcher(),
    private val cacheTtlMs: Long = 30_000,
    private val cookieManager: () -> CookieManager? = { runCatching { CookieManager.getInstance() }.getOrNull() },
) {

    private data class CacheEntry(val at: Long, val html: String, val finalUrl: String, val charset: String)

    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?) = size > 12
    }

    data class Outcome(val response: WebResourceResponse?, val reason: String? = null)

    fun intercept(url: String, requestHeaders: Map<String, String>, userAgent: String): Outcome {
        val cached = synchronized(cache) {
            cache[url]?.takeIf { System.currentTimeMillis() - it.at < cacheTtlMs }
        }
        if (cached != null) {
            DiagLog.log("desktop-cache", url)
            return Outcome(response(cached.html, cached.charset, url, cached.finalUrl))
        }

        val cookies = runCatching { cookieManager()?.getCookie(url) }.getOrNull()
        val headers = DesktopHeaders.build(
            url = url,
            userAgent = userAgent,
            passThrough = requestHeaders,
            cookie = cookies,
        )

        DiagLog.log("desktop-fetch", url)
        return when (val result = fetcher.fetch(url, headers)) {
            is DesktopDocumentFetcher.Result.Html -> {
                // 把每一跳的 Set-Cookie 写回 WebView 的 Cookie 仓库，否则登录态会丢
                result.cookies.forEach { (cookieUrl, header) ->
                    runCatching { cookieManager()?.setCookie(cookieUrl, header) }
                }
                runCatching { cookieManager()?.flush() }
                val html = BaseHref.inject(result.html, url, result.finalUrl)
                synchronized(cache) {
                    cache[url] = CacheEntry(System.currentTimeMillis(), html, result.finalUrl, result.charset)
                }
                DiagLog.log("desktop-ok", "${result.finalUrl} ${html.length}B")
                Outcome(response(html, result.charset, url, result.finalUrl))
            }
            is DesktopDocumentFetcher.Result.Skipped -> {
                DiagLog.log("desktop-skip", result.reason)
                Outcome(null, result.reason)
            }
            is DesktopDocumentFetcher.Result.Failed -> {
                DiagLog.log("desktop-fail", result.reason)
                Outcome(null, result.reason)
            }
        }
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun response(html: String, charset: String, requestedUrl: String, finalUrl: String): WebResourceResponse {
        val headers = HashMap<String, String>()
        headers["Cache-Control"] = "no-store"
        if (finalUrl != requestedUrl) headers["Content-Location"] = finalUrl
        return WebResourceResponse(
            "text/html",
            charset.ifBlank { "utf-8" },
            200,
            "OK",
            headers,
            ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)),
        )
    }
}
