package com.ravenxud.allegro.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassQualityTest {

    @Test
    fun `no backdrop falls back to off`() {
        // 没有捕获层时无法做模糊，只能退化成半透明 + 描边
        assertEquals(GlassQuality.Off, GlassQuality.effective(GlassQuality.High, hasBackdrop = false))
        assertEquals(GlassQuality.Off, GlassQuality.effective(GlassQuality.Medium, hasBackdrop = false))
        assertEquals(GlassQuality.Off, GlassQuality.effective(GlassQuality.Off, hasBackdrop = false))
    }

    @Test
    fun `user quality is kept when backdrop exists`() {
        assertEquals(GlassQuality.High, GlassQuality.effective(GlassQuality.High, hasBackdrop = true))
        assertEquals(GlassQuality.Medium, GlassQuality.effective(GlassQuality.Medium, hasBackdrop = true))
        assertEquals(GlassQuality.Off, GlassQuality.effective(GlassQuality.Off, hasBackdrop = true))
    }
}
