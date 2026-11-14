package com.ravenxud.allegro.desktop

/**
 * 桌面代理拿到的 HTML 需要一次后处理：
 * 当最终 URL（重定向后）与 WebView 请求的 URL 不同时，页面里的相对路径会指向错误的域名，
 * 于是图片/样式全挂。注入一个 `<base href="最终URL">` 即可修正。
 *
 * 纯字符串处理，便于单测。
 */
object BaseHref {

    /** 已存在 `<base>` 时不重复注入；[finalUrl] 为空或与 [requestedUrl] 相同时不注入。 */
    fun inject(html: String, requestedUrl: String, finalUrl: String): String {
        if (finalUrl.isBlank()) return html
        if (normalize(finalUrl) == normalize(requestedUrl)) return html
        if (Regex("<base\\b", RegexOption.IGNORE_CASE).containsMatchIn(html)) return html

        val tag = "<base href=\"${escape(finalUrl)}\">"
        val head = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE).find(html)
        return if (head != null) {
            val at = head.range.last + 1
            html.substring(0, at) + tag + html.substring(at)
        } else {
            tag + html
        }
    }

    private fun normalize(url: String): String = url.trim().substringBefore('#')

    private fun escape(value: String): String = value.replace("\"", "%22")
}
