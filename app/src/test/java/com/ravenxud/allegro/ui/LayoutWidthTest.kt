package com.ravenxud.allegro.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutWidthTest {

    @Test
    fun `classifies by breakpoints`() {
        assertEquals(LayoutWidth.COMPACT, LayoutWidth.classify(320))
        assertEquals(LayoutWidth.COMPACT, LayoutWidth.classify(599))
        assertEquals(LayoutWidth.MEDIUM, LayoutWidth.classify(600))
        assertEquals(LayoutWidth.MEDIUM, LayoutWidth.classify(839))
        assertEquals(LayoutWidth.EXPANDED, LayoutWidth.classify(840))
        assertEquals(LayoutWidth.EXPANDED, LayoutWidth.classify(1280))
    }

    @Test
    fun `panel width only exists on large`() {
        assertEquals(0, LayoutWidth.COMPACT.panelWidthDp)
        assertEquals(220, LayoutWidth.MEDIUM.panelWidthDp)
        assertEquals(280, LayoutWidth.EXPANDED.panelWidthDp)
    }

    @Test
    fun `isLarge flags`() {
        assertFalse(LayoutWidth.COMPACT.isLarge)
        assertTrue(LayoutWidth.MEDIUM.isLarge)
        assertTrue(LayoutWidth.EXPANDED.isLarge)
    }
}
