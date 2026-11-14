package com.example.simplebrowser.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真连一台 WebDAV 服务器的集成测试。
 *
 * 默认跳过：只有设置了 `ALLEGRO_DAV_URL`（本机可用 /tmp/dav_server.py 起一台）
 * 与 `ALLEGRO_DAV_DIR` 才会跑，避免 CI 依赖外部网络。
 */
class WebDavClientIntegrationTest {

    private val baseUrl: String? = System.getenv("ALLEGRO_DAV_URL")
    private val user: String = System.getenv("ALLEGRO_DAV_USER") ?: "tester"
    private val password: String = System.getenv("ALLEGRO_DAV_PASSWORD") ?: "s3cret"

    private fun config(dir: String = "AllegroIT") = WebDavConfig(
        baseUrl = baseUrl ?: "http://127.0.0.1:1/",
        user = user,
        password = password,
        directory = dir,
    )

    @Test(timeout = 30_000)
    fun `upload then download roundtrip`() = runBlocking {
        assumeTrue("未设置 ALLEGRO_DAV_URL，跳过集成测试", baseUrl != null)

        val client = WebDavClient(config())
        client.ensureDirectory().getOrThrow()
        client.check().getOrThrow()

        val payload = SyncPayload(
            exportedAt = 42,
            device = "integration",
            bookmarks = listOf(SyncedBookmark("https://integration.test/", "IT", null, 0, 1)),
        ).encode()

        client.upload(payload.toByteArray()).getOrThrow()
        val downloaded = client.download().getOrThrow()
        assertEquals(payload, String(downloaded!!))

        val decoded = SyncPayload.decode(String(downloaded))
        assertEquals("integration", decoded?.device)
        assertEquals(1, decoded?.bookmarks?.size)
    }

    @Test(timeout = 30_000)
    fun `missing file returns null`() = runBlocking {
        assumeTrue(baseUrl != null)
        val client = WebDavClient(config(dir = "AllegroIT-nothing-here"))
        client.ensureDirectory().getOrThrow()
        assertNull(client.download().getOrThrow())
    }

    @Test(timeout = 30_000)
    fun `wrong password is reported`() = runBlocking {
        assumeTrue(baseUrl != null)
        val client = WebDavClient(config().copy(password = "wrong-password"))
        val result = client.check()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("认证失败") == true)
    }
}
