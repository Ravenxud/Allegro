package com.ravenxud.allegro.data

import android.content.Context
import com.ravenxud.allegro.data.model.SearchEngine
import com.ravenxud.allegro.data.model.SearchEngines
import com.ravenxud.allegro.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 轻量设置仓库：主题、搜索引擎。
 * 目前用 SharedPreferences（同步读取、零额外依赖），
 * 若后续需要跨进程/加密再迁 DataStore 或 SQLCipher。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _searchEngineId = MutableStateFlow(
        prefs.getString(KEY_ENGINE, null) ?: SearchEngines.default.id
    )
    val searchEngineId: StateFlow<String> = _searchEngineId.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun cycleTheme(): ThemeMode {
        val next = _themeMode.value.next()
        setThemeMode(next)
        return next
    }

    fun setSearchEngine(id: String) {
        prefs.edit().putString(KEY_ENGINE, id).apply()
        _searchEngineId.value = id
    }

    private val _desktopUa = MutableStateFlow(
        com.ravenxud.allegro.data.model.DesktopUa.fromKey(prefs.getString(KEY_DESKTOP_UA, null)),
    )
    /** 桌面版使用的 UA 平台（macOS / Windows）。 */
    val desktopUa: StateFlow<com.ravenxud.allegro.data.model.DesktopUa> = _desktopUa.asStateFlow()

    fun setDesktopUa(value: com.ravenxud.allegro.data.model.DesktopUa) {
        prefs.edit().putString(KEY_DESKTOP_UA, value.key).apply()
        _desktopUa.value = value
    }

    private val _alwaysDesktop = MutableStateFlow(prefs.getBoolean(KEY_ALWAYS_DESKTOP, false))
    /** 全局「始终请求桌面版」。站点设置可覆盖它。 */
    val alwaysDesktop: StateFlow<Boolean> = _alwaysDesktop.asStateFlow()

    fun setAlwaysDesktop(value: Boolean) {
        prefs.edit().putBoolean(KEY_ALWAYS_DESKTOP, value).apply()
        _alwaysDesktop.value = value
    }

    private val _reduceMotion = MutableStateFlow(prefs.getBoolean(KEY_REDUCE_MOTION, false))
    /** 应用内「减少动画」：与系统设置取或。 */
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    fun setReduceMotion(value: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, value).apply()
        _reduceMotion.value = value
    }

    private val _textZoom = MutableStateFlow(prefs.getInt(KEY_TEXT_ZOOM, 100))
    /** 页面缩放（百分比，80–140）。 */
    val textZoom: StateFlow<Int> = _textZoom.asStateFlow()

    fun setTextZoom(percent: Int) {
        val clamped = percent.coerceIn(80, 140)
        prefs.edit().putInt(KEY_TEXT_ZOOM, clamped).apply()
        _textZoom.value = clamped
    }

    private val _homeBookmarks = MutableStateFlow(prefs.getBoolean(KEY_HOME_BOOKMARKS, true))
    val homeBookmarksEnabled: StateFlow<Boolean> = _homeBookmarks.asStateFlow()

    fun setHomeBookmarksEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_BOOKMARKS, value).apply()
        _homeBookmarks.value = value
    }



    /** 更新源：GitHub 仓库 "owner/repo"，留空表示不检查更新。默认指向本项目。 */
    fun updateRepo(): String = prefs.getString(KEY_UPDATE_REPO, DEFAULT_UPDATE_REPO).orEmpty()


    fun setUpdateRepo(value: String) {
        prefs.edit().putString(KEY_UPDATE_REPO, value.trim()).apply()
    }

    fun searchEngine(): SearchEngine =
        SearchEngines.builtIn.firstOrNull { it.id == _searchEngineId.value } ?: SearchEngines.default

    private fun readTheme(): ThemeMode {
        val raw = prefs.getString(KEY_THEME, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private companion object {
        const val PREFS = "simple_browser_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_ENGINE = "search_engine"
        const val KEY_HOME_BOOKMARKS = "home_bookmarks"
        const val KEY_TEXT_ZOOM = "text_zoom"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_UPDATE_REPO = "update_repo"
        const val DEFAULT_UPDATE_REPO = "Ravenxud/Allegro"
        const val KEY_ALWAYS_DESKTOP = "always_desktop"
        const val KEY_DESKTOP_UA = "desktop_ua"
    }
}
