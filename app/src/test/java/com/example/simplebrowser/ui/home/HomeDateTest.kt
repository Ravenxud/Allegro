package com.example.simplebrowser.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeDateTest {

    @Test
    fun `formats date without throwing`() {
        val label = HomeDate.todayLabel(LocalDate.of(2026, 11, 13))
        assertTrue("缺少月日: $label", label.contains("11月13日"))
    }

    @Test
    fun `greeting boundaries`() {
        assertEquals("早上好", HomeDate.greeting(6))
        assertEquals("中午好", HomeDate.greeting(12))
        assertEquals("下午好", HomeDate.greeting(16))
        assertEquals("晚上好", HomeDate.greeting(22))
        assertEquals("晚上好", HomeDate.greeting(2))
    }
}
