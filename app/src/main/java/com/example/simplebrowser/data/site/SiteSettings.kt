package com.example.simplebrowser.data.site

/**
 * 单站点设置。默认全开（等于「跟随全局」），只有用户改过的项才会被写入存储。
 *
 * 广告过滤不在这里：它复用 [com.example.simplebrowser.adblock.AdBlockRepository] 的白名单，
 * 避免两套真值互相打架（早期"本站暂停过滤"已经在用了）。
 */
data class SiteSettings(
    val host: String,
    val javaScript: Boolean = true,
    val images: Boolean = true,
    val desktopUa: Boolean = false,
) {
    val isDefault: Boolean
        get() = javaScript && images && !desktopUa

    /** 已关闭/已开启的项，用于列表里的一行摘要。 */
    fun deviations(): List<String> = buildList {
        if (!javaScript) add("JS 关")
        if (!images) add("图片关")
        if (desktopUa) add("桌面版")
    }
}

/** 站点设置的序列化与域名匹配（纯 Kotlin，便于单测）。 */
object SiteSettingsCodec {

    /** `js=0;img=1;ua=1`；解析失败的字段回退默认值。 */
    fun encode(settings: SiteSettings): String = buildString {
        append("js=").append(if (settings.javaScript) 1 else 0)
        append(";img=").append(if (settings.images) 1 else 0)
        append(";ua=").append(if (settings.desktopUa) 1 else 0)
    }

    fun decode(host: String, raw: String?): SiteSettings {
        if (raw.isNullOrBlank()) return SiteSettings(host)
        val map = raw.split(';').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
        }.toMap()
        return SiteSettings(
            host = host,
            javaScript = map["js"]?.let { it != "0" } ?: true,
            images = map["img"]?.let { it != "0" } ?: true,
            desktopUa = map["ua"] == "1",
        )
    }

    /**
     * 域名继承链：`m.news.example.com` → `m.news.example.com`、`news.example.com`、`example.com`。
     *
     * 只到两级后缀为止，避免把 `com` 也当成可配置站点；
     * 也不做 eTLD+1 判断（`example.co.uk` 会多出一层 `co.uk`，但用户几乎不会去配置它）。
     */
    fun hostCandidates(host: String?): List<String> {
        if (host.isNullOrBlank()) return emptyList()
        val clean = host.trim().lowercase()
        val labels = clean.split('.').filter { it.isNotEmpty() }
        if (labels.size < 2) return listOf(clean)
        val result = ArrayList<String>(labels.size)
        var start = 0
        while (start <= labels.size - 2) {
            result += labels.subList(start, labels.size).joinToString(".")
            start++
        }
        return result
    }

    /** 优先精确命中，其次逐级父域。 */
    fun resolve(store: Map<String, SiteSettings>, host: String?): SiteSettings? =
        hostCandidates(host).firstNotNullOfOrNull { store[it] }
}
