package com.ravenxud.allegro.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseHrefTest {

    @Test
    fun `injects base when final url differs`() {
        val html = "<html><head><title>t</title></head><body>x</body></html>"
        val out = BaseHref.inject(html, "https://m.example.com/", "https://www.example.com/")
        assertTrue(out.contains("<base href=\"https://www.example.com/\">"))
        // 仍然插在 head 里，而不是页面最前面
        assertTrue(out.indexOf("<base") > out.indexOf("<head>"))
    }

    @Test
    fun `keeps html untouched when urls match`() {
        val html = "<html><head></head></html>"
        assertEquals(html, BaseHref.inject(html, "https://a.test/x", "https://a.test/x"))
    }

    @Test
    fun `does not duplicate existing base`() {
        val html = "<html><head><base href=\"/x/\"></head></html>"
        assertEquals(html, BaseHref.inject(html, "https://m.a.test/", "https://www.a.test/"))
    }

    @Test
    fun `handles head with attributes and missing head`() {
        val withAttrs = "<html><head profile=\"p\"><meta></head></html>"
        assertTrue(BaseHref.inject(withAttrs, "https://m.a.test/", "https://www.a.test/").contains("<base"))

        val noHead = "<div>bare</div>"
        assertTrue(BaseHref.inject(noHead, "https://m.a.test/", "https://www.a.test/").startsWith("<base"))
    }

    @Test
    fun `escapes quotes in url`() {
        val out = BaseHref.inject("<head></head>", "https://m.a.test/", "https://www.a.test/?q=\"x\"")
        assertTrue(out.contains("%22x%22"))
    }
}
