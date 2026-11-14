package com.example.simplebrowser.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.simplebrowser.data.entity.BookmarkEntity
import com.example.simplebrowser.data.entity.BookmarkFolderEntity

class BookmarksHtmlTest {

    private val folders = listOf(
        BookmarkFolderEntity(id = "f1", name = "工具", position = 0, createdAt = 1L),
        BookmarkFolderEntity(id = "f2", name = "News & Blog", position = 1, createdAt = 2L),
    )

    private val bookmarks = listOf(
        BookmarkEntity(id = 1, title = "GitHub", url = "https://github.com", createdAt = 1L, folderId = "f1", position = 0),
        BookmarkEntity(id = 2, title = "HN & Co", url = "https://news.ycombinator.com", createdAt = 2L, folderId = "f2", position = 1),
        BookmarkEntity(id = 3, title = "散装的", url = "https://example.org", createdAt = 3L, folderId = null, position = 2),
    )

    @Test
    fun `export then parse keeps counts and folders`() {
        val html = BookmarksHtml.export(folders, bookmarks)
        val parsed = BookmarksHtml.parse(html)

        assertEquals(3, parsed.bookmarks.size)
        assertEquals(2, parsed.folders.size)
        assertTrue(parsed.folders.map { it.name }.contains("工具"))
        assertTrue(parsed.folders.map { it.name }.contains("News & Blog"))
    }

    @Test
    fun `escaped characters survive round trip`() {
        val html = BookmarksHtml.export(folders, bookmarks)
        val parsed = BookmarksHtml.parse(html)
        val hn = parsed.bookmarks.first { it.url.contains("ycombinator") }
        assertEquals("HN & Co", hn.title)
    }

    @Test
    fun `bookmarks keep their folder assignment`() {
        val html = BookmarksHtml.export(folders, bookmarks)
        val parsed = BookmarksHtml.parse(html)
        val github = parsed.bookmarks.first { it.url.contains("github") }
        val folder = parsed.folders.firstOrNull { it.id == github.folderId }
        assertEquals("工具", folder?.name)
    }

    @Test
    fun `imports flat html without folders`() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
            <DT><A HREF="https://a.example" TAGS="">A</A>
            <DT><A HREF="https://b.example">B</A>
            </DL><p>
        """.trimIndent()
        val parsed = BookmarksHtml.parse(html)
        assertEquals(2, parsed.bookmarks.size)
        assertTrue(parsed.folders.isEmpty())
        assertEquals(null, parsed.bookmarks.first().folderId)
    }
}
