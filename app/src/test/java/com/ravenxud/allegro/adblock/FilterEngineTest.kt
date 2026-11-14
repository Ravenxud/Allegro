package com.ravenxud.allegro.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    private val engine = FilterEngine.parse(
        """
        ! 注释应被忽略
        [Adblock Plus 2.0]
        ||doubleclick.net^
        ||ads.example.com^
        @@||ads.example.com/allow^
        /adserver/
        example.org##.ad-box
        ##.global-ad
        """.trimIndent(),
    )

    @Test
    fun `blocks host and subdomain`() {
        assertTrue(engine.shouldBlock("https://doubleclick.net/x.js"))
        assertTrue(engine.shouldBlock("https://static.doubleclick.net/x.js"))
    }

    @Test
    fun `does not block unrelated host`() {
        assertFalse(engine.shouldBlock("https://example.com/app.js"))
        // 子串相似但不同域，不应误伤
        assertFalse(engine.shouldBlock("https://notdoubleclick.net/x.js"))
    }

    @Test
    fun `exception rule wins`() {
        assertTrue(engine.shouldBlock("https://ads.example.com/banner.png"))
        assertFalse(engine.shouldBlock("https://ads.example.com/allow/pixel.png"))
    }

    @Test
    fun `substring rule matches path`() {
        assertTrue(engine.shouldBlock("https://cdn.site.com/adserver/load.js"))
    }

    @Test
    fun `same site ignores path fragments`() {
        // 同站请求忽略 /adserver/ 这类片段规则，避免拦掉网站自己的资源
        assertFalse(
            engine.shouldBlock(
                "https://example.org/adserver/app.css",
                includePathFragments = false,
            ),
        )
        // 跨站仍然按片段规则拦截
        assertTrue(engine.shouldBlock("https://cdn.other.com/adserver/load.js"))
    }

    @Test
    fun `domain rules still apply to same site`() {
        assertTrue(engine.shouldBlock("https://ads.example.com/x.js", includePathFragments = false))
    }

    @Test
    fun `cosmetic selectors merge host and global`() {
        val selectors = engine.cosmeticSelectors("https://www.example.org/page")
        assertTrue(selectors.contains(".ad-box"))
        assertTrue(selectors.contains(".global-ad"))
    }

    @Test
    fun `cosmetic selectors empty for unknown host`() {
        val selectors = engine.cosmeticSelectors("https://unknown.test/page")
        assertEquals(listOf(".global-ad"), selectors)
    }

    @Test
    fun `local cosmetic rules apply to every site without reparsing`() {
        val custom = engine.withExtraCosmetic(listOf(".picked-ad", " ", ".global-ad"))
        val selectors = custom.cosmeticSelectors("https://unknown.test/page")
        assertEquals(listOf(".global-ad", ".picked-ad"), selectors)
        // 原引擎不受影响
        assertEquals(listOf(".global-ad"), engine.cosmeticSelectors("https://unknown.test/page"))
    }

    @Test
    fun `cosmetic script is idempotent and escaped`() {
        val script = CosmeticInjector.script(listOf(".ad", "#banner"))
        assertTrue(script.contains("__sb_adblock"))
        assertTrue(script.contains("display:none"))
        assertTrue(script.contains(".ad,#banner"))
    }
}
