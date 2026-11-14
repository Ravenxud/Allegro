package com.ravenxud.allegro.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlParserTest {

    private val search = "https://www.baidu.com/s?wd=%s"

    @Test
    fun `keeps explicit scheme`() {
        assertEquals("https://a.com/x", UrlParser.toUrl("https://a.com/x", search))
        assertEquals("http://a.com", UrlParser.toUrl("http://a.com", search))
        assertEquals("about:blank", UrlParser.toUrl("about:blank", search))
    }

    @Test
    fun `adds https for bare host`() {
        assertEquals("https://bilibili.com", UrlParser.toUrl("bilibili.com", search))
        assertEquals("https://www.zhihu.com/question/1", UrlParser.toUrl("www.zhihu.com/question/1", search))
    }

    @Test
    fun `handles ip and localhost`() {
        assertEquals("https://1.2.3.4:8080/x", UrlParser.toUrl("1.2.3.4:8080/x", search))
        assertEquals("https://localhost:3000", UrlParser.toUrl("localhost:3000", search))
    }

    @Test
    fun `falls back to search`() {
        assertEquals(
            "https://www.baidu.com/s?wd=%E5%A4%A9%E6%B0%94",
            UrlParser.toUrl("天气", search),
        )
        assertEquals(
            "https://www.baidu.com/s?wd=hello+world",
            UrlParser.toUrl("hello world", search),
        )
    }

    @Test
    fun `blank input`() {
        assertEquals("about:blank", UrlParser.toUrl("   ", search))
    }
}
