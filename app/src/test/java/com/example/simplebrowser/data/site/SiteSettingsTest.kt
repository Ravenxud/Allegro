package com.example.simplebrowser.data.site

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSettingsTest {

    @Test
    fun `encode decode roundtrip`() {
        val settings = SiteSettings(host = "a.test", javaScript = false, images = true, desktopUa = true)
        val raw = SiteSettingsCodec.encode(settings)
        assertEquals("js=0;img=1;ua=1", raw)
        assertEquals(settings, SiteSettingsCodec.decode("a.test", raw))
    }

    @Test
    fun `decode falls back to defaults for missing or garbage fields`() {
        assertEquals(SiteSettings("x.test"), SiteSettingsCodec.decode("x.test", null))
        assertEquals(SiteSettings("x.test"), SiteSettingsCodec.decode("x.test", ""))
        assertEquals(SiteSettings("x.test", javaScript = false), SiteSettingsCodec.decode("x.test", "js=0"))
        // 未知字段被忽略
        assertEquals(SiteSettings("x.test"), SiteSettingsCodec.decode("x.test", "foo=1;bar=2"))
    }

    @Test
    fun `default settings are detected`() {
        assertTrue(SiteSettings("a.test").isDefault)
        assertTrue(SiteSettings("a.test", javaScript = true, images = true, desktopUa = false).isDefault)
        assertTrue(!SiteSettings("a.test", javaScript = false).isDefault)
        assertTrue(!SiteSettings("a.test", desktopUa = true).isDefault)
    }

    @Test
    fun `host candidates walk up to two labels`() {
        assertEquals(
            listOf("m.news.example.com", "news.example.com", "example.com"),
            SiteSettingsCodec.hostCandidates("m.news.example.com"),
        )
        assertEquals(listOf("example.com"), SiteSettingsCodec.hostCandidates("example.com"))
        assertEquals(listOf("localhost"), SiteSettingsCodec.hostCandidates("localhost"))
        assertEquals(emptyList<String>(), SiteSettingsCodec.hostCandidates(null))
        // 大小写与首尾空格
        assertEquals(listOf("www.example.com", "example.com"), SiteSettingsCodec.hostCandidates(" WWW.Example.com "))
    }

    @Test
    fun `resolve prefers exact host then parent`() {
        val parent = SiteSettings("example.com", javaScript = false)
        val exact = SiteSettings("m.example.com", images = false)
        val store = mapOf("example.com" to parent, "m.example.com" to exact)

        assertEquals(exact, SiteSettingsCodec.resolve(store, "m.example.com"))
        assertEquals(parent, SiteSettingsCodec.resolve(store, "www.example.com"))
        assertNull(SiteSettingsCodec.resolve(store, "other.test"))
    }

    @Test
    fun `deviations summarize changed options`() {
        assertEquals(emptyList<String>(), SiteSettings("a.test").deviations())
        assertEquals(
            listOf("JS 关", "图片关", "桌面版"),
            SiteSettings("a.test", javaScript = false, images = false, desktopUa = true).deviations(),
        )
    }
}
