package com.example.simplebrowser.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavConfigTest {

    @Test
    fun `builds directory and file urls`() {
        val config = WebDavConfig(baseUrl = "https://dav.test/remote.php/dav/files/me", directory = "Allegro")
        assertEquals("https://dav.test/remote.php/dav/files/me/Allegro/", config.directoryUrl())
        assertEquals("https://dav.test/remote.php/dav/files/me/Allegro/allegro-sync.json", config.fileUrl())
    }

    @Test
    fun `tolerates trailing and leading slashes`() {
        val config = WebDavConfig(baseUrl = "https://dav.test/root/", directory = "/a/b/")
        assertEquals("https://dav.test/root/a/b/", config.directoryUrl())
    }

    @Test
    fun `empty directory falls back to base`() {
        val config = WebDavConfig(baseUrl = "https://dav.test/root", directory = "")
        assertEquals("https://dav.test/root/", config.directoryUrl())
        assertEquals("https://dav.test/root/allegro-sync.json", config.fileUrl())
    }

    @Test
    fun `encodes non ascii and spaces in path segments`() {
        val config = WebDavConfig(baseUrl = "https://dav.test/root", directory = "我的 备份")
        assertEquals("https://dav.test/root/%E6%88%91%E7%9A%84%20%E5%A4%87%E4%BB%BD/", config.directoryUrl())
    }

    @Test
    fun `auth header uses basic base64`() {
        assertEquals("Basic dXNlcjpwYXNz", WebDavConfig("https://d.test", "user", "pass").authHeader())
        assertNull(WebDavConfig("https://d.test").authHeader())
    }

    @Test
    fun `detects configured state`() {
        assertTrue(WebDavConfig("https://d.test").isConfigured)
        assertTrue(WebDavConfig("http://192.168.1.2:5005").isConfigured)
        assertTrue(!WebDavConfig("d.test").isConfigured)
        assertTrue(!WebDavConfig("").isConfigured)
    }
}
