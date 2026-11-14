package com.ravenxud.allegro.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopRedirectPolicyTest {

    private fun policy(url: String) = DesktopRedirectPolicy(maxHops = 5).apply { start(url) }

    @Test
    fun `follows same host path redirect`() {
        val p = policy("https://www.example.com/a")
        assertEquals("https://www.example.com/b", p.next("https://www.example.com/a", "/b"))
    }

    @Test
    fun `allows one mobile to desktop flip then stops`() {
        val p = policy("https://m.example.com/")
        // 第一次 m -> www 允许
        val first = p.next("https://m.example.com/", "https://www.example.com/")
        assertEquals("https://www.example.com/", first)
        // 第二次 www -> m 拒绝（这正是 B 站的死循环）
        assertNull(p.next("https://www.example.com/", "https://m.example.com/"))
        assertEquals(1, p.mobileFlips)
    }

    @Test
    fun `detects a to b to a loop`() {
        val p = policy("https://a.test/x")
        assertEquals("https://b.test/x", p.next("https://a.test/x", "https://b.test/x"))
        assertNull(p.next("https://b.test/x", "https://a.test/x"))
    }

    @Test
    fun `stops after max hops`() {
        val p = policy("https://a.test/0")
        var current = "https://a.test/0"
        var hops = 0
        while (hops < 10) {
            val next = p.next(current, "https://a.test/${hops + 1}") ?: break
            current = next
            hops++
        }
        assertEquals(5, hops)
    }

    @Test
    fun `rejects non http locations`() {
        val p = policy("https://a.test/")
        assertNull(p.next("https://a.test/", "mailto:x@y.test"))
        assertNull(p.next("https://a.test/", "javascript:void(0)"))
    }

    @Test
    fun `resolves relative location`() {
        val p = policy("https://a.test/dir/page")
        assertEquals("https://a.test/other", p.next("https://a.test/dir/page", "../other"))
    }

    @Test
    fun `mobile swap detection`() {
        val p = policy("https://x.test/")
        assertTrue(p.isMobileSwap("m.bilibili.com", "www.bilibili.com"))
        assertTrue(p.isMobileSwap("www.example.com", "m.example.com"))
        // 不同站点不算
        assertTrue(!p.isMobileSwap("m.a.com", "www.b.com"))
        // 完全相同的 host 不算"互换"
        assertTrue(!p.isMobileSwap("example.com", "example.com"))
    }
}
