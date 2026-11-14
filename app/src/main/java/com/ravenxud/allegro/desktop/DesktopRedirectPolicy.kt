package com.ravenxud.allegro.desktop

/**
 * 桌面模式下载主文档时的重定向策略。
 *
 * 只有自己跟随重定向，才能保证"物理上不循环"：
 * - 最多 [maxHops] 跳；
 * - 同一站点内 `m.` / `www.` 互换**只允许一次**（B 站就是在这里无限互踢）；
 * - 回到已经访问过的 URL 直接停止（A→B→A 即循环）；
 * - 非 http(s) 停止。
 *
 * 纯逻辑，便于单测。
 */
class DesktopRedirectPolicy(private val maxHops: Int = 5) {

    private val visited = ArrayList<String>()
    var hops: Int = 0
        private set
    var mobileFlips: Int = 0
        private set

    fun start(url: String) {
        visited.clear()
        visited += normalize(url)
        hops = 0
        mobileFlips = 0
    }

    /**
     * @return 应该跟随的目标 URL；null 表示停止（把当前响应当作最终结果）。
     */
    fun next(currentUrl: String, location: String): String? {
        if (hops >= maxHops) return null
        val target = resolve(currentUrl, location) ?: return null
        if (!target.startsWith("http://") && !target.startsWith("https://")) return null

        val key = normalize(target)
        if (visited.any { it == key }) return null // A→B→A 循环

        val fromHost = host(currentUrl)
        val toHost = host(target)
        if (fromHost != null && toHost != null && isMobileSwap(fromHost, toHost)) {
            if (mobileFlips >= 1) return null
            mobileFlips++
        }

        visited += key
        hops++
        return target
    }

    /** `m.example.com` 与 `example.com` / `www.example.com` 视为移动版互换。 */
    fun isMobileSwap(a: String, b: String): Boolean {
        val na = stripWww(a)
        val nb = stripWww(b)
        if (na == nb) return false
        return stripMobile(na) == stripMobile(nb) && (na.startsWith("m.") || nb.startsWith("m."))
    }

    private fun stripWww(host: String): String = if (host.startsWith("www.")) host.drop(4) else host

    private fun stripMobile(host: String): String = if (host.startsWith("m.")) host.drop(2) else host

    private fun normalize(url: String): String {
        val noFragment = url.substringBefore('#')
        return if (noFragment.endsWith("/")) noFragment else noFragment + "/"
    }

    private fun host(url: String): String? =
        runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()

    private fun resolve(base: String, location: String): String? {
        if (location.isBlank()) return null
        val trimmed = location.trim()
        runCatching { java.net.URI(trimmed) }.getOrNull()?.let { if (it.isAbsolute) return trimmed }
        return runCatching { java.net.URI(base).resolve(trimmed).toString() }.getOrNull()
    }
}
