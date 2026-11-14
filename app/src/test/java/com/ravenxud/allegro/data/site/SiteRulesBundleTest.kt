package com.ravenxud.allegro.data.site

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteRulesBundleTest {

    private val bundle = SiteRulesBundle(
        exportedAt = 1000,
        sites = listOf(
            SiteSettings("bilibili.com", javaScript = false),
            SiteSettings("zhihu.com", images = false, desktopUa = true),
        ),
        adBlockWhitelist = listOf("weibo.com", "xiaohongshu.com"),
    )

    @Test
    fun `encode decode roundtrip`() {
        val decoded = SiteRulesBundle.decode(bundle.encode())
        assertEquals(bundle.sites, decoded?.sites)
        assertEquals(bundle.adBlockWhitelist, decoded?.adBlockWhitelist)
        assertEquals(1000L, decoded?.exportedAt)
    }

    @Test
    fun `skips default entries and normalizes hosts`() {
        val text = """
            {"schema":1,"exportedAt":1,
             "sites":[{"host":"  A.Test ","js":true,"img":true,"ua":false},
                      {"host":"b.test","js":false}],
             "adblock":["C.Test","c.test"]}
        """.trimIndent()
        val decoded = SiteRulesBundle.decode(text)!!
        assertEquals(1, decoded.sites.size)          // 全默认的 A.Test 被丢弃
        assertEquals("b.test", decoded.sites.first().host)
        assertEquals(listOf("c.test"), decoded.adBlockWhitelist) // 去重 + 小写
    }

    @Test
    fun `rejects unknown schema and garbage`() {
        assertNull(SiteRulesBundle.decode("""{"schema":99}"""))
        assertNull(SiteRulesBundle.decode("not json"))
        assertNull(SiteRulesBundle.decode(""))
    }

    @Test
    fun `empty export is still valid json`() {
        val empty = SiteRulesBundle(exportedAt = 0, sites = emptyList(), adBlockWhitelist = emptyList())
        val decoded = SiteRulesBundle.decode(empty.encode())!!
        assertTrue(decoded.sites.isEmpty())
        assertTrue(decoded.adBlockWhitelist.isEmpty())
    }
}
