package com.ravenxud.allegro.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {

    private fun v(raw: String): ReleaseVersion = ReleaseVersion.parse(raw)!!

    @Test
    fun `parses common tag shapes`() {
        assertEquals(listOf(0, 27, 2), v("v0.27.2").numbers)
        assertEquals(listOf(0, 27, 2), v("0.27.2-beta").numbers)
        assertEquals("beta", v("0.27.2-beta").prerelease)
        assertEquals(listOf(1, 0), v("V1.0").numbers)
        assertEquals(listOf(0, 22, 3), v("v0.22.3-beta").numbers)
    }

    @Test
    fun `rejects non versions`() {
        assertNull(ReleaseVersion.parse("nightly"))
        assertNull(ReleaseVersion.parse(""))
        assertNull(ReleaseVersion.parse("v"))
        assertNull(ReleaseVersion.parse("v1.x.3"))
    }

    @Test
    fun `numeric order wins`() {
        assertTrue(v("0.27.10") > v("0.27.9"))
        assertTrue(v("0.28.0") > v("0.27.99"))
        assertTrue(v("1.0.0") > v("0.99.99"))
        assertEquals(0, v("0.27").compareTo(v("0.27.0")))
    }

    @Test
    fun `release is newer than its prerelease`() {
        assertTrue(v("0.27.2") > v("0.27.2-beta"))
        assertTrue(v("0.27.2-rc1") > v("0.27.2-beta"))
        assertTrue(v("0.27.2-beta") < v("0.27.2"))
    }

    @Test
    fun `installed beta is not updated to itself`() {
        assertTrue(!isNewerVersion("v0.27.2-beta", "0.27.2-beta"))
        assertTrue(!isNewerVersion("v0.27.1-beta", "0.27.2-beta"))
        assertTrue(isNewerVersion("v0.27.2-beta", "0.27.1-beta"))
        assertTrue(isNewerVersion("v0.28.0-beta", "0.27.2-beta"))
    }

    @Test
    fun `unparseable tags never count as newer`() {
        assertTrue(!isNewerVersion("nightly", "0.27.2-beta"))
        assertTrue(!isNewerVersion("v0.27.2-beta", "custom"))
    }
}
