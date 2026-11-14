package com.ravenxud.allegro.sync

import android.content.Context
import android.os.Build
import com.ravenxud.allegro.data.SettingsRepository
import com.ravenxud.allegro.data.entity.BookmarkEntity
import com.ravenxud.allegro.data.entity.BookmarkFolderEntity
import com.ravenxud.allegro.data.repo.BookmarkRepository
import com.ravenxud.allegro.data.site.SiteSettings
import com.ravenxud.allegro.data.site.SiteSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一次同步的结果，直接给界面显示。 */
data class SyncOutcome(
    val bookmarks: Int,
    val folders: Int,
    val sites: Int,
    val at: Long,
)

/**
 * WebDAV 同步仓库。
 *
 * 策略是**整包覆盖**，不做双向增量合并：
 * - 上传：本机数据 → 远端文件（覆盖）；
 * - 下载：远端文件 → 本机（书签/文件夹按 url、id upsert，站点设置按域名覆盖，然后应用偏好）。
 *
 * 理由：书签/站点设置总量小（几百条以内），增量合并需要墓碑与冲突解决，
 * 收益远小于复杂度；"整包覆盖 + 用户自己选择方向"已经能满足换机迁移与多设备统一。
 */
class SyncRepository(
    context: Context,
    private val bookmarks: BookmarkRepository,
    private val siteSettings: SiteSettingsRepository,
    private val settings: SettingsRepository,
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<WebDavConfig> = _config.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC, 0L))
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    fun saveConfig(config: WebDavConfig) {
        _config.value = config
        prefs.edit()
            .putString(KEY_URL, config.baseUrl)
            .putString(KEY_USER, config.user)
            .putString(KEY_PASS, config.password)
            .putString(KEY_DIR, config.directory)
            .putString(KEY_FILE, config.fileName)
            .apply()
    }

    private fun readConfig(): WebDavConfig = WebDavConfig(
        baseUrl = prefs.getString(KEY_URL, "").orEmpty(),
        user = prefs.getString(KEY_USER, "").orEmpty(),
        password = prefs.getString(KEY_PASS, "").orEmpty(),
        directory = prefs.getString(KEY_DIR, "Allegro").orEmpty().ifBlank { "Allegro" },
        fileName = prefs.getString(KEY_FILE, "allegro-sync.json").orEmpty().ifBlank { "allegro-sync.json" },
    )

    private fun client(): WebDavClient = WebDavClient(_config.value)

    suspend fun testConnection(): Result<Unit> {
        val config = _config.value
        if (!config.isConfigured) return Result.failure(IllegalStateException("请先填写服务器地址"))
        val client = client()
        return client.check().recoverCatching {
            // 目录可能还不存在：试着建一下再看
            client.ensureDirectory().getOrThrow()
            client.check().getOrThrow()
        }
    }

    /** 本机 → 远端。 */
    suspend fun upload(): Result<SyncOutcome> = runCatching {
        val config = _config.value
        require(config.isConfigured) { "请先填写服务器地址" }
        val payload = buildPayload()
        WebDavClient(config).upload(payload.encode().toByteArray()).getOrThrow()
        val now = System.currentTimeMillis()
        markSynced(now)
        SyncOutcome(
            bookmarks = payload.bookmarks.size,
            folders = payload.folders.size,
            sites = payload.siteSettings.size,
            at = now,
        )
    }

    /** 远端 → 本机。 */
    suspend fun download(): Result<SyncOutcome> = runCatching {
        val config = _config.value
        require(config.isConfigured) { "请先填写服务器地址" }
        val bytes = WebDavClient(config).download().getOrThrow()
            ?: error("远端还没有备份文件，先上传一次")
        val payload = SyncPayload.decode(String(bytes))
            ?: error("备份文件无法解析（可能不是本应用的备份）")
        applyPayload(payload)
        val now = System.currentTimeMillis()
        markSynced(now)
        SyncOutcome(
            bookmarks = payload.bookmarks.size,
            folders = payload.folders.size,
            sites = payload.siteSettings.size,
            at = now,
        )
    }

    private fun markSynced(at: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, at).apply()
        _lastSyncAt.value = at
    }

    private suspend fun buildPayload(): SyncPayload {
        val folders = bookmarks.folders().map {
            SyncedFolder(id = it.id, name = it.name, position = it.position, createdAt = it.createdAt)
        }
        val items = bookmarks.all().map {
            SyncedBookmark(
                url = it.url,
                title = it.title,
                folderId = it.folderId,
                position = it.position,
                createdAt = it.createdAt,
            )
        }
        val sites = siteSettings.configured().associateBy { it.host }
        return SyncPayload(
            exportedAt = System.currentTimeMillis(),
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            folders = folders,
            bookmarks = items,
            siteSettings = sites,
            prefs = mapOf(
                "theme" to settings.themeMode.value.name,
                "engine" to settings.searchEngineId.value,
                "homeBookmarks" to settings.homeBookmarksEnabled.value.toString(),
            ),
        )
    }

    private suspend fun applyPayload(payload: SyncPayload) {
        // 文件夹先写，书签的 folderId 才有意义
        payload.folders.forEach { folder ->
            bookmarks.importAll(
                folders = listOf(
                    BookmarkFolderEntity(
                        id = folder.id,
                        name = folder.name,
                        position = folder.position,
                        createdAt = folder.createdAt,
                    ),
                ),
                bookmarks = emptyList(),
            )
        }
        val existing = bookmarks.all().associateBy { it.url }
        payload.bookmarks.forEach { item ->
            val current = existing[item.url]
            bookmarks.importAll(
                folders = emptyList(),
                bookmarks = listOf(
                    BookmarkEntity(
                        id = current?.id ?: 0L,
                        title = item.title,
                        url = item.url,
                        createdAt = item.createdAt,
                        folderId = item.folderId,
                        position = item.position,
                    ),
                ),
            )
        }
        payload.siteSettings.values.forEach { site ->
            siteSettings.save(site.copy(host = site.host))
        }
        payload.prefs["homeBookmarks"]?.toBooleanStrictOrNull()?.let(settings::setHomeBookmarksEnabled)
        payload.prefs["theme"]?.let { name ->
            com.ravenxud.allegro.data.model.ThemeMode.entries.firstOrNull { it.name == name }
                ?.let(settings::setThemeMode)
        }
        payload.prefs["engine"]?.let(settings::setSearchEngine)
    }

    private companion object {
        const val PREFS = "simple_browser_sync"
        const val KEY_URL = "url"
        const val KEY_USER = "user"
        const val KEY_PASS = "pass"
        const val KEY_DIR = "dir"
        const val KEY_FILE = "file"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
