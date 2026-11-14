package com.example.simplebrowser.data

import android.content.Context
import com.example.simplebrowser.data.model.SearchEngine
import com.example.simplebrowser.data.model.SearchEngines
import com.example.simplebrowser.data.model.ThemeMode
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
        const val KEY_UPDATE_REPO = "update_repo"
        const val DEFAULT_UPDATE_REPO = "Ravenxud/Allegro"
    }
}
