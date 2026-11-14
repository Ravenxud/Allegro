package com.example.simplebrowser.adblock

/**
 * 一条网络规则（已编译成可快速匹配的形式）。
 *
 * - [hostSuffix] 非空：按域名（含子域）匹配；[pathContains] 非空时还要求 URL 含该路径片段
 * - [substring] 非空：整条 URL 包含匹配（用于 `/adserver/` 这类规则）
 * - [exception] 为 true：命中即放行，优先于拦截规则
 */
internal data class NetworkRule(
    val hostSuffix: String?,
    val pathContains: String?,
    val substring: String?,
    val exception: Boolean,
) {
    fun matches(host: String, url: String): Boolean {
        if (hostSuffix != null) {
            val hostHit = host == hostSuffix || host.endsWith(".$hostSuffix")
            if (!hostHit) return false
            return pathContains == null || url.contains(pathContains, ignoreCase = true)
        }
        if (substring != null) {
            return url.contains(substring, ignoreCase = true)
        }
        return false
    }
}

/**
 * 广告过滤引擎（EasyList 子集）。
 *
 * 支持：
 * - `||example.com^`         域名（含子域）拦截
 * - `||example.com/allow^`   带路径的规则（路径片段匹配）
 * - `@@||example.com^`       例外，优先于拦截
 * - `/adserver/`             路径片段包含匹配
 * - `example.com##.ad`       站点级元素隐藏
 * - `##.ad`                  全局元素隐藏
 * - 忽略 `!` 注释、`[Adblock Plus]` 头、空行
 *
 * 不支持（后续再补）：正则 `/.../`、`$` 修饰符的细分语义、`#@#` 例外。
 */
class FilterEngine private constructor(
    private val networkRules: List<NetworkRule>,
    private val cosmeticByHost: Map<String, List<String>>,
    private val cosmeticGlobal: List<String>,
) {

    val networkRuleCount: Int get() = networkRules.size
    val cosmeticRuleCount: Int get() = cosmeticGlobal.size + cosmeticByHost.values.sumOf { it.size }

    /**
     * 返回 true 表示应当拦截该请求。
     *
     * [includePathFragments] 为 false 时忽略纯片段规则（`/adserver/` 这类），
     * 用于"同站请求"——否则很容易把网站自己的 js/css 拦掉导致页面坏掉。
     */
    fun shouldBlock(url: String, includePathFragments: Boolean = true): Boolean {
        if (networkRules.isEmpty()) return false
        val host = hostOf(url) ?: return false
        var blocked = false
        for (rule in networkRules) {
            if (!includePathFragments && rule.hostSuffix == null) continue
            if (!rule.matches(host, url)) continue
            if (rule.exception) return false
            blocked = true
        }
        return blocked
    }

    /** 当前站点适用的隐藏选择器（全局 + 各级父域）。 */
    fun cosmeticSelectors(url: String): List<String> {
        val host = hostOf(url) ?: return cosmeticGlobal
        val result = ArrayList<String>(cosmeticGlobal.size + 8)
        result += cosmeticGlobal
        var probe = host
        while (true) {
            cosmeticByHost[probe]?.let { result += it }
            val dot = probe.indexOf('.')
            if (dot < 0) break
            probe = probe.substring(dot + 1)
            if (!probe.contains('.')) {
                cosmeticByHost[probe]?.let { result += it }
                break
            }
        }
        return result
    }

    companion object {

        val EMPTY = FilterEngine(emptyList(), emptyMap(), emptyList())

        fun parse(text: String): FilterEngine {
            val network = ArrayList<NetworkRule>(4096)
            val cosmeticHost = HashMap<String, MutableList<String>>()
            val cosmeticGlobal = ArrayList<String>()

            text.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach

                // 元素隐藏规则
                val cosmeticIndex = line.indexOf("##")
                if (cosmeticIndex >= 0) {
                    val hostPart = line.substring(0, cosmeticIndex)
                    val selector = line.substring(cosmeticIndex + 2).trim()
                    if (selector.isEmpty() || line.contains("#@#")) return@forEach
                    if (hostPart.isEmpty()) {
                        cosmeticGlobal += selector
                    } else {
                        hostPart.split(',').forEach { h ->
                            val host = h.trim().lowercase()
                            if (host.isNotEmpty()) {
                                cosmeticHost.getOrPut(host) { mutableListOf() } += selector
                            }
                        }
                    }
                    return@forEach
                }

                // 网络规则
                val exception = line.startsWith("@@")
                var body = if (exception) line.substring(2) else line

                // 去掉 $ 修饰符（保守：只取前半段做匹配）
                val dollar = body.indexOf('$')
                if (dollar > 0) body = body.substring(0, dollar)
                if (body.isBlank()) return@forEach

                if (body.startsWith("||")) {
                    val rest = body.substring(2).trimEnd('^', '|')
                    val slash = rest.indexOf('/')
                    val host = (if (slash >= 0) rest.substring(0, slash) else rest).trim()
                    val path = if (slash >= 0) {
                        rest.substring(slash).trimEnd('^', '|').ifBlank { null }
                    } else {
                        null
                    }
                    if (host.isNotEmpty() && !host.contains('*')) {
                        network += NetworkRule(
                            hostSuffix = host.lowercase(),
                            pathContains = path,
                            substring = null,
                            exception = exception,
                        )
                    }
                    return@forEach
                }

                val cleaned = body.trimStart('|').trimEnd('^', '|')
                if (cleaned.length >= 4 && !cleaned.contains('*')) {
                    network += NetworkRule(
                        hostSuffix = null,
                        pathContains = null,
                        substring = cleaned,
                        exception = exception,
                    )
                }
            }

            return FilterEngine(network, cosmeticHost, cosmeticGlobal)
        }

        fun hostOf(url: String): String? {
            val schemeEnd = url.indexOf("://")
            val start = if (schemeEnd > 0) schemeEnd + 3 else 0
            if (start >= url.length) return null
            val rest = url.substring(start)
            val end = rest.indexOfFirst { it == '/' || it == ':' || it == '?' }
            val host = if (end >= 0) rest.substring(0, end) else rest
            return host.lowercase().ifBlank { null }
        }
    }
}
