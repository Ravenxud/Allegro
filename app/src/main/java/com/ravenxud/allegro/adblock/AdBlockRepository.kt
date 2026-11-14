package com.ravenxud.allegro.adblock

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/** 一条可订阅的规则源。 */
data class FilterSource(
    val id: String,
    val title: String,
    val urls: List<String>,
    val defaultEnabled: Boolean = false,
)

/** 规则源清单：主源 + 镜像回退。 */
object FilterSources {
    val all: List<FilterSource> = listOf(
        FilterSource(
            id = "easylist-china",
            title = "EasyList China",
            urls = listOf(
                "https://easylist-downloads.adblockplus.org/easylistchina.txt",
                "https://fastly.jsdelivr.net/gh/easylist/easylistchina/easylistchina.txt",
            ),
            defaultEnabled = true,
        ),
        FilterSource(
            id = "easylist",
            title = "EasyList",
            urls = listOf(
                "https://easylist-downloads.adblockplus.org/easylist.txt",
                "https://fastly.jsdelivr.net/gh/easylist/easylist/easylist.txt",
            ),
        ),
        FilterSource(
            id = "easyprivacy",
            title = "EasyPrivacy",
            urls = listOf(
                "https://easylist-downloads.adblockplus.org/easyprivacy.txt",
                "https://fastly.jsdelivr.net/gh/easylist/easylist/easyprivacy.txt",
            ),
        ),
        FilterSource(
            id = "cjx-annoyance",
            title = "CJX's Annoyance",
            urls = listOf(
                "https://fastly.jsdelivr.net/gh/cjx82630/cjxlist/cjx-annoyance.txt",
                "https://cdn.jsdelivr.net/gh/cjx82630/cjxlist/cjx-annoyance.txt",
            ),
            defaultEnabled = true,
        ),
    )

    fun byId(id: String): FilterSource? = all.firstOrNull { it.id == id }
}

/**
 * 规则仓库：内置兜底 + 联网更新 + 拦截统计。
 *
 * 更新策略：逐个源、逐个镜像尝试；下载大小上限 12MB；
 * 全部失败保留旧规则（不会出现"更新后反而不拦"）。
 */
class AdBlockRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir: File
        get() = File(context.filesDir, "adblock").apply { mkdirs() }

    private val _engine = MutableStateFlow(FilterEngine.EMPTY)
    val engine: StateFlow<FilterEngine> = _engine.asStateFlow()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _blocked = MutableStateFlow(prefs.getInt(KEY_BLOCKED, 0))
    val blocked: StateFlow<Int> = _blocked.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private val counter = AtomicInteger(prefs.getInt(KEY_BLOCKED, 0))

    @Volatile
    private var loaded = false

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    fun sourceEnabled(id: String): Boolean =
        prefs.getBoolean("$KEY_SOURCE$id", FilterSources.byId(id)?.defaultEnabled ?: false)

    fun setSourceEnabled(id: String, value: Boolean) {
        prefs.edit().putBoolean("$KEY_SOURCE$id", value).apply()
    }

    /** 站点白名单：命中时该站点完全不拦。 */
    fun isSiteAllowed(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return prefs.getStringSet(KEY_ALLOW, emptySet())?.contains(host.lowercase()) == true
    }

    fun setSiteAllowed(host: String, allowed: Boolean) {
        if (host.isBlank()) return
        val current = (prefs.getStringSet(KEY_ALLOW, emptySet()) ?: emptySet()).toMutableSet()
        if (allowed) current += host.lowercase() else current -= host.lowercase()
        prefs.edit().putStringSet(KEY_ALLOW, current).apply()
    }

    fun allowedSites(): Set<String> = prefs.getStringSet(KEY_ALLOW, emptySet()) ?: emptySet()

    fun lastUpdated(id: String): Long = prefs.getLong("$KEY_TIME$id", 0L)

    /** 首次使用或冷启动时加载：内置规则 + 已下载规则。 */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        val builder = StringBuilder()
        runCatching {
            context.assets.open("adblock/builtin.txt").bufferedReader().use { builder.append(it.readText()) }
        }
        FilterSources.all.forEach { source ->
            if (!sourceEnabled(source.id)) return@forEach
            val file = cachedFile(source.id)
            if (file.exists()) runCatching { builder.append('\n').append(file.readText()) }
        }
        // 本地规则不进 base，走 withExtraCosmetic 叠加，增删时无需重新解析整套规则
        baseEngine = FilterEngine.parse(builder.toString())
        applyLocalRules()
        loaded = true
    }

    suspend fun updateAll(): Int = withContext(Dispatchers.IO) {
        _updating.value = true
        var success = 0
        try {
            FilterSources.all.forEach { source ->
                if (!sourceEnabled(source.id)) return@forEach
                val text = download(source) ?: return@forEach
                runCatching {
                    cachedFile(source.id).writeText(text)
                    prefs.edit().putLong("$KEY_TIME${source.id}", System.currentTimeMillis()).apply()
                }
                success++
            }
            if (success > 0) {
                loaded = false
                ensureLoaded()
            }
        } finally {
            _updating.value = false
        }
        success
    }

    private fun download(source: FilterSource): String? {
        for (url in source.urls) {
            val body = runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "SimpleBrowser/1.0")
                }
                connection.inputStream.use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    val out = StringBuilder()
                    var total = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_BYTES) return null
                        out.append(String(buffer, 0, read, Charsets.UTF_8))
                    }
                    out.toString()
                }
            }.getOrNull()
            // 简单有效性判断：过滤列表至少包含注释行，长度过小视为错误页
            if (body != null && body.length > 1024 && (body.contains("##") || body.contains("||"))) {
                return body
            }
        }
        return null
    }

    /** 按域名统计拦截次数（隐私报告用）。 */
    private val hostCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val pendingCount = java.util.concurrent.atomic.AtomicInteger(0)
    private var baseEngine = FilterEngine.EMPTY

    /** Top N 拦截域名。 */
    fun stats(limit: Int = 12): List<Pair<String, Int>> =
        runCatching { persistedHosts().sortedByDescending { it.second }.take(limit) }
            .getOrDefault(emptyList())

    /** 去重后的拦截域名总数（隐私报告头部的「覆盖 N 个域名」）。 */
    fun trackedHostCount(): Int = runCatching { persistedHosts().size }.getOrDefault(0)

    private fun persistedHosts(): List<Pair<String, Int>> {
        val raw = prefs.getString(KEY_HOSTS, null) ?: return emptyList()
        val obj = org.json.JSONObject(raw)
        return obj.keys().asSequence()
            .map { it to obj.optInt(it) }
            .filter { it.second > 0 }
            .toList()
    }

    private fun recordHost(url: String) {
        val host = FilterEngine.hostOf(url) ?: return
        hostCounts.merge(host, 1) { a, b -> a + b }
        // 每 24 次落盘一次，避免高频请求下反复写 SharedPreferences
        if (pendingCount.incrementAndGet() % 24 == 0) flushHosts()
    }

    /**
     * 把内存计数合并进持久化统计。
     *
     * 注意：这里必须读**全量**域名再合并，不能复用 [stats] 的 Top N，
     * 否则每次落盘都会把榜单以外的域名丢掉（历史实现有此 bug）。
     */
    @Synchronized
    fun flushHosts() {
        runCatching {
            val merged = HashMap<String, Int>()
            persistedHosts().forEach { (h, c) -> merged[h] = c }
            hostCounts.forEach { (h, c) -> merged[h] = (merged[h] ?: 0) + c }
            val obj = org.json.JSONObject()
            merged.forEach { (h, c) -> obj.put(h, c) }
            prefs.edit().putString(KEY_HOSTS, obj.toString()).apply()
            hostCounts.clear()
        }
    }

    fun localCosmeticRules(): List<String> =
        prefs.getStringSet(KEY_LOCAL_RULES, emptySet())?.sorted().orEmpty()

    fun addLocalCosmeticRule(selector: String) {
        val clean = selector.trim()
        if (clean.isEmpty()) return
        val current = (prefs.getStringSet(KEY_LOCAL_RULES, emptySet()) ?: emptySet()).toMutableSet()
        current += clean
        prefs.edit().putStringSet(KEY_LOCAL_RULES, current).apply()
        applyLocalRules()
    }

    fun removeLocalCosmeticRule(selector: String) {
        val current = (prefs.getStringSet(KEY_LOCAL_RULES, emptySet()) ?: emptySet()).toMutableSet()
        current -= selector.trim()
        prefs.edit().putStringSet(KEY_LOCAL_RULES, current).apply()
        applyLocalRules()
    }

    fun clearLocalCosmeticRules() {
        prefs.edit().putStringSet(KEY_LOCAL_RULES, emptySet()).apply()
        applyLocalRules()
    }

    /** 把本地规则叠加到 base 引擎上（不重新解析文本，主线程安全）。 */
    private fun applyLocalRules() {
        _engine.value = baseEngine.withExtraCosmetic(localCosmeticRules())
    }

    fun onRequestChecked(blocked: Boolean, url: String = "") {
        if (!blocked) return
        if (url.isNotEmpty()) recordHost(url)
        val value = counter.incrementAndGet()
        _blocked.value = value
        if (value % 20 == 0) prefs.edit().putInt(KEY_BLOCKED, value).apply()
    }

    /** 清空统计（拦截总数 + 域名榜）。 */
    fun resetStats() {
        counter.set(0)
        _blocked.value = 0
        hostCounts.clear()
        pendingCount.set(0)
        prefs.edit().putInt(KEY_BLOCKED, 0).apply()
        prefs.edit().putString(KEY_HOSTS, "{}").apply()
    }

    private fun cachedFile(id: String) = File(dir, "$id.txt")

    private companion object {
        const val PREFS = "simple_browser_adblock"
        const val KEY_ENABLED = "enabled"
        const val KEY_BLOCKED = "blocked_total"
        const val KEY_SOURCE = "source_"
        const val KEY_TIME = "updated_"
        const val KEY_ALLOW = "allow_sites"
        const val KEY_HOSTS = "blocked_hosts"
        const val KEY_LOCAL_RULES = "local_cosmetic"
        const val MAX_BYTES = 12 * 1024 * 1024
    }
}
