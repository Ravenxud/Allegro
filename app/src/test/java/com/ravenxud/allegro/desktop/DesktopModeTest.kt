package com.ravenxud.allegro.desktop

import com.ravenxud.allegro.data.model.DesktopUa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopModeTest {

    @Test
    fun `site setting wins over tab and global`() {
        // 站点明确要桌面
        assertTrue(DesktopMode.effective(true, true, tabDesktop = false, globalAlways = false))
        // 站点明确要移动（覆盖标签开关与全局开关）
        assertFalse(DesktopMode.effective(true, false, tabDesktop = true, globalAlways = true))
    }

    @Test
    fun `tab toggle applies when site has no explicit setting`() {
        assertTrue(DesktopMode.effective(false, false, tabDesktop = true, globalAlways = false))
        assertFalse(DesktopMode.effective(false, true, tabDesktop = false, globalAlways = false))
    }

    @Test
    fun `global switch is the fallback`() {
        assertTrue(DesktopMode.effective(false, false, tabDesktop = false, globalAlways = true))
        assertFalse(DesktopMode.effective(false, false, tabDesktop = false, globalAlways = false))
    }

    @Test
    fun `desktop ua keys roundtrip`() {
        assertEquals(DesktopUa.MACOS, DesktopUa.fromKey("macos"))
        assertEquals(DesktopUa.WINDOWS, DesktopUa.fromKey("windows"))
        assertEquals(DesktopUa.MACOS, DesktopUa.fromKey(null))
        assertEquals(DesktopUa.MACOS, DesktopUa.fromKey("nonsense"))
    }
}
