package com.ravenxud.allegro.sync

import com.ravenxud.allegro.data.site.SiteSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadTest {

    private val payload = SyncPayload(
        exportedAt = 1_700_000_000_000,
        device = "Pixel 8",
        folders = listOf(SyncedFolder("f1", "技术", 0, 1000)),
        bookmarks = listOf(
            SyncedBookmark("https://a.test/", "A", "f1", 0, 2000),
            SyncedBookmark("https://b.test/", "B", null, 1, 3000),
        ),
        siteSettings = mapOf(
            "a.test" to SiteSettings("a.test", javaScript = false),
            "b.test" to SiteSettings("b.test", desktopUa = true),
        ),
        prefs = mapOf("theme" to "DARK", "engine" to "bing", "homeBookmarks" to "false"),
    )

    @Test
    fun `encode decode roundtrip`() {
        val decoded = SyncPayload.decode(payload.encode())
        assertEquals(payload.folders, decoded?.folders)
        assertEquals(payload.bookmarks, decoded?.bookmarks)
        assertEquals(payload.siteSettings, decoded?.siteSettings)
        assertEquals(payload.prefs, decoded?.prefs)
        assertEquals(payload.device, decoded?.device)
        assertEquals(payload.exportedAt, decoded?.exportedAt)
    }

    @Test
    fun `missing optional sections default to empty`() {
        val decoded = SyncPayload.decode("""{"schema":1,"exportedAt":5}""")
        assertEquals(0, decoded?.folders?.size)
        assertEquals(0, decoded?.bookmarks?.size)
        assertTrue(decoded?.siteSettings?.isEmpty() == true)
    }

    @Test
    fun `v2 payload carries adblock whitelist`() {
        val withAds = payload.copy(adblockSites = listOf("weibo.com", "zhihu.com"))
        val decoded = SyncPayload.decode(withAds.encode())!!
        assertEquals(listOf("weibo.com", "zhihu.com"), decoded.adblockSites)
    }

    @Test
    fun `v1 backup still decodes`() {
        // 0.27 时代的备份没有 adblock 字段，必须还能读
        val v1 = """{"schema":1,"exportedAt":5,"bookmarks":[{"url":"https://a.test/","title":"A"}]}"""
        val decoded = SyncPayload.decode(v1)!!
        assertEquals(1, decoded.bookmarks.size)
        assertTrue(decoded.adblockSites.isEmpty())
    }

    @Test
    fun `rejects unknown schema and garbage`() {
        assertNull(SyncPayload.decode("""{"schema":99}"""))
        assertNull(SyncPayload.decode("""{"schema":0}"""))
        assertNull(SyncPayload.decode("not json"))
        assertNull(SyncPayload.decode(""))
    }

    @Test
    fun `skips entries without url or id`() {
        val decoded = SyncPayload.decode(
            """
            {"schema":1,"exportedAt":1,
             "bookmarks":[{"title":"no url"},{"url":"https://x.test/"}],
             "folders":[{"name":"no id"},{"id":"f9","name":"ok"}]}
            """.trimIndent(),
        )
        assertEquals(1, decoded?.bookmarks?.size)
        assertEquals("https://x.test/", decoded?.bookmarks?.first()?.title)
        assertEquals(1, decoded?.folders?.size)
    }
}
