package com.example.simplebrowser.sync

import com.example.simplebrowser.data.site.SiteSettings
import com.example.simplebrowser.util.MiniJson

/** 备份里的一个文件夹。 */
data class SyncedFolder(
    val id: String,
    val name: String,
    val position: Int,
    val createdAt: Long,
)

/** 备份里的一条书签（按 url 去重，和数据库的唯一索引一致）。 */
data class SyncedBookmark(
    val url: String,
    val title: String,
    val folderId: String?,
    val position: Int,
    val createdAt: Long,
)

/**
 * 同步载荷：只放「跨设备有意义且不会打架」的数据。
 *
 * 不含标签页（设备各开各的）、不含历史与下载（隐私/体量）、不含广告规则缓存（可重新下载）。
 */
data class SyncPayload(
    val schema: Int = SCHEMA,
    val exportedAt: Long,
    val device: String,
    val folders: List<SyncedFolder> = emptyList(),
    val bookmarks: List<SyncedBookmark> = emptyList(),
    val siteSettings: Map<String, SiteSettings> = emptyMap(),
    val prefs: Map<String, String> = emptyMap(),
) {

    fun encode(): String = MiniJson.write(
        linkedMapOf(
            "schema" to schema,
            "exportedAt" to exportedAt,
            "device" to device,
            "folders" to folders.map {
                linkedMapOf("id" to it.id, "name" to it.name, "position" to it.position, "createdAt" to it.createdAt)
            },
            "bookmarks" to bookmarks.map {
                linkedMapOf(
                    "url" to it.url,
                    "title" to it.title,
                    "folderId" to it.folderId,
                    "position" to it.position,
                    "createdAt" to it.createdAt,
                )
            },
            "siteSettings" to siteSettings.mapValues { (host, s) ->
                linkedMapOf("host" to host, "js" to s.javaScript, "img" to s.images, "ua" to s.desktopUa)
            },
            "prefs" to prefs,
        ),
    )

    companion object {
        const val SCHEMA = 1

        /** 解析失败或不是本应用的备份时返回 null。 */
        fun decode(text: String): SyncPayload? {
            val root = MiniJson.obj(MiniJson.parse(text))
            if (root.isEmpty()) return null
            val schema = MiniJson.long(root["schema"])
            if (schema <= 0 || schema > SCHEMA) return null

            val folders = MiniJson.array(root["folders"]).mapNotNull { raw ->
                val o = MiniJson.obj(raw)
                val id = MiniJson.str(o["id"])
                if (id.isEmpty()) null else SyncedFolder(
                    id = id,
                    name = MiniJson.str(o["name"]),
                    position = MiniJson.long(o["position"]).toInt(),
                    createdAt = MiniJson.long(o["createdAt"]),
                )
            }
            val bookmarks = MiniJson.array(root["bookmarks"]).mapNotNull { raw ->
                val o = MiniJson.obj(raw)
                val url = MiniJson.str(o["url"])
                if (url.isEmpty()) null else SyncedBookmark(
                    url = url,
                    title = MiniJson.str(o["title"]).ifBlank { url },
                    folderId = MiniJson.str(o["folderId"]).ifBlank { null },
                    position = MiniJson.long(o["position"]).toInt(),
                    createdAt = MiniJson.long(o["createdAt"]),
                )
            }
            val sites = LinkedHashMap<String, SiteSettings>()
            MiniJson.obj(root["siteSettings"]).forEach { (host, raw) ->
                val o = MiniJson.obj(raw)
                sites[host] = SiteSettings(
                    host = host,
                    javaScript = MiniJson.bool(o["js"], true),
                    images = MiniJson.bool(o["img"], true),
                    desktopUa = MiniJson.bool(o["ua"], false),
                )
            }
            val prefs = LinkedHashMap<String, String>()
            MiniJson.obj(root["prefs"]).forEach { (key, value) ->
                if (value is String) prefs[key] = value
            }
            return SyncPayload(
                schema = schema.toInt(),
                exportedAt = MiniJson.long(root["exportedAt"]),
                device = MiniJson.str(root["device"]),
                folders = folders,
                bookmarks = bookmarks,
                siteSettings = sites,
                prefs = prefs,
            )
        }
    }
}
