package com.ravenxud.allegro.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopHeadersTest {

    @Test
    fun `declares desktop client hints consistent with user agent`() {
        val headers = DesktopHeaders.build("https://example.com/")
        assertEquals("?0", headers["Sec-CH-UA-Mobile"])
        assertEquals("\"macOS\"", headers["Sec-CH-UA-Platform"])
        assertTrue(headers["User-Agent"]!!.contains("Macintosh"))
        assertTrue(headers["Sec-CH-UA"]!!.contains("Chromium"))
    }

    @Test
    fun `windows ua maps to windows platform hint`() {
        val headers = DesktopHeaders.build("https://example.com/", userAgent = DesktopHeaders.UA_WINDOWS)
        assertEquals("\"Windows\"", headers["Sec-CH-UA-Platform"])
    }

    @Test
    fun `passes through accept language and referer`() {
        val headers = DesktopHeaders.build(
            url = "https://example.com/",
            passThrough = mapOf(
                "Accept-Language" to "ja-JP",
                "Referer" to "https://ref.test/",
                "Accept-Encoding" to "gzip",
            ),
        )
        assertEquals("ja-JP", headers["Accept-Language"])
        assertEquals("https://ref.test/", headers["Referer"])
        // 压缩交给 OkHttp，不能手写
        assertFalse(headers.keys.any { it.equals("Accept-Encoding", ignoreCase = true) })
    }

    @Test
    fun `adds cookie when provided`() {
        val headers = DesktopHeaders.build("https://example.com/", cookie = "a=1; b=2")
        assertEquals("a=1; b=2", headers["Cookie"])
        assertFalse(DesktopHeaders.build("https://e.test/").containsKey("Cookie"))
    }

    @Test
    fun `marks request as document navigation`() {
        val headers = DesktopHeaders.build("https://example.com/")
        assertEquals("navigate", headers["Sec-Fetch-Mode"])
        assertEquals("document", headers["Sec-Fetch-Dest"])
    }
}
