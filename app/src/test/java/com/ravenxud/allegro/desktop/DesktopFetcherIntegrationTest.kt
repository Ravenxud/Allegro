package com.ravenxud.allegro.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真连一台 mock 站点的集成测试（默认跳过）。
 *
 * 覆盖三件真机才会暴露的事：
 * 1. 我们自己发的 hint 头能让服务端返回桌面页（WebView 做不到）
 * 2. m./www 互相 302 时，策略会自己停下（不再无限刷新）
 * 3. 非 HTML / 循环重定向 会回落到 WebView
 *
 * 跑法：`python3 scripts/desktop_mock_server.py 8099` 后
 * `ALLEGRO_DESKTOP_MOCK=http://127.0.0.1:8099/ gradle :app:testDebugUnitTest`
 */
class DesktopFetcherIntegrationTest {

    private val base: String? = System.getenv("ALLEGRO_DESKTOP_MOCK")

    private fun fetcher() = DesktopDocumentFetcher()

    private fun headers() = DesktopHeaders.build(base ?: "http://127.0.0.1:1/")

    @Test(timeout = 30_000)
    fun `desktop hints get desktop page without redirect`() {
        assumeTrue("未设置 ALLEGRO_DESKTOP_MOCK，跳过", base != null)
        val result = fetcher().fetch(base + "hint", headers())
        assertTrue("应拿到 HTML，实际 $result", result is DesktopDocumentFetcher.Result.Html)
        val html = result as DesktopDocumentFetcher.Result.Html
        assertTrue(html.html.contains("DESKTOP PAGE"))
        assertEquals(base + "hint", html.finalUrl)
    }

    @Test(timeout = 30_000)
    fun `mobile host redirects to www only once`() {
        assumeTrue(base != null)
        val result = fetcher().fetch(base + "m", headers())
        assertTrue("应拿到 HTML，实际 $result", result is DesktopDocumentFetcher.Result.Html)
        val html = result as DesktopDocumentFetcher.Result.Html
        assertTrue(html.html.contains("DESKTOP PAGE"))
        assertTrue("最终应停在 /www", html.finalUrl.endsWith("/www"))
    }

    @Test(timeout = 30_000)
    fun `endless ping pong is stopped by policy`() {
        assumeTrue(base != null)
        val result = fetcher().fetch(base + "loop-a", headers())
        assertTrue("循环应被停止而不是成功", result is DesktopDocumentFetcher.Result.Skipped)
        assertTrue((result as DesktopDocumentFetcher.Result.Skipped).reason.contains("循环"))
    }

    @Test(timeout = 30_000)
    fun `non html is skipped`() {
        assumeTrue(base != null)
        val result = fetcher().fetch(base + "image", headers())
        assertTrue(result is DesktopDocumentFetcher.Result.Skipped)
        assertTrue((result as DesktopDocumentFetcher.Result.Skipped).reason.contains("非 HTML"))
    }

    @Test(timeout = 30_000)
    fun `collects set cookie for the android layer`() {
        assumeTrue(base != null)
        val result = fetcher().fetch(base + "set", headers())
        val html = result as DesktopDocumentFetcher.Result.Html
        assertTrue(html.cookies.any { it.second.contains("sid=abc123") })
    }

    @Test(timeout = 30_000)
    fun `forwards existing cookies`() {
        assumeTrue(base != null)
        val custom = DesktopHeaders.build(base + "whoami", cookie = "sid=abc123")
        val result = fetcher().fetch(base + "whoami", custom)
        val html = result as DesktopDocumentFetcher.Result.Html
        assertTrue(html.html.contains("COOKIE:sid=abc123"))
    }
}
