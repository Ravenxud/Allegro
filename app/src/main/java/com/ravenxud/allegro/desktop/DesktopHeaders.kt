package com.ravenxud.allegro.desktop

/**
 * 桌面版请求头构造。
 *
 * 这里是整个桌面模式的**关键**：WebView 改不了 `Sec-CH-UA-*`，所以我们自己发请求，
 * 显式写出与 `User-Agent` 一致的 Client Hints。服务端两套判断就统一了。
 *
 * 纯函数，便于单测（不依赖 Android）。
 */
object DesktopHeaders {

    const val UA_MAC =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    const val UA_WINDOWS =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    /** 桌面模式默认 UA。 */
    const val UA_DEFAULT = UA_MAC

    private const val SEC_CH_UA = "\"Chromium\";v=\"123\", \"Not:A-Brand\";v=\"99\""

    /**
     * @param passThrough 原请求里值得保留的头（Accept / Accept-Language / Referer 等）
     * @param cookie `CookieManager.getCookie(url)` 的结果，可能为 null
     */
    fun build(
        url: String,
        userAgent: String = UA_DEFAULT,
        passThrough: Map<String, String> = emptyMap(),
        cookie: String? = null,
    ): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["User-Agent"] = userAgent
        headers["Accept"] = passThrough["Accept"] ?: DEFAULT_ACCEPT
        headers["Accept-Language"] = passThrough["Accept-Language"] ?: "zh-CN,zh;q=0.9,en;q=0.8"
        headers["Upgrade-Insecure-Requests"] = "1"

        // 关键三件套：与 UA 对齐
        headers["Sec-CH-UA"] = SEC_CH_UA
        headers["Sec-CH-UA-Mobile"] = "?0"
        headers["Sec-CH-UA-Platform"] = if (userAgent.contains("Windows")) "\"Windows\"" else "\"macOS\""

        // 这些头会让服务端把请求当成"客户端导航"，按 UA/hints 决定是否跳移动版
        headers["Sec-Fetch-Site"] = "none"
        headers["Sec-Fetch-Mode"] = "navigate"
        headers["Sec-Fetch-Dest"] = "document"
        headers["Sec-Fetch-User"] = "?1"

        passThrough["Referer"]?.takeIf { it.isNotBlank() }?.let { headers["Referer"] = it }
        cookie?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }

        // 不能带的条件头：我们把压缩交给 OkHttp 自己处理
        headers.keys.removeAll { it.equals("Accept-Encoding", true) }
        return headers
    }

    private const val DEFAULT_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
}
