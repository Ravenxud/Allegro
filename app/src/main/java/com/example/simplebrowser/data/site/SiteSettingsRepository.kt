package com.example.simplebrowser.data.site

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 站点设置仓库：一个 SharedPreferences，key 是域名，value 是 [SiteSettingsCodec] 的紧凑串。
 *
 * 只保存非默认值：恢复默认 = 删除该 key，列表里也就自然消失了。
 */
class SiteSettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _sites = MutableStateFlow(load())
    val sites: StateFlow<Map<String, SiteSettings>> = _sites.asStateFlow()

    /** 该域名（含父域继承）生效的设置，没有配置过时返回 null。 */
    fun forHost(host: String?): SiteSettings? = SiteSettingsCodec.resolve(_sites.value, host)

    /** 显式配置过的站点（不含继承）。 */
    fun configured(): List<SiteSettings> = _sites.value.values.sortedBy { it.host }

    /** 保存设置；等于默认值就直接删掉这条记录，避免存一堆"全开"的死数据。 */
    fun save(settings: SiteSettings) {
        val host = settings.host.trim().lowercase()
        if (host.isEmpty()) return
        val editor = prefs.edit()
        if (settings.isDefault) {
            editor.remove(host)
        } else {
            editor.putString(host, SiteSettingsCodec.encode(settings))
        }
        editor.apply()
        _sites.value = _sites.value.toMutableMap().apply {
            if (settings.isDefault) remove(host) else put(host, settings.copy(host = host))
        }
    }

    fun reset(host: String) {
        val clean = host.trim().lowercase()
        prefs.edit().remove(clean).apply()
        _sites.value = _sites.value.toMutableMap().apply { remove(clean) }
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        _sites.value = emptyMap()
    }

    private fun load(): Map<String, SiteSettings> {
        val raw = prefs.all
        val result = HashMap<String, SiteSettings>(raw.size)
        raw.forEach { (host, value) ->
            if (value is String) {
                val settings = SiteSettingsCodec.decode(host, value)
                if (!settings.isDefault) result[host] = settings
            }
        }
        return result
    }

    private companion object {
        const val PREFS = "simple_browser_site_settings"
    }
}
