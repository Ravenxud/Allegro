package com.ravenxud.allegro.data.model

import com.ravenxud.allegro.data.entity.BookmarkEntity
import com.ravenxud.allegro.data.entity.BookmarkFolderEntity

/**
 * 书签 HTML 导入导出（Netscape Bookmark File 格式）。
 *
 * 该格式被 Chrome / Firefox / Edge / Safari 通用支持，因此可以直接和其他浏览器互相搬运。
 * 只处理 `<A HREF="...">标题</A>` 与 `<H3>文件夹</H3>`，忽略图标、注释、时间戳等细节。
 */
object BookmarksHtml {

    private val linkRegex = Regex(
        """<A\s+HREF="([^"]+)"[^>]*>(.*?)</A>""",
        RegexOption.IGNORE_CASE,
    )
    private val folderRegex = Regex("""<H3[^>]*>(.*?)</H3>""", RegexOption.IGNORE_CASE)

    data class Parsed(
        val folders: List<BookmarkFolderEntity>,
        val bookmarks: List<BookmarkEntity>,
    )

    fun export(
        folders: List<BookmarkFolderEntity>,
        bookmarks: List<BookmarkEntity>,
        now: Long = System.currentTimeMillis(),
    ): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")

        val byFolder = bookmarks.groupBy { it.folderId }
        byFolder[null].orEmpty().forEach { sb.append(link(it.title, it.url)).append('\n') }

        folders.forEach { folder ->
            sb.append(folderLine(folder.name)).append('\n')
            byFolder[folder.id].orEmpty().forEach { sb.append(link(it.title, it.url)).append('\n') }
            sb.append("</DL><p>\n")
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    fun parse(html: String, now: Long = System.currentTimeMillis()): Parsed {
        val folderNames = folderRegex.findAll(html).map { decode(it.groupValues[1]) }.toList()
        val folders = folderNames.mapIndexed { index, name ->
            BookmarkFolderEntity(
                id = "import-${index}-${name.hashCode()}",
                name = name.ifBlank { "未命名" },
                position = index,
                createdAt = now,
            )
        }

        val bookmarks = ArrayList<BookmarkEntity>()
        var folderIndex = -1
        var cursor = 0
        // 按出现顺序扫描，遇到文件夹标题就切换目标
        val events = ArrayList<Triple<Int, String, String>>() // (位置, 类型, 内容)
        folderRegex.findAll(html).forEach { m ->
            events += Triple(m.range.first, "folder", m.groupValues[1])
        }
        linkRegex.findAll(html).forEach { m ->
            events += Triple(m.range.first, "link", m.groupValues[1] + "\u0000" + m.groupValues[2])
        }
        events.sortedBy { it.first }.forEach { (_, type, payload) ->
            if (type == "folder") {
                folderIndex++
            } else {
                val parts = payload.split('\u0000')
                val url = parts.getOrNull(0).orEmpty()
                val title = decode(parts.getOrNull(1).orEmpty()).ifBlank { url }
                if (url.startsWith("http") || url.startsWith("file")) {
                    bookmarks += BookmarkEntity(
                        title = title,
                        url = url,
                        createdAt = now,
                        folderId = folders.getOrNull(folderIndex)?.id,
                        position = bookmarks.size,
                    )
                }
            }
        }
        cursor = 0
        return Parsed(folders, bookmarks)
    }

    private fun link(title: String, url: String): String =
        "<DT><A HREF=\"${escape(url)}\">${escape(title)}</A>"

    private fun folderLine(name: String): String = "<DT><H3>${escape(name)}</H3>\n<DL><p>"

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun decode(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
