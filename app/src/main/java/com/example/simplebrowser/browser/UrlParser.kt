package com.example.simplebrowser.browser

import java.net.URLEncoder

/** 把地址栏输入解析为 URL：像网址就补协议，否则走搜索。 */
object UrlParser {

    private val IP = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?(/.*)?$")

    fun toUrl(input: String, searchTemplate: String): String {
        val text = input.trim()
        if (text.isEmpty()) return "about:blank"
        if (hasScheme(text)) return text

        val host = text.substringBefore('/')
        val looksLikeHost = !text.contains(' ') && (
            text.contains('.') ||
                host.equals("localhost", ignoreCase = true) ||
                host.startsWith("localhost:", ignoreCase = true) ||
                IP.matches(text)
            )

        return if (looksLikeHost) {
            "https://$text"
        } else {
            searchTemplate.replace("%s", URLEncoder.encode(text, "UTF-8"))
        }
    }

    private fun hasScheme(text: String): Boolean {
        if (text.startsWith("about:") || text.startsWith("data:")) return true
        val index = text.indexOf("://")
        if (index <= 0) return false
        return text.substring(0, index).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
    }
}
