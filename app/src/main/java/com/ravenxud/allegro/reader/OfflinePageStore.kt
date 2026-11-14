package com.ravenxud.allegro.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 离线页面索引（JSON 文件，避免为此改数据库版本）。 */
class OfflinePageStore(context: Context) {

    private val indexFile = File(context.filesDir, "offline/index.json").apply {
        parentFile?.mkdirs()
    }
    private val dir = File(context.filesDir, "offline").apply { mkdirs() }

    data class Entry(
        val id: String,
        val title: String,
        val url: String,
        val fileName: String,
        val savedAt: Long,
        val sizeBytes: Long,
    )

    fun fileFor(id: String): File = File(dir, "$id.mhtml")

    /** 转换后的自包含 HTML（离线页真正打开的就是它）。 */
    fun htmlFileFor(id: String): File = File(dir, "$id.html")

    fun fileFor(entry: Entry): File = File(dir, entry.fileName)

    /**
     * 把某条记录的源文件从 MHTML 换成转换后的 HTML。
     *
     * 离线页在首次打开时惰性转换，转换完就地更新索引，避免重复转换。
     */
    @Synchronized
    fun replaceFile(entry: Entry, newFileName: String, sizeBytes: Long) {
        val list = load().map { e ->
            if (e.id == entry.id) e.copy(fileName = newFileName, sizeBytes = sizeBytes) else e
        }
        write(list)
    }

    @Synchronized
    fun save(title: String, url: String, file: File) {
        val list = load().toMutableList()
        val id = file.nameWithoutExtension
        list.removeAll { it.fileName == file.name }
        list.add(
            0,
            Entry(
                id = id,
                title = title.ifBlank { url },
                url = url,
                fileName = file.name,
                savedAt = System.currentTimeMillis(),
                sizeBytes = file.length(),
            ),
        )
        write(list)
    }

    @Synchronized
    fun load(): List<Entry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    url = o.optString("url"),
                    fileName = o.optString("fileName"),
                    savedAt = o.optLong("savedAt"),
                    sizeBytes = o.optLong("sizeBytes"),
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun delete(entry: Entry) {
        runCatching { fileFor(entry).delete() }
        // 兼容：转换前的 .mhtml 与转换后的 .html 都清掉
        runCatching { fileFor(entry.id).delete() }
        write(load().filterNot { it.id == entry.id })
    }

    @Synchronized
    private fun write(list: List<Entry>) {
        val array = JSONArray()
        list.forEach { e ->
            array.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("title", e.title)
                    put("url", e.url)
                    put("fileName", e.fileName)
                    put("savedAt", e.savedAt)
                    put("sizeBytes", e.sizeBytes)
                },
            )
        }
        runCatching { indexFile.writeText(array.toString()) }
    }
}
